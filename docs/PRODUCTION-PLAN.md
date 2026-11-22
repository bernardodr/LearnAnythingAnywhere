# Production plan: web, iOS and Google Play, with the server on Hetzner

Written 2026-11-17. A step-by-step plan to take LAA from "works on my laptop" to the web, the App Store and Google Play, with a GitHub Actions pipeline and the server on Hetzner. Each phase ends on a **Done when** check. **You** marks steps only you can do (accounts, payments, legal); everything else I build, one milestone at a time, with tests and docs as usual.

## Where we are

Found in the repo today:

- **The GitHub repo is public** (`bernardodr/LearnAnythingAnywhere`). GitHub Actions minutes are free for public repos, but everything committed is published, so secrets must never enter it. The `.env` scan before each commit matters even more.
- **No pipeline yet:** no `.github/` folder, no `eas.json`, and no Dockerfile.
- **Production blockers in the code:**
  - **The server reads the client's address straight from the connection** (`getRemoteAddr` in `AuthController` and `ProfileController`). Behind a proxy, every user shares the proxy's address. So 20 failed sign-ins anywhere would pause sign-in for everyone for 15 minutes, and the sixth sign-up of the day would be refused.
  - **Without an SMTP host, emails go to an in-memory outbox** with only a warning. In production, nobody would get a confirmation or reset email, and an unconfirmed account can't use the AI.
  - **There's no way to delete an account.** Apple and Google both require one.
  - **The health check (`/health`) answered a database outage with a generic 500 error** rather than a clear "down" (fixed in Phase 1, step 2).
- **The app icon is still Expo's placeholder** (the blue "A"), and there's no bundle identifier or package name yet.
- **Still open from before:**
  - the Anthropic key in `.env` is rejected (401), and `OPENAI_API_KEY` is a placeholder;
  - the children's-privacy legal review hasn't happened.

## The target

```
GitHub (public repo)
 ├─ every pull request ─────► CI: server tests (Testcontainers) · app typecheck + lint + web build
 ├─ merge to main ──────────► build 2 images ─► GitHub Container Registry ─► you approve ─► deploy to Hetzner
 └─ tag v1.2.3 ─────────────► EAS Build (iOS + Android) ─► EAS Submit ─► TestFlight · Play testing track
                                                                          └─► you promote to the stores

Hetzner Cloud server (Docker Compose)
  Caddy (automatic HTTPS)
   ├─ https://learnanythinganywhere.app      → the web app (the exported Expo site)
   └─ https://api.learnanythinganywhere.app  → LAA server (Spring Boot) ─► Postgres 18 (a volume; nightly backups copied off the box)
```

- **Two images per commit:** `laa-server` (the Spring Boot jar on a Java 21 runtime) and `laa-web` (Caddy plus the exported site; it is also the HTTPS front door). A deploy sets both tags and restarts. A rollback sets the previous tags.
- **Where secrets live:**
  - Production secrets (database password, JWT secret, AI keys, SMTP password, owner setup code) live only on the server, in `/opt/laa/.env`, readable by root only. You write them there once over SSH.
  - GitHub holds only the deploy key and `EXPO_TOKEN`.
  - Apple and Google store credentials live in EAS.
- **Phones and the web call `https://api.learnanythinganywhere.app`.** The address is baked into each app build, so it must be final before the first store build.

## Decisions for you

