# Deploying: what runs on the server, and how a version goes live

LAA in production is one Hetzner server running three containers. This page covers how they fit together, how a new version goes live and comes back off, and how to restore the database. The whole path from a task to a deploy is in [`WORKFLOW.md`](WORKFLOW.md); the plan that gets us there is [`PRODUCTION-PLAN.md`](PRODUCTION-PLAN.md).

## What runs

```mermaid
flowchart LR
    V(["Visitor's browser<br/>or phone app"]) -->|"HTTPS :443"| C
    subgraph H["Hetzner server · Docker Compose (/opt/laa)"]
        subgraph E["network: edge"]
            C["web (laa-web)<br/>Caddy: certificates,<br/>the web app's files"]
            S["server (laa-server)<br/>Spring Boot, Java 21"]
        end
        subgraph D["network: data (no way out)"]
            P[("postgres<br/>Postgres 18")]
        end
        C -->|"api. → :8080"| S
        S --> P
    end
    S -->|"AI providers, email"| I(["the internet"])
```

- **`web`** is Caddy, holding the exported web app. It answers both addresses, `learnanythinganywhere.app` (the app's files) and `api.learnanythinganywhere.app` (passed on to the server). It fetches and renews the HTTPS certificates itself, and sets the visitor's real address in `X-Forwarded-For`, replacing whatever the visitor sent.
- **`server`** is the Spring Boot jar. It's reached only through Caddy, runs as an unprivileged user on a read-only file system, and gets its settings from `/opt/laa/.env`.
- **`postgres`** keeps its data in the `laa_pgdata` volume. It's on a network with no route out, and nothing outside the server can reach it.

The two images are built from `deploy/server.Dockerfile` and `deploy/web.Dockerfile`. Each has an allowlist (`*.Dockerfile.dockerignore`) of what the build may see, so `.env` can never get into an image. **The web app has the API's address baked in when it's built**, so an image is built for one address.

## On the server

```
/opt/laa/
├── compose.yml          deploy/compose.yml
├── compose.build.yml    deploy/compose.build.yml (only for building on the server)
├── deploy.sh            deploy/deploy.sh
├── .env                 the production settings, root only (chmod 600), from deploy/.env.example
├── backups/             a database copy from just before each of the last 10 deploys
└── src/                 a clone of the repository (only for building on the server)
```

`.env` holds everything: the two addresses, the secrets, the AI keys, the email settings and the spending caps. Compose reads it, and the server gets all of it as its environment. Type the secrets on the server itself. `LAA_TAG` (the live version) and `LAA_PREVIOUS_TAG` are written by `deploy.sh`, so leave them alone. The laptop-only switches (`LAA_DEV_LOGIN`, `LAA_DEV_ENDPOINTS`, `LAA_PRACTICE_PROVIDERS`, `LAA_MAIL_OUTBOX`) never go here.

## Putting a version live

Everything goes through `deploy.sh`, as root in `/opt/laa`:

| Command | What it does |
|---|---|
| `./deploy.sh build [ref]` | Builds both images on the server from `src/` at `ref` (a branch, tag or commit; default `main`), tags them with the commit, then rolls them out. **This is how we deploy until the deploy workflow exists** (Phase 4). |
| `./deploy.sh <tag>` | Rolls out images already built, pulling them from the registry (`LAA_IMAGES`) if they aren't on the server. The deploy workflow will run this. |
| `./deploy.sh rollback` | Goes back to the version before the live one. |
| `./deploy.sh status` | The live and previous versions, and the containers' state. |

Each roll-out does this:

```mermaid
flowchart TD
    A["Back up the database<br/>(backups/before-TAG-TIME.dump)"] --> B["LAA_TAG = the new version"]
    B --> C["docker compose up --wait<br/>(the server's /health must pass)"]
    C --> D{"Both sites answer<br/>through this server's Caddy?"}
    D -->|yes| E["Live. LAA_PREVIOUS_TAG = the old version.<br/>Older images are removed."]
    D -->|no, within 3 minutes| F["Print the logs.<br/>LAA_TAG = the old version, start it again."]
    F --> G["The deploy fails,<br/>and the old version is live"]
```

- **Only one deploy runs at a time.** A second one stops with a message. If a deploy was interrupted, remove `/opt/laa/.deploy.lock`.
- **A roll back puts the old code back, not the old data.** A new version may already have changed the database. Older code usually still runs, because migrations only add things, but if it doesn't, restore the backup taken just before (below).
- **The first start of an `https://` address fetches its certificates**, so the DNS records must already point at the server (Phase 3, step 4). The check keeps trying for 3 minutes while that happens.

## Restoring a backup

The backups are in Postgres's own format. To put one back, for example `backups/before-abc123-20261122T155055Z.dump`:

```bash
cd /opt/laa
docker compose stop server                     # nobody writes while it's restored
docker compose exec -T postgres pg_restore -U laa -d laa --clean --if-exists --single-transaction \
  < backups/before-abc123-20261122T155055Z.dump
docker compose start server
```

`--clean` replaces what's there, and `--single-transaction` means a restore that fails changes nothing. Everything written since that backup is lost. If the code that's live is newer than the backup, roll back to the version the backup was taken under, too: the backup's name says which version came *after* it.

The nightly backups, kept off the server, are Phase 3, step 5 (issue #28).

## Trying it locally first

```bash
./e2e/production.sh [screenshot dir]
```

This runs the production setup on your laptop, using the real `compose.yml` and `deploy.sh`. It lays out a scratch folder like `/opt/laa`, with local `http://` addresses and practice mode, and then:

1. builds both images from your working copy and deploys them;
2. checks the stack from outside in headless Chrome (`e2e/production.mjs`): headers, caching, streamed replies, a learner opening a lesson and its diagram, and that a forged `X-Forwarded-For` can't get round the sign-in limit;
3. deploys a second version, which must take a readable backup first;
4. deploys a broken version, which must roll itself back;
5. rolls back by hand, keeping the data;
6. loses the data and restores it from the backup, with the commands above.

Then it removes everything it started. Run it after any change to `deploy/`.

## When something's wrong

| What you see | What to do |
|---|---|
| A deploy fails and rolls back | It prints the server's last log lines. More with `docker compose logs --tail 200 server`. Fix it in a pull request and deploy again. |
| `https://` doesn't work on a first start | `docker compose logs web`: usually the DNS records don't point here yet, or port 80 or 443 is closed in the Hetzner firewall. |
| The server restarts over and over | `docker compose logs server`. It refuses to start when a required setting is missing (`JWT_SECRET`, an SMTP host, `LAA_DEFAULT_PROVIDER` on a new database), and the message names it. |
| Out of memory | The server has 1.5 GB (75% of it heap), Postgres 768 MB and Caddy 256 MB, in `compose.yml`. Running out of memory ends the server process, and Docker starts it again. |
