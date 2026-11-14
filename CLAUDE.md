# Learn Anything Anywhere (LAA)

A prototype that turns a learner's *why* into a personal textbook, using the `/teach` method. **`docs/BRIEF.md` is the spec.** Read the relevant section before starting any milestone. `docs/PROGRESS.md` records what's done, what's left and the decisions made so far. `GLOSSARY.md` holds the domain words (safety layer, reader settings, shown text, …): use them in code and docs.

## Workflow

- **One milestone at a time** (BRIEF §11). At the end of each one: run the server tests, then the app typecheck and lint, update `docs/PROGRESS.md` and the product docs, summarise, then stop until the user says to go on.
- **Product docs** (`docs/product/`) explain LAA to its users, not its builders. Every feature a learner, parent or owner can see goes in the user guide (what it is, why it helps, how to use it) and in a new entry at the top of `release-notes.md`, in the same change as the feature. Plain words only: describe what people see and do, never how it works inside (no APIs, models, tables or code).
- **Dependencies:** before adding one the brief doesn't name, ask. Record approved ones under *Decisions* in `docs/PROGRESS.md`.
- **Ambiguity:** when the brief is unclear or contradicts itself, ask.
- **Versions and model IDs:** look them up (Maven Central, npm, provider docs) at the moment you need them.
- **Two systems are built to spec, not as prototypes:** the LLM gateway (BRIEF §5) and the safety layer (BRIEF §8). Everything else can stay prototype-grade.
- **Secrets** live only in `.env`, which is git-ignored. Commit `.env.example`. Never log keys or return them in API responses.

## Layout

