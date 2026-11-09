# Progress

## Status

| Milestone | State |
|---|---|
| M0 Scaffold | ✅ Done (2026-11-01) |
| M1 LLM gateway, plus dev login and JWT | ✅ Done (2026-11-02) |
| M2 Library and mission | Next, waiting for the go-ahead |
| M3–M6 | Not started |

## M1: LLM gateway, plus dev login and JWT (done 2026-11-02)

**Done**
- **Migrations:**
  - `V2__users.sql`: users, roles, plus `parent_user_id` and `profile_id` (its foreign key arrives in M6).
  - `V3__llm_gateway.sql`: `providers` (at most one default), `model_routes` (one active route per task) and `model_runs` (one row per provider attempt, recording origin, attempt number, error kind and a redacted error message).
- **Provider adapters** (`llm/provider/`):
  - One Spring AI `ChatClient` for each of OpenAI, Anthropic, Google GenAI and Ollama, built at startup only when its key or URL is present.
  - Structured calls use each provider's native JSON-schema mode (`StructuredOutputChatOptions.outputSchema`).
  - Messages are passed as `Message` objects, never through ChatClient's template rendering, so learner text containing `{braces}` is safe.
  - Provider-specific settings live in the adapters. For example, OpenAI gets `max_completion_tokens`, and the SDK's own retries are capped so fallbacks stay fast.
- **Fakes:** `FAKE` and `FAKE_B` give deterministic replies. Structured replies are JSON generated from the requested schema; text replies are a canned sentence per task. Each fake can be switched to fail on purpose (`TIMEOUT`, `RATE_LIMIT`, `SERVER_ERROR`, `PROVIDER_ERROR`, `INVALID_OUTPUT_ONCE`, `INVALID_OUTPUT`).
- **Routing** (`llm/routing/`):
  - `RoutingSeeder` syncs each provider's model catalogue from `application.yml` on every start. It only fills in a missing default or a missing route, so it never overwrites the owner's choices.
  - `ModelRouter` keeps a Caffeine cache that expires after 30s and is cleared right after any owner change commits.
  - `RoutingSnapshot` resolves DEFAULT and model tiers, and runs the capability checks: structured output, tool calling, streaming, minimum context, plus whether each provider is configured and enabled. It checks fallbacks too.
- **Gateway** (`DefaultLlmGateway`):
  - Builds the system prompt from the task's prompt file plus the learner-profile rules.
  - Tries the primary provider, then the fallbacks. Any timeout, 429, 5xx or other provider error moves to the next provider.
  - Each call is wrapped in a timeout on a virtual thread, which a route can override with `params.timeoutMs`.
  - Structured output is validated (JSON, then JSON Schema with networknt, then Jakarta constraints). On failure it retries once with the errors appended, then returns a clear 502.
  - Streaming falls back only before the first token, with first-token and idle timeouts.
  - Every attempt writes a `model_runs` row with tokens, a rough cost, latency and status.
- **Prompts:** `prompts/<task>.v1.md` for all 8 tasks (stubs, filled in by the milestone that implements each task). The route names the prompt version.
- **Auth:**
  - `POST /auth/dev-login` (email in, HS256 JWT out) and `GET /auth/me`.
  - Emails in `LAA_OWNER_EMAILS` get OWNER. Anyone else can choose LEARNER or PARENT, and asking for OWNER is refused.
  - Spring Security resource server, stateless. `/owner/**` and `/dev/**` require OWNER.
- **Owner API** (§5.4):
  - `GET` and `PATCH /owner/providers`.
  - `GET` and `PATCH /owner/routes/{task}` (partial updates; unknown fields and params are rejected; the prompt version must exist).
  - `GET /owner/runs`.
  - `POST /owner/compare`: both routes run in parallel with no fallbacks, and each result is checked against the schema.
  - A change that would break routing returns 400 with the reasons, and nothing is written.
- **Dev endpoints:** `POST /dev/llm/ping` (structured or streamed) and `PUT /dev/fake/{provider}/failure`, behind the OWNER role and the `laa.dev.endpoints-enabled` flag.
- **Secret safety:**
  - `SecretRedactor` masks the configured keys, the JWT secret and common key formats. It's applied to stored error messages, API problem responses and log output (Logback message and stack-trace converters).
  - The settings classes override `toString` so they never print a key.
- `scripts/m1-switch-default.sh` is the curl demo.
- **`dev.sh` fix (2026-11-09):** Ctrl-C used to stop only the subshell, leaving Maven and the app's JVM running on :8080. The next run's `/health` check then passed against the old server, hiding the new one's "port in use" failure. Ctrl-C now stops the whole process tree and waits for the port to free, and `dev.sh` refuses to start while :8080 is taken. Tested with a real SIGINT to the process group, and with a listener holding the port.

