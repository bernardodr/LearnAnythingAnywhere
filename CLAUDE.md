# Learn Anything Anywhere (LAA)

A prototype that turns a learner's *why* into a personal textbook, using the `/teach` method. **`docs/BRIEF.md` is the spec.** Read the relevant section before starting any milestone. `docs/PROGRESS.md` records what's done, what's left and the decisions made so far.

## Workflow

- **One milestone at a time** (BRIEF §11). At the end of each one: run the server tests, then the app typecheck and lint, update `docs/PROGRESS.md`, summarise, then stop until the user says to go on.
- **Dependencies:** before adding one the brief doesn't name, ask. Record approved ones under *Decisions* in `docs/PROGRESS.md`.
- **Ambiguity:** when the brief is unclear or contradicts itself, ask.
- **Versions and model IDs:** look them up (Maven Central, npm, provider docs) at the moment you need them.
- **Two systems are built to spec, not as prototypes:** the LLM gateway (BRIEF §5) and the safety layer (BRIEF §8). Everything else can stay prototype-grade.
- **Secrets** live only in `.env`, which is git-ignored. Commit `.env.example`. Never log keys or return them in API responses.

## Layout

```
app/      Expo (React Native + web), Expo Router; screens in app/src/app/, tokens in app/src/theme.ts
server/   Spring Boot 4 + Spring AI 2, Java 21, Maven wrapper; package com.laa
docs/     BRIEF.md (spec), PROGRESS.md, reference/ (skills/ is a git-ignored clone of mattpocock/skills)
dev.sh    one-command dev start
```

## Commands

| What | Command |
|---|---|
| Start everything (Postgres, server, Expo web) | `./dev.sh` (pass Expo flags to override `--web`, e.g. `./dev.sh --lan`) |
| Postgres only | `docker compose up -d --wait` · stop with `docker compose down` |
| Server tests (Testcontainers, needs Docker) | `cd server && ./mvnw test` |
| Server alone | `cd server && ./mvnw spring-boot:run` (needs `.env` exported: `set -a; source ../.env; set +a`) |
| App typecheck / lint | `cd app && npm run typecheck && npx expo lint` |
| Add an app package | `cd app && npx expo install <pkg>` (resolves SDK-compatible versions) |

## Conventions and gotchas

- **Server:** use `JdbcClient` and Flyway SQL, not JPA. Every table gets a UUID primary key (`gen_random_uuid()`), `created_at` and `updated_at`. Attach the `set_updated_at()` trigger from `V1__baseline.sql`.
- **Spring Boot 4 modules:** autoconfig is split into separate modules. For example, `RestClient.Builder` isn't a bean without `spring-boot-restclient`. Tests build clients with `RestClient.create(...)`.
- **Tests** run against the `FAKE` LLM provider and Testcontainers `postgres:18`. Tests never need real API keys.
- **Postgres port:** this machine runs Postgres.app on 5432, so the local `.env` sets `POSTGRES_PORT=5433` and `DATABASE_URL` to match. `docker-compose.yml` reads `POSTGRES_PORT`.
- **App:** before touching an Expo API, read `app/AGENTS.md`. Expo changes between SDKs, so check the versioned docs.
- **UI:** use the design tokens in `app/src/theme.ts` (BRIEF §9). Contrast must be at least 4.5:1, touch targets at least 44px, every input needs a label, and icon-only buttons need an `accessibilityLabel`. Use typographic apostrophes (’) in JSX text, because lint rejects plain `'`.
- **Expo Go on a phone:** set `EXPO_PUBLIC_API_URL` in `.env` to the laptop's LAN IP, because `localhost` on the phone means the phone. Native apps skip CORS. `CORS_ALLOWED_ORIGINS` only matters for the web app (default `http://localhost:8081`).