```
app/      Expo (React Native + web), Expo Router; screens in app/src/app/, tokens in app/src/theme.ts
  src/lib/        api.ts (fetch + ApiError), session.tsx (token: SecureStore / localStorage), sse.ts (streamed POST), types.ts
  src/components/ ui.tsx (Page, Button, Field, Note, Ornament, goBack), Cover, MissionCard, LessonBlocks, Markdown,
                  Tutor (practice + tutor's note + hints, tutor chat)
                  Owner (owner console panels; the screen is src/app/owner.tsx, web only)
                  Settings (reader settings form + read-only summary; the screen is src/app/settings.tsx)
server/   Spring Boot 4 + Spring AI 2, Java 21, Maven wrapper; package com.laa
  llm/      Calls (every model call: task · variant · output), CallsCheck (startup), gateway (DefaultLlmGateway),
            routing/ (ModelRouter, capability checks), provider/ (adapters + fake/),
            prompt/, structured/ (schema validation), runs/ (model_runs)
  textbook/ library API + repository             mission/ preface interview (SSE), MissionDraft, MISSION.md
  lesson/   LessonWriter (plan → write → LessonChecks → rewrite once → save), LessonChecks (links · safety check · LessonRules ·
            final-attempt decisions), contents + lesson API                                     job/ Jobs (SSE progress)
  safety/   SafetyLayer (the only way learner flows reach a model: admit · reply · write), ReaderView (+ advice: the
            links rule on every learner JSON response), SafetyRules + Category (LAA's own rules), Moderation (fails
            closed), LinkPolicy, TopicGate, LeakCheck (+ LeakSimilarity)
  workspace/ /teach files + versions, Resources, TeachingContext
  profile/  reader settings (Profiles; the brief's "generation profile"), children, parent PIN
  tutor/    Practice (grade → leak guard → hint fallback; hints; evidence), TutorChat
  chat/     chat_messages
  auth/     dev login + JWT + SecurityConfig     owner/  owner API + compare     dev/  /dev smoke endpoints
  security/ SecretRedactor + log converters      web/    ApiException, problem responses, Sse (chat streaming)
docs/     BRIEF.md (spec), PROGRESS.md, product/ (user guide + release notes, for users),
          research/, reference/ (skills/ is a git-ignored clone of mattpocock/skills)
scripts/  m1-switch-default.sh (curl demo of the gateway), redteam.sh (red-team topics against real providers)
dev.sh    one-command dev start
GLOSSARY.md  domain words (safety layer, reader settings, shown text, held back, …)
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
| Gateway demo (server running) | `./scripts/m1-switch-default.sh [FROM] [TO]` |
| Owner token by hand | `curl -s -X POST localhost:8080/auth/dev-login -H 'Content-Type: application/json' -d '{"email":"owner@laa.dev"}' \| jq -r .token` |

## Conventions and gotchas

- **Server:** use `JdbcClient` and Flyway SQL, not JPA. Every table gets a UUID primary key (`gen_random_uuid()`), `created_at` and `updated_at`. Attach the `set_updated_at()` trigger from `V1__baseline.sql`.
- **Spring Boot 4 modules:** autoconfig is split into separate modules. For example, `RestClient.Builder` isn't a bean without `spring-boot-restclient`. Tests build clients with `RestClient.create(...)`.
- **LLM calls** go through `LlmGateway` only. Code outside `llm/provider/` never touches a `ChatClient` or provider SDK, so routing, fallbacks, metering and safety stay in one place.
- **Model calls are registered descriptors (`llm/Calls`).** Every call the app makes is one constant: a `TextCall` (streamed) or a `StructuredCall<T>` (a Jakarta-annotated record, schema-validated). Each names its task (the unit of routing), its prompt variant (null for the main prompt) and an owner-console label. Callers pass the constant (`gateway.structuredResult(Calls.TOPIC_GATE, ctx, profile)`, `safety.write(Calls.GRADE, ctx)`), never a task plus a type.
  - **A new call** is one constant plus its prompt file `server/src/main/resources/prompts/<task>[.<variant>].vN.md`, e.g. `safety_check.content.v1.md`; its id is `TASK[.variant]`.
  - **`CallsCheck` refuses to start the app** if a registered call lacks its v1 prompt, its output type has no schema, or its task's `Needs` don't cover it (text needs streaming; structured needs structured output). Give it a scripted reply in `FakeScripts` too: `FakeScriptsTest` fails otherwise.
  - **Prompts:** `<!-- -->` comments are stripped before sending. A new prompt version is a new file, and the route picks it.
  - **Dev and ad-hoc calls:** the dev ping builds unregistered descriptors; the gateway accepts them.
- **Streaming endpoints:** validate everything that can fail *before* creating the `SseEmitter`, so those failures are plain JSON errors. Run the turn on the `llmExecutor`, send named events (`token`, …, `done`, or `error`), and save a turn only after it finishes. Clients send `Accept: text/event-stream, application/json`.
- **Ownership:** load a learner's textbook with `TextbookRepository.requireForUser`. Another user's id is a 404, never a 403.
- **Lessons:** the model writes `LessonDraft` (one compact block shape). `LessonChecks` is the only path to a stored lesson. `LessonRepository.insert` takes a `CheckedLesson`, which only `LessonChecks` can make. Its `check(draft, FIRST|FINAL, about)` answers `Pass`, `Rewrite` (typed problems) or `Refuse`. Inside, `LessonRules` enforces the quiz rules and citations and moves rubrics and hints into the server-only `rubrics` column. Never send `rubrics` or `plan` to the app. Teaching prompts get their workspace variables from `TeachingContext`.
- **Long-running work** (lesson writing): run it through `Jobs.start` and return the job id; the app follows `GET /jobs/{id}/events`. Save only once the work has passed its checks.
- **Safety (BRIEF §8) is built to spec: learner flows reach a model only through `SafetyLayer`.** `ArchitectureTest` fails if anything outside `llm..`, `safety..`, `owner..`, `dev..` (or its frozen exception list, today only `LessonWriter`) depends on `LlmGateway`.
  - **`admit(userId, textbookId, typed)`:** checks what the learner typed. Call it before any work starts and before opening a stream.
  - **`reply(task, ctx, ifEmpty[, prepare])`:** a reply written in full, checked, then returned for sending and storing. Nothing is sent before the check.
  - **`write(task, ctx, Type)`:** for structured results. The result type implements `ShownText.mapShown` over every string a learner will see, and gets back `Checked(value, blocked, runId)`. `value` is always safe to show (held-back text is the placeholder); the caller decides what blocked means.
  - The layer looks up the reader settings and the links allowlist itself, from the `PromptContext`.
  - **`Moderation.Unavailable` is an `ApiException(503)`.** Never catch it to carry on: unchecked text is never saved or shown.
  - **Display time:** `ReaderViewAdvice` puts every JSON response through the learner's `ReaderView` (their links policy; the allowlist is every source they've added, in any textbook). Owner and dev controllers are left out. Return stored text as it is, and never apply links per endpoint. For the rule at write time, use `ReaderView.of(userId)`.
- **The tutor never hands over the answer.** Learner-facing text from a model that has seen a rubric (the grader) goes through `Practice`'s leak guard; the chat tutor gets no rubrics or hints at all. Show covered rubric points in full and missing ones only as a count: showing a missing point's text is a leak.
- **Model IDs, tiers, capabilities and prices** live in `application.yml` under `laa.llm.providers` and are synced into `providers` on every start. Change them there, never in code.
- **Secrets:** any text that might hold provider output or errors goes through `SecretRedactor.redact` before it's logged, stored or returned. Logback's `%m` and `%wEx` are redacted too (`logback-spring.xml`).
- **Jackson 3** (`tools.jackson.*`): a missing JSON field can't fill a primitive, so optional request booleans must be `Boolean`.
- **Tests** extend `IntegrationTest`, which gives a shared context, Testcontainers `postgres:18` and routing re-seeded before each test. They use the `FAKE` and `FAKE_B` providers. Make a fake fail with `FakeBehaviour` or `PUT /dev/fake/{provider}/failure`, and pin a reply to exactly one call with `fakes.setCanned(Calls.X, json)` or `setCannedText(Calls.X, text)`. Text containing `FakeBehaviour.BLOCK_MARKER` fails the fake safety check. `safetyCheckOnFakeB()` keeps the safety check up while FAKE fails, and `family(age, childSafe)` makes a parent and child. Test the safety layer through its interface (`SafetyLayerTest`); `ModerationApiTest` covers each flow end to end. `FakeScripts` holds the scripted offline preface. Read a streamed endpoint with `sse(...)`. The real adapters get canary keys pointed at a dead port, so tests never need real keys or the network.
- **Postgres port:** this machine runs Postgres.app on 5432, so the local `.env` sets `POSTGRES_PORT=5433` and `DATABASE_URL` to match. `docker-compose.yml` reads `POSTGRES_PORT`.
- **App:** before touching an Expo API, read `app/AGENTS.md`. Expo changes between SDKs, so check the versioned docs.
- **App data:** server state lives in TanStack Query. A streamed turn updates the cache with `setQueryData`, never a copied `useState` (lint rejects setState in effects). Links use typed routes (`{ pathname: '/textbooks/[id]', params }`). Screens wait for `useSession().ready`, then `<Redirect href="/" />` when signed out. To return to a screen that's already open (the contents page), use `router.dismissTo`, not `replace`, which stacks a second copy; a screen returned to this way keeps its old data, so refetch in `useFocusEffect`. Components that seed `useState` from query data get a `key` from that data, so fresh data remounts them. Subscriptions (SSE) live in a `useEffect` with state set only in callbacks, and abort in the cleanup.
- **Dropdowns:** use `Select` from `ui.tsx` (no native picker package). Wide working screens use `<Page wide>`.
- **UI:** use the design tokens in `app/src/theme.ts` (BRIEF §9). Contrast must be at least 4.5:1, touch targets at least 44px, every input needs a label, and icon-only buttons need an `accessibilityLabel`. Use typographic apostrophes (’) in JSX text, because lint rejects plain `'`.
- **Expo Go on a phone:** set `EXPO_PUBLIC_API_URL` in `.env` to the laptop's LAN IP, because `localhost` on the phone means the phone. Native apps skip CORS. `CORS_ALLOWED_ORIGINS` only matters for the web app (default `http://localhost:8081`).