| # | Decision | My recommendation |
|---|---|---|
| D1 | **A domain**, or run on the server's bare IP address | ✅ **Decided 2026-11-17: `learnanythinganywhere.app`** (bought). The web app at the bare domain, the server at `api.learnanythinganywhere.app`. Browsers open `.app` addresses only over HTTPS, which Caddy's automatic certificates cover. |
| D2 | **Apple and Google developer accounts**: personal, or an organisation (needs a D-U-N-S number, i.e. a registered company) | ✅ **Decided 2026-11-17: personal.** So Google's closed test (12 testers for 14 days) comes first (Phase 6), and your own name shows as the seller on the App Store. |
| D3 | **App identity:** the store name, and the iOS bundle ID / Android package name. **These are permanent once published.** | ✅ **Decided 2026-11-17:** the name **Learn Anything Anywhere** (23 characters; both stores allow 30), and **`app.learnanythinganywhere`** (the domain reversed) as both the bundle ID and the package name. Whether the name is free on the App Store shows only when you reserve it in App Store Connect. |
| D4 | **Where phone builds run** | **EAS Build** (Expo's build servers, started from GitHub Actions). It manages signing for both stores. The alternative, `eas build --local` on GitHub's runners (macOS runners are free on public repos), has more moving parts. |
| D5 | **How code reaches production** | ✅ **Decided 2026-11-17: nobody pushes to `main`, you and I included.** Every change arrives by pull request, and CI must pass. **You review by merging** (Phase 2, option (a)). Merging deploys the server and web after you click Approve. Store builds happen when you push a version tag. |
| D6 | **Health check** | A small `/health` endpoint of our own. Spring Boot Actuator would be a new dependency. |
| D7 | **Server type** | **CX23** (x86, 2 vCPU, 4 GB) if Hetzner lets you order it. Otherwise **CAX11** (ARM), and I build ARM images. |
| D8 | **Email provider** (to send confirmation and reset emails) | ✅ **Decided 2026-11-17: Cloudflare Email Sending** (the domain is on Cloudflare; Workers Paid plan, 3,000 emails a month included). SMTP on port 465 with `SPRING_MAIL_SSL_ENABLED=true`. Email Routing forwards `support@` to the user's inbox. Setup steps: issue #29. |
| D9 | **Keep the repo public?** | Your call. Public keeps Actions free and costs nothing as long as secrets stay out. |
| D10 | **Who the app is for on the stores** (Google's "target audience") | **Adults**, matching "accounts are for adults". Choosing ages under 13 brings in Google's Families policy, which changes a lot. Settle it with the legal review. |
| D11 | **Third-party GitHub Actions** (checkout, setup-java, setup-node, Docker login/build-push, Expo's action, an SSH step) | Approve the list in Phase 2. Each is pinned to a commit hash, so a hijacked tag can't run in the pipeline. |

## What it costs

| Item | Cost |
|---|---|
| Domain | ~€10–15 / year |
| Hetzner CX23 + IPv4 | €5.49 + €0.50 / month (after Hetzner's June 2026 price rise) |
| Hetzner backups | +20% of the server price (~€1.10 / month) |
| Off-box backup storage | a few € / month (a Storage Box or object storage) |
| Apple Developer Program | $99 / year |
| Google Play Console | $25 once |
| Expo EAS | Free plan to start: a limited number of low-priority builds a month, each cut off at 45 minutes. The Starter plan is $19 / month if you outgrow it. |
| GitHub Actions | free on a public repo |
| Email sending | the free tiers cover a small launch |
| AI usage | capped by LAA itself at $1 per learner and $25 in total per day, so up to ~$750 a month at worst. Consider a lower total cap at launch, plus a monthly budget in each provider's console. |

## The steps

### Phase 0: start the slow things now (You)

These take minutes to start but days to come through, so begin them while I build Phase 1.

1. ✅ **Buy the domain** (D1): done 2026-11-17, `learnanythinganywhere.app`. Keep the registrar's DNS settings page handy for Phase 3.
2. **Enrol in the Apple Developer Program** (D2). Individual enrolment usually clears in a day or two; an organisation needs a D-U-N-S number first.
3. **Open a Google Play Console account** (D2) and finish identity verification.
4. **Create an Expo account** (free) for EAS.
5. **Create a Hetzner Cloud account.**
6. **Fix the AI keys:** a working Anthropic key in `.env`, and the OpenAI key if you'll use it. Set a monthly spending limit in each provider's console.
7. **Line up 15 or more Android testers** (friends and family with a Google account). Google needs 12 opted in continuously for 14 days, and people drop out.
8. **Legal:** who stands behind the app, and a review of children's privacy (children use LAA through a parent's account). I'll draft the privacy policy and terms from what the app actually stores, for you, and ideally a lawyer, to approve.

**Done when:** you have the domain, the Apple and Google accounts (or they're under review), the Expo and Hetzner accounts, working AI keys, and a list of testers.

### Phase 1: make the code production-ready

Each step is one milestone: built, tested, documented in the product docs where people can see it, and then I stop for your go-ahead.

1. ✅ **Real client addresses behind the proxy** (done 2026-11-17). Trust `X-Forwarded-For` only from the proxy on the server's private Docker network (`server.forward-headers-strategy` plus Tomcat's trusted proxies), so throttling and the sign-up limit count real people again. A test checks that a forwarded address is used from the trusted proxy and ignored from anyone else.
2. ✅ **`GET /health`** (done 2026-11-17): it already checked the database; now it answers 503 when the database can't be reached, instead of a 500. Used by the deploy and the uptime monitor (D6).
3. ✅ **Email must be set up in production** (done 2026-11-17). The server refuses to start without an SMTP host unless outbox mode is switched on (tests, and dev without Mailpit). This follows the existing rule that the server's own defaults are safe for production.
4. **Delete your account** (Apple guideline 5.1.1(v); Google Play's account-deletion rule):
   - In Settings: type your password (and the parent PIN, if one is set). This deletes the account and everything in it: books, lessons, chats and reader settings.
   - The AI cost records stay for the owner's totals, with the person removed.
   - A public page, `/delete-account`, explains how. Google Play links to it.
5. **Report a lesson or a reply** (Google Play's AI-generated content policy asks for a way to report offensive AI output without leaving the app):
   - A *Report* action on lessons, practice feedback and tutor replies.
   - Reports appear in the owner console.
6. **Public pages** served by the web app: `/privacy`, `/terms`, `/support` (a contact address) and `/delete-account`. Both stores need the privacy and support URLs. You approve the wording.
7. **App identity** (D3):
   - the store name *Learn Anything Anywhere*, and `app.learnanythinganywhere` as the bundle ID and package name;
   - a real icon and splash screen (you supply or approve the artwork);
   - version 1.0.0;
   - `eas.json` with `preview` and `production` profiles, each with its API address.
8. ✅ **Production packaging** (done 2026-11-22; `docs/DEPLOYING.md`):
   - a `Dockerfile` for the server (Java 21 runtime, non-root, memory limits);
   - `deploy/compose.yml` (Caddy, server, Postgres 18) and a `Caddyfile`;
   - `deploy/deploy.sh`: back up the database, pull, restart, wait for `/health`, and roll back if it fails.
   - All of it is tried end to end on my machine first (`./e2e/production.sh`).
   - Until the deploy workflow exists (Phase 4), `deploy.sh build main` builds the images on the server itself, so the web beta doesn't wait for Phases 2 and 4.

**Done when:** all of the above is merged, with the server suite, app typecheck and lint green, and a local run of the production compose file passing a browser walk-through.

### Phase 2: CI on GitHub

1. **`.github/workflows/ci.yml`** on every pull request and push to `main`:
   - **Server:** `./mvnw -B verify` on Java 21. Testcontainers works on GitHub's Ubuntu runners, which have Docker.
   - **App:** `npm ci`, typecheck, lint, and `expo export --platform web` (catches build breaks).
   - Maven and npm caches, and actions pinned to commit hashes (D11).
   - Then the CI checks are added to `main`'s ruleset as required. **You** make that change: the hook stops me from changing the rules.
2. ✅ **Protect `main`** (D5), with a repository ruleset (free on public repos). Turned on 2026-11-17, apart from the CI checks:
   - every change through a pull request, with the CI checks required once `ci.yml` exists;
   - no force pushes and no deleting `main`;
   - an empty bypass list, so it binds you and me too.
   - **Who reviews.** GitHub never counts a pull request's author's own approval, and the commits I push are made as you (your `gh` sign-in), so your approval of my pull requests doesn't count either. On a one-person repo, "1 approval required" would block every merge. The choices:
     - **(a) You review by merging** *(chosen 2026-11-17)*: approvals required 0. I open each pull request with a summary and the checks, and you read it and merge. On this machine a Claude Code hook stops me from pushing to `main` or merging, so the merge stays yours.
     - **(b) A separate GitHub account for me** (a "machine user"): approvals required 1, and you approve my pull requests. It costs a second account and a token on this machine. Your own changes would then need my approval, which means nothing, so you'd be on the bypass list for them.
     - **(c) A second person** with write access: approvals required 1, and the review is real.
     - Whichever you pick: stale approvals are dismissed when new commits arrive.
3. **Repository settings** (You, with my click-by-click list):
   - turn on secret scanning and push protection (free on public repos);
   - turn on Dependabot security updates for npm, Maven, Actions and Docker.

**Done when:** a pull request shows green checks, a deliberately broken test blocks the merge, and a direct `git push` to `main` is refused.

### Phase 3: the server on Hetzner

1. ✅ **Create the server** (You, following my list; done 2026-11-22: `laa-prod-1`, a CX23 in Nuremberg on **Ubuntu 26.04**, the console's current image, at `2.28.71.82` / `2a01:4f8:1c1b:dee5::1`):
   - CX23 (or CAX11, D7) in Germany or Finland, with your SSH key;
   - a **Primary IPv4 kept separate from the server**, so the address survives a rebuild;
   - Hetzner's own backups (a whole-server copy, +20%) are optional, the owner's call: what can't be rebuilt is the database, which the nightly copies off the server (step 5) protect.
2. ✅ **Cloud Firewall** (done 2026-11-22, tested from outside): ports 80 and 443 open (TCP, plus UDP 443 for HTTP/3); 22 open but key-only. GitHub's runner addresses change, so SSH can't be limited by address unless we add a private network like Tailscale later.
3. **`deploy/bootstrap.sh`** (I write it, you run it once; how: `docs/DEPLOYING.md`):
   - system updates and automatic security updates;
   - Docker, and fail2ban;
   - SSH password login turned off;
   - 2 GB of swap, so images can be built on the server beside the running one;
   - the `deploy` user whose key can only run the deploy script comes with the deploy workflow (Phase 4, #32): until then, deploys are run by hand as root;
   - `/opt/laa` holding the compose file and a root-only `.env`, with the generated secrets (database password, sign-in secret, owner setup code) already in it. **You** type the rest (AI key, email password, owner email) on the server.
4. **DNS** (You): A records for `learnanythinganywhere.app` and `api.learnanythinganywhere.app` pointing at the server's IP. Caddy fetches and renews the certificates itself.
5. **Backups:**
   - a nightly `pg_dump`, keeping 7 days on the server, also copied off the box;
   - a restore drill into a scratch database before launch.
6. **Email** (D8, You): verify the domain with the provider (SPF, DKIM and DMARC records), then put the SMTP settings in the server's `.env`.

**Done when:** `https://api.learnanythinganywhere.app/health` answers 200 with a valid certificate, a test email arrives in a real inbox (not spam), and the restore drill has worked.

### Phase 4: deploy the server and web from `main`

1. **`.github/workflows/deploy.yml`**, run on each merge to `main` once CI passes:
   - build `laa-server` and `laa-web` (the web export built with `EXPO_PUBLIC_API_URL=https://api.learnanythinganywhere.app`);
   - tag both with the commit and push them to GitHub Container Registry;
   - a job in the **`production` environment** waits for your approval, then connects over SSH and runs the deploy script;
   - a smoke test from GitHub checks the health endpoint and that the web page loads.
2. **GitHub secrets** (environment `production`): the deploy SSH key, the host and its `known_hosts` line. Nothing else.
3. **A manual "Roll back" workflow** that redeploys any earlier version.

**Done when:** merging a pull request updates `https://learnanythinganywhere.app` a few minutes after you approve, and a deploy with a broken health check rolls itself back.

### Phase 5: iOS and Android builds

1. **EAS setup:**
   - `eas init`, and EAS-managed signing (iOS certificates and profiles; the Android upload key, with Google's Play App Signing);
   - **You** create an App Store Connect API key and a Google Play service account, and upload both to EAS (not GitHub). I'll give you the steps.
2. **First builds by hand:**
   - `eas build --profile production` for each platform;
   - `eas submit` to TestFlight;
   - **upload the first Android build to Play Console by hand**, because Google only accepts uploads through its API once an app exists. After that, EAS Submit sends them.
3. **`.github/workflows/mobile.yml`**, run when you push a tag like `v1.0.0` (or start it by hand):
   - `eas build --platform all --profile production --non-interactive --auto-submit`;
   - build numbers go up automatically (EAS remote versioning), and the version people see comes from `app.json`;
   - the only secret is `EXPO_TOKEN`.
4. **Later, optional:** over-the-air updates (`expo-updates`, a new dependency) to ship JavaScript-only fixes without a store review.

**Done when:** pushing a version tag puts a new build in TestFlight and in Play's testing track, without your laptop.

### Phase 6: store listings and review

1. **Both stores:**
   - the description and phone screenshots;
   - the privacy policy and support URLs;
   - the age-rating questionnaires (LAA writes text with AI, and children may use it through a parent's account);
   - Apple's privacy labels and Google's Data safety form. I'll draft the answers from what LAA collects: email, display name, what learners type, and the text sent to AI providers.
   - Sign in with Apple isn't required: it's only needed when an app offers third-party sign-in, and LAA has its own email sign-in only.
2. **A review account:** a demo account with a confirmed email and a book on the shelf, for Apple's reviewers. Apps that need a sign-in must supply one.
3. **Google (personal account):**
   - a **closed test with at least 12 testers, opted in for 14 days in a row**;
   - then apply for production access, and release.
4. **Apple:** submit the TestFlight build for review.
5. **The web:** `https://learnanythinganywhere.app` is live from Phase 4. Share it whenever you're ready.

**Done when:** LAA is live on the web, the App Store and Google Play.

### Phase 7: running it

- **Uptime monitoring:** a free uptime monitor on `/health` and the web page, with email alerts. GitHub emails you when a workflow fails.
- **AI spend:** watch it in the owner console. The app's caps and the providers' own budget limits are the guard rails.
- **A runbook** (`docs/RUNBOOK.md`) covering deploying, rolling back, restoring a backup, rotating a key, and signing everyone out (rotating the JWT secret).
- **Later:**
  - a staging server;
  - error tracking (e.g. Sentry, a new dependency);
  - links in emails that open the app on phones (universal links and app links).

## Timeline

- **The critical path is Google's 14-day closed test.** It starts only once an Android build is in the closed track (end of Phase 5).
- **Apple review** usually takes a day or two, but that isn't guaranteed.
- **Phases 1–2 overlap Phase 0's waiting.** A realistic order:
  1. Phase 0 today, in parallel;
  2. Phases 1 and 2;
  3. Phases 3 and 4;
  4. Phase 5, which starts Google's clock;
  5. Phase 6, finishing around two weeks later.

### The web beta first (decided 2026-11-22)

The user asked for the web app in testers' hands before the stores. That needs only part of the plan, worked as a checklist in this order:

0. ✅ Merge interactive diagrams (#61).
1. ✅ Production packaging (Phase 1, step 8; #18).
2. Create the server and its firewall (Phase 3, steps 1–2; #24, #25).
3. The server setup script (Phase 3, step 3; #26).
4. DNS records (Phase 3, step 4; #27).
5. Email sending (Phase 3, step 6; #29).
6. AI keys and spending limits (Phase 0; #7).
7. Nightly backups (Phase 3, step 5; #28).
8. A short privacy page (part of Phase 1, step 6; #16).

The first deploys use `deploy.sh build main` on the server. CI (Phase 2) and the deploy workflow (Phase 4) follow. Until *Delete your account* (#14) is built, a tester who wants their account deleted emails support, and it's done by hand. The beta is for adults until #9 is settled.

## Without a domain

It is technically possible: since January 2026, Let's Encrypt issues free certificates for bare IP addresses. They last only six days, so the server must renew them automatically (Certbot supports this). But:

- **Email:** sending reliably needs SPF and DKIM records on a domain you control. Without them, confirmation emails go to spam or bounce. In LAA, an unconfirmed account can't use the AI, so sign-up stalls.
- **The address is baked into every phone build.** Moving to a domain later means old installs keep calling the IP until people update.
- **The store listings need URLs** for the privacy policy and support, and an IP address looks untrustworthy to reviewers and users alike.

A domain costs less than a month of the server, so buy one.

## Sources

- Let's Encrypt, [6-day and IP address certificates are generally available](https://letsencrypt.org/2026/01/15/6day-and-ip-general-availability) (2026-01-15)
- Google Play Console Help, [App testing requirements for new personal developer accounts](https://support.google.com/googleplay/android-developer/answer/14151465)
- Expo, [EAS pricing](https://expo.dev/pricing) and [billing plans](https://docs.expo.dev/billing/plans/)
- Hetzner Docs, [Price adjustment](https://docs.hetzner.com/general/infrastructure-and-availability/price-adjustment/) (effective 2026-06-15); post-increase prices as reported by [wz-it.com](https://wz-it.com/en/blog/hetzner-price-increase-june-2026-cpx-ccx-alternatives/). One report says Hetzner's cost-optimised plans were temporarily not orderable in October 2026 ([bitdoze.com](https://www.bitdoze.com/hetzner-cloud-cost-optimized-plans/)), so check before Phase 3.
- Apple, [App Review Guidelines](https://developer.apple.com/app-store/review/guidelines/) (5.1.1(v) account deletion; 2.1 demo accounts; 4.8 sign-in services)
