# Progress

## Status

| Milestone | State |
|---|---|
| M0 Scaffold | ✅ Done (2026-11-01) |
| M1 LLM gateway, plus dev login and JWT | Next, waiting for the go-ahead |
| M2–M6 | Not started |

## M0: Scaffold (done 2026-11-01)

**Done**
- Monorepo: `app/`, `server/`, `docs/`, `docker-compose.yml`, `.env.example`, `CLAUDE.md` and `THIRD_PARTY_NOTICES.md`. The brief was renamed to `docs/BRIEF.md`.
- **Server:** Spring Boot 4.1.2 on Java 21 with virtual threads, Flyway, `JdbcClient` and Jakarta Validation.
  - `GET /health` checks the database too.
  - `V1__baseline.sql` adds a `set_updated_at()` trigger function.
  - CORS is set up for the Expo web origin.
  - A Testcontainers test checks `/health`.
- **App:** Expo SDK 58, TypeScript and Expo Router (`src/app/`), with TanStack Query and Newsreader and Inter fonts.
  - Design tokens are in `src/theme.ts`.
  - The title page is built (screen 1). Its Sign in button is disabled until M1, and it shows the server's connection status.
  - The template's demo screens were removed.
- **`./dev.sh`** loads `.env`, starts Postgres (and waits for its health check), starts the server (and waits for `/health`), then starts Expo web.
- `mattpocock/skills` is cloned into `docs/reference/skills` (git-ignored) at commit `4ae2317` (2026-10-31).

**Verified**
- `./mvnw test`: 1 test, 0 failures.
- `npm run typecheck` and `npx expo lint` are clean, and `expo-doctor` passes 20 of 20 checks.
- `./dev.sh` brought up Postgres 18, the server (Flyway applied v1) and Expo web on :8081.
- A headless Chrome screenshot shows the title page rendering with its fonts and "Server: connected".
- Metro built the iOS and Android bundles without errors.
- **Not yet verified:** the app on a physical phone in Expo Go. The user needs to scan the QR code.

**Left / known gaps**
- The example `/teach` lessons were added partway through M0 under `docs/references/lessons/`, so I moved them to `docs/reference/lessons/` as the brief specifies. I haven't read them yet. They're used from M3 on.
- `npm install` reports 25 moderate audit warnings in Expo's dev-tool dependencies. I left them alone, because `npm audit fix --force` would break SDK pins.

## Decisions

| Date | Decision | Why |
|---|---|---|
| 2026-11-01 | Use Spring Boot **4.1.2** with Spring AI **2.0.x** (2.0.2 is the latest GA) | These are the newest stable versions. Initializr confirms Spring AI 2.0 supports Boot `[4.0.0, 4.3.0.M1)`. The 2.1 and 4.2 releases are only RCs so far. |
| 2026-11-01 | Use Expo SDK **58** | This is the current stable SDK. |
| 2026-11-01 | Use `postgres:18` in Docker Compose and Testcontainers, pinned to the major version rather than `latest` | Builds stay reproducible. |
| 2026-11-01 | The Postgres host port is configurable (`POSTGRES_PORT`, default 5432). The local `.env` uses 5433. | Postgres.app already listens on 5432 on this machine. |
| 2026-11-01 | `./dev.sh` is a bash script, not an npm package like `concurrently` | It avoids adding a dependency. |
| 2026-11-01 | **Dev login and JWT move into M1** (the user's choice) | The owner API needs the OWNER role from the start. |
| 2026-11-01 | Dependencies the user approved: Spring AI starters, Testcontainers, **Caffeine** (router cache), **networknt `json-schema-validator`** (lesson and structured-output schemas) | BRIEF §0.4 says to ask before adding dependencies. |
| 2026-11-01 | Add a second fake provider, **`FAKE_B`**, for the offline M1 default-switch demo and tests (user approved) | Lets the demo switch providers without real keys. |
| 2026-11-01 | `expo lint` installed `eslint` and `eslint-config-expo` | This is Expo's standard lint setup. |
| 2026-11-01 | `app/web.output` is set to `single` (a single-page app) instead of the template's `server` | The prototype needs no server rendering. |