**Verified**
- `./mvnw test`: **24 tests, 0 failures**.
  - The five §5.5 acceptance tests are in `GatewayAcceptanceTest`. The fallback test runs four times, once for each failure kind.
  - Structured retry, streaming fallback, the all-providers-fail 503 and metering are in `GatewayBehaviourTest`.
  - Auth, roles, rejected changes, input validation and compare are in `OwnerApiTest`.
  - Redaction and error classification have unit tests.
- **Key leaks:** the leak test drives the real OpenAI, Anthropic and Gemini adapters with canary keys against a dead local port, and confirms all three actually failed. It then checks every response and all captured log output for the keys. It also logs a key on purpose to prove the log converters mask it.
- **Live run** of the server with the real `.env`:
  - The curl script moved the next `LESSON_PLAN` run from FAKE to FAKE_B.
  - Routing `LESSON_PLAN` to OpenAI with the placeholder key made a real call to api.openai.com. It came back 401, was classified `PROVIDER_ERROR` and fell back to FAKE, logged as `FALLBACK`.
  - OpenAI's 401 message echoes the key back, and the stored message shows `[REDACTED]`. The key appeared nowhere in responses or logs.
  - Routing `RESEARCH` to a model without tool calling was rejected live.
- **Not yet verified:** a *successful* call to any real provider (no working keys yet), and Ollama (it isn't running here).

**Left / known gaps (M1)**
- `traffic_split` is stored and returned but not applied yet. The column exists for a future A/B split.
- Compare checks schema validity only. Quiz rules and reading age arrive in M3, moderation in M6. The response lists these under `notYetAvailable`.
- Callers pass `GenerationProfile.ADULT_DEFAULT` until profiles are stored (M6). The profile rules are already appended to every system prompt.
- Only `GRADE` has a registered output type (`TaskSchemas`). Each task's type is added in its own milestone.
- Model notes:
  - OpenAI's models page doesn't say whether models support structured outputs, so I set it to true, as with all current OpenAI chat models. Re-check once there's a real key.
  - `gemini-4-argon` has no stability label on Google's page.
  - Gemini prices use the rates that apply from 2026-12-01.
  - Claude Haiku 5.5 pricing is listed "from" $0.10/$0.50.
  - Prices are only for the rough cost estimate.
- The app is unchanged in M1. The sign-in screen comes in M2.

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
| 2026-11-02 | JWT uses **Spring Security's OAuth2 resource server** (Nimbus, HS256) | It's Spring's standard way to check tokens. I flagged it before starting M1 and there was no objection. |
| 2026-11-02 | Use Spring AI's plain model modules, not the Boot starters | Building the clients ourselves means "only providers with a key" is enforced in one place, and a missing key doesn't break auto-configuration. |
| 2026-11-02 | One `model_runs` row per provider attempt: a failed primary is `ERROR`, and the fallback that answers is `FALLBACK`. Validation retries count toward the same row. | The run log shows exactly what failed and what answered. |
| 2026-11-02 | Structured output that still fails validation after the retry returns an error (502) and doesn't fall back | That's what BRIEF §5.2 says ("retry once … then fail with a clear error"). It's easy to change if we'd rather fall back. |
| 2026-11-02 | Streams fall back only before the first token | Half an answer from one model can't be continued by another. |
| 2026-11-02 | A provider or default change is rejected only for problems it *introduces*. A route change must leave that route with no problems at all. | A key that disappears from the environment shouldn't block unrelated owner changes. |
| 2026-11-02 | Seeded routes set `params.maxTokens` (LARGE 16000, SMALL 4000) | Anthropic requires `max_tokens`, and an explicit value beats library defaults. The owner can edit it. |
| 2026-11-02 | Seeded default model per tier: Anthropic `claude-opus-5-5` / `claude-haiku-5-5`, OpenAI `gpt-6.1-sol` / `gpt-6-luna`, Google `gemini-4-argon` / `gemini-3.5-flash-lite` | Taken from each provider's docs on 2026-11-01. A route can name any listed model through `modelId`. |
| 2026-11-02 | Ollama is seeded **disabled** | It's optional and isn't running locally. The owner enables it once a model is pulled. |
| 2026-11-02 | Spring AI's `MessageAggregator` logging is turned off | It logs every failed stream at ERROR, but the gateway already logs these as redacted warnings. |
