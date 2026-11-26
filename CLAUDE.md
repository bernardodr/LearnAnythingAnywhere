# Learn Anything Anywhere (LAA)

A prototype that turns a learner's *why* into a personal textbook, using the `/teach` method. **`docs/BRIEF.md` is the spec.** Read the relevant section before starting any milestone. `docs/PROGRESS.md` records what's done, what's left and the decisions made so far. `GLOSSARY.md` holds the domain words (safety layer, reader settings, shown text, …): use them in code and docs.

## Workflow

- **Changes reach `main` only by pull request, and the user merges.** Branch `<issue>-<few-words>` from `main`, and open the pull request assigned to the user (`gh pr create --assignee @me`: GitHub never lists your own pull request as a review request) with `Closes #<issue>`, the test results and a summary. Commit and push only when asked. Tasks are GitHub issues on the *LAA production* board; move a card to *In Progress* when you start it. `docs/WORKFLOW.md` has the whole path: tasks, pull requests, deploys and store releases. The `Protect main` ruleset and `.claude/hooks/protect-main.sh` enforce it.
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
  src/lib/        api.ts (fetch + ApiError), session.tsx (token: SecureStore / localStorage), prefs.ts (device preferences),
                  sse.ts (streamed POST), diagramShell.ts (the sealed frame's document + contrast guard), arrange.ts (useArrange:
                  saving the library's order), types.ts
  src/components/ ui.tsx (Page with a pinned footer, TopBar, Button, Field, Note, MarginNote, Ornament, Icon + IconButton, Divider, goBack),
                  Cover (BookFace: a drawn book cover; MaterialGrain: leather or linen; coverForVolume), Motif (the cover drawings), Shelf (the library grid: covers or spines, drag to arrange), MissionCard, LessonBlocks, Diagram + DiagramFrame(.web), Markdown,
                  Tutor (practice + tutor's note + hints, tutor chat)
                  Owner (owner console panels; the screen is src/app/owner.tsx, web only)
                  Settings (reader settings form + read-only summary; the screen is src/app/settings.tsx)
server/   Spring Boot 4 + Spring AI 2, Java 21, Maven wrapper; package com.laa
  llm/      Calls (every model call: task · variant · output), CallsCheck (startup), gateway (DefaultLlmGateway), Allowance,
            routing/ (ModelRouter, capability checks), provider/ (adapters + fake/),
            prompt/, structured/ (schema validation), runs/ (model_runs)
  textbook/ library API + repository             mission/ preface interview (SSE), MissionDraft, MISSION.md
  lesson/   LessonWriter (plan → write → LessonChecks → rewrite once → draw diagrams → save), DiagramRules, LessonChecks (links · safety check · LessonRules ·
            final-attempt decisions), contents + lesson API                                     job/ Jobs (SSE progress)
  safety/   SafetyLayer (the only way learner flows reach a model: admit · reply · write), ReaderView (+ advice: the
            links rule on every learner JSON response), SafetyRules + Category (LAA's own rules), Moderation (fails
            closed), LinkPolicy, TopicGate, LeakCheck (+ LeakSimilarity)
  workspace/ /teach files + versions, Resources, TeachingContext
  profile/  reader settings (Profiles; the brief's "generation profile"): one per account, locked by the parent PIN
  tutor/    Practice (grade → leak guard → hint fallback; hints; evidence), TutorChat
  chat/     chat_messages
  auth/     Accounts (sign-up, sign-in, passwords, email links), Passwords (Argon2id + rules), PinLock (the parent PIN),
            EmailLinks, Throttle, dev sign-in, JWT
  mail/     Mailer (SMTP when SPRING_MAIL_HOST is set; else the in-memory outbox if LAA_MAIL_OUTBOX=true, else it refuses to start)
  owner/    owner API + compare     dev/  /dev smoke endpoints
  security/ SecretRedactor + log converters      web/    ApiException, problem responses, Sse (chat streaming)
README.md  start here for developers (setup, the map, the doc index)
docs/     BRIEF.md (spec), DESIGN.md (the look: the owner's design brief, tokens, books; mockups in design/), PROGRESS.md, WORKFLOW.md (dev → PR → deploy → release), DEPLOYING.md, DATABASE.md, EMAIL.md, PRODUCTION-PLAN.md, product/ (user guide + release notes, for users),
          research/, reference/ (skills/ is a git-ignored clone of mattpocock/skills)
scripts/  m1-switch-default.sh (curl demo of the gateway), redteam.sh (red-team topics against real providers),
          mac-backup.sh (the owner's Mac fetches the nightly backup), restore-drill.sh (restore a backup into a scratch database),
          cover-textures.cjs (makes the leather and linen overlays in app/assets/covers/ from CC0 materials; see its README)
e2e/      run.sh (starts a scratch stack), diagrams.mjs, library.mjs, book.mjs, production.sh + production.mjs (the production stack, locally),
          lib/ (cdp.mjs: headless Chrome over DevTools; serve.mjs: web app + canary; lesson.mjs: sign in, books with missions, lesson 1)
deploy/   production (docs/DEPLOYING.md): server.Dockerfile, web.Dockerfile (Caddy + the web export), compose.yml (Caddy,
          server, Postgres), compose.build.yml, Caddyfile, deploy.sh (back up → switch → check → roll back), .env.example,
          bootstrap.sh (a fresh Ubuntu server: updates, SSH keys only, fail2ban, swap, Docker, /opt/laa, backups),
          backup.sh + laa-backup.service/.timer (nightly pg_dump; `serve` is the backup key's forced command)
dev.sh    one-command dev start
GLOSSARY.md  domain words (safety layer, reader settings, shown text, held back, …)
```

## Commands

| What | Command |
|---|---|
| Start everything (Postgres, Mailpit, server, Expo web) | `./dev.sh` (pass Expo flags to override `--web`, e.g. `./dev.sh --lan`) |
| Emails sent locally (verification, reset links) | Mailpit at http://localhost:8025 |
| Postgres only | `docker compose up -d --wait` · stop with `docker compose down` |
| Server tests (Testcontainers, needs Docker) | `cd server && ./mvnw test` |
| Server alone | `cd server && ./mvnw spring-boot:run` (needs `.env` exported: `set -a; source ../.env; set +a`, plus `SPRING_MAIL_HOST=localhost SPRING_MAIL_PORT=1025` for Mailpit or `LAA_MAIL_OUTBOX=true`) |
| App typecheck / lint | `cd app && npm run typecheck && npx expo lint` |
| Browser walk-through (diagrams: the sealed frame attacked from inside) | `./e2e/run.sh diagrams [screenshot dir]` (scratch database `laa_e2e`, practice mode, no AI keys; needs Chrome) |
| Browser walk-through (the library: drawn covers and motifs, spines, arranging, the empty library; screenshots) | `./e2e/run.sh library [screenshot dir]` |
| Browser walk-through (starting a book with its cover preview, the preface and mission, the book page, reading and writing lessons; screenshots) | `./e2e/run.sh book [screenshot dir]` |
| Production stack, locally (after changing `deploy/`) | `./e2e/production.sh [screenshot dir]` (builds both images, deploys with the real `deploy.sh`, checks it in Chrome, then a backup, a broken deploy that rolls back, a restore, and the nightly backup fetched and restored) |
| Add an app package | `cd app && npx expo install <pkg>` (resolves SDK-compatible versions) |
| Gateway demo (server running) | `./scripts/m1-switch-default.sh [FROM] [TO]` |
| Owner token by hand (dev sign-in on, as `./dev.sh` sets) | `curl -s -X POST localhost:8080/auth/dev-login -H 'Content-Type: application/json' -d '{"email":"owner@laa.dev"}' \| jq -r .token` |

## Conventions and gotchas

- **Comments and Javadoc are written for a developer reading the code for the first time:** plain, full sentences in the words of `GLOSSARY.md`. Every file you create or change meets this; older code catches up one area at a time (issue #56).
  - **Each class, component and package** (`package-info.java`): what it is, why it exists, who uses it and what it relies on, and anything surprising.
  - **Each public method:** what it does, its parameters and result, what can go wrong (with the HTTP status), and its side effects (database writes, emails, AI calls, cost).
  - **Inside a method:** say *why* at each step that isn't obvious, such as why a check comes first or why a row is locked.
  - **Each app screen** opens with what it shows, the data it loads and where it leads. Document component props and hooks.
- **Developer docs stay current in the same pull request:** a migration updates `docs/DATABASE.md`, a change to emails or their settings updates `docs/EMAIL.md`, a new production setting goes in `deploy/.env.example` (and `docs/DEPLOYING.md` if it changes how deploys work), and a new setup step, tool or doc updates `README.md`.
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
- **Cover motifs** (issue #68): each book's cover has one subject drawing from a fixed set, kept in three places that must agree: `Motif.java` (with words for `Motif.guess`), `Motif.tsx` (the drawings; `MotifTest` checks the two lists match), and the list in `mission_interview.draft.v2.md`. The AI picks one in the mission draft (`MissionDraft.motif`), and `MissionInterview.confirm` saves the server's stored pick, not what the app sends back. A book without one shows `Motif.guess` of its title (`Textbook.motif()`). **Materials** follow the colour (`materialFor`: forest and terracotta are leather, ochre and blue linen): `MaterialGrain` tiles an overlay from `app/assets/covers/` (black and white grain at partial strength, so it works on any colour with no blend modes, which the web can't do), offset per book. **Cover colours** are given in turn by volume: `Textbook.COVER_COLORS` and `colors.covers` in `theme.ts` must list the same four in the same order (`CoverColorsTest`), because the new-book screen previews the cloth with `coverForVolume`.
- **Interactive diagrams** (issue #60): the lesson writer places at most 2 `DIAGRAM` blocks (a server-only brief plus a caption). After the lesson passes, `LessonWriter` asks `Calls.DIAGRAM` for each (task `DIAGRAM`), redrawing once if `DiagramRules` refuses the page, and `LessonChecks.withDiagrams` has the final say: page rules, links rule, and the safety check on every readable word (`DiagramRules.words`). A diagram that fails or can't be drawn is left out; it never stops a lesson. The app runs the page only inside the sealed frame (`lib/diagramShell.ts` + `DiagramFrame` / `.web`): CSP with no network, a sandboxed iframe or locked web view, and a bridge that accepts only height and error. The frame also runs a **contrast guard**: when the page changes, HTML text below 4.5:1 against the fill behind it is marked to take ink or ivory (`data-laa-text`), whatever the page's own CSS did. Never loosen the frame or relax `DiagramRules` to make a page work: fix the prompt instead (diagram prompt v2 asks for hands-on patterns: dragging with a button alternative, predict then reveal, build up). `DiagramRules` has page rules (tags, attributes, CSS; any case) and script rules (scripts and `on…=` handlers only, case-sensitive, with strings and comments blanked), so prose never trips a rule. Change the frame only with `./e2e/run.sh diagrams` passing: it attacks the frame from inside (15 request kinds against a canary, storage, cookies, eval, pop-ups, navigation, spoofed messages).
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
- **Sign-in:** email and password through `Accounts`, the only place that issues a token.
  - **`Passwords`** stores Argon2id (OWASP settings, tagged `{argon2}`, weaker hashes redone at sign-in) and owns the rules (15–128 characters after NFKC, `auth/common-passwords.txt`, not the email, not one repeated character). Never put a password field through bean validation: a rejected value can end up in the error message.
  - **`Throttle`** pauses an email after 5 failures in 15 minutes, a network address after 20, and a parent PIN after 5. Check it before checking the secret.
  - **The network address** is `HttpServletRequest.getRemoteAddr()`, which is the client's also behind the proxy: `server.forward-headers-strategy: native` has Tomcat read `X-Forwarded-For` on connections from an internal proxy only (private and loopback addresses by default). Never read `X-Forwarded-For` yourself.
  - **Accounts are for adults** (roles `LEARNER`, `OWNER`). A child uses a parent's account: the parent sets its reader settings for the child. **`PinLock`** (the parent PIN, `users.pin_hash`) once set guards the reader settings (`X-Parent-Pin`), the password and the PIN. A forgotten PIN is replaced with the account password. There are no child accounts.
  - **Tokens carry `pv`, the password version.** A password change or reset bumps it and calls `JwtService.forget`, which ends every older sign-in.
  - **OWNER** only for `LAA_OWNER_EMAILS`. Signing up with one needs `LAA_OWNER_SETUP_CODE`, and an OWNER taken off the list is a learner at their next sign-in.
  - **Dev sign-in** (`/auth/dev-login`, email only) is off unless `LAA_DEV_LOGIN=true` (`./dev.sh` and the test profile turn it on). Its accounts count as verified.
  - **Email links** (`EmailLinks`): random tokens stored only as SHA-256, used once (verify 24h, reset 30 min). Never log a link, a body or an address. A reset request answers the same for any email.
- **Production defaults:** the server's own defaults are safe for production. Dev sign-in, `/dev` (`LAA_DEV_ENDPOINTS`) and the practice providers (`LAA_PRACTICE_PROVIDERS`) are off, and `LAA_DEFAULT_PROVIDER` has no default (a new database without it refuses to start). Without an SMTP host the server refuses to start, unless `LAA_MAIL_OUTBOX=true` keeps emails in memory. `./dev.sh` turns the local switches on; `application-test.yml` sets its own. Request bodies are capped at 256 KB.
- **`Allowance`** runs before every gateway call. A learner's own requests (origin APP) need a verified email (403 `email-unverified`) and must be under the per-learner daily cap (429). Every request must be under the total cap (503). Spend is `model_runs.cost_estimate` since midnight UTC.
- **Tests** extend `IntegrationTest`, which gives a shared context, Testcontainers `postgres:18`, routing re-seeded and the `Throttle` cleared before each test. `login(email)` uses dev sign-in, so its accounts are verified; a `/auth/sign-up` account isn't until a test uses its link. `PASSWORD` passes the password rules. Emails land in `mailer.outbox()` (cleared per test); `emailedToken(email, "/verify-email")` reads a link's token. They use the `FAKE` and `FAKE_B` providers. Make a fake fail with `FakeBehaviour` or `PUT /dev/fake/{provider}/failure`, and pin a reply to exactly one call with `fakes.setCanned(Calls.X, json)` or `setCannedText(Calls.X, text)`. Text containing `FakeBehaviour.BLOCK_MARKER` fails the fake safety check. `safetyCheckOnFakeB()` keeps the safety check up while FAKE fails, and `childReader(age, childSafe)` makes an account set up for a child. Test the safety layer through its interface (`SafetyLayerTest`); `ModerationApiTest` covers each flow end to end. `FakeScripts` holds the scripted offline preface. Read a streamed endpoint with `sse(...)`. The real adapters get canary keys pointed at a dead port, so tests never need real keys or the network.
- **Postgres port:** this machine runs Postgres.app on 5432, so the local `.env` sets `POSTGRES_PORT=5433` and `DATABASE_URL` to match. `docker-compose.yml` reads `POSTGRES_PORT`.
- **App:** before touching an Expo API, read `app/AGENTS.md`. Expo changes between SDKs, so check the versioned docs.
- **App data:** server state lives in TanStack Query. A streamed turn updates the cache with `setQueryData`, never a copied `useState` (lint rejects setState in effects). Links use typed routes (`{ pathname: '/textbooks/[id]', params }`). Screens wait for `useSession().ready`, then `<Redirect href="/" />` when signed out. To return to a screen that's already open (a book's page), use `router.dismissTo`, not `replace`, which stacks a second copy; a screen returned to this way keeps its old data, so refetch in `useFocusEffect`. Components that seed `useState` from query data get a `key` from that data, so fresh data remounts them. Subscriptions (SSE) live in a `useEffect` with state set only in callbacks, and abort in the cleanup.
- **Gestures and animation:** `react-native-gesture-handler` **3** (its hook API: `usePanGesture`, `useHoverGesture`, `useSimultaneousGestures`, `GestureDetector`; the old `Gesture.Pan()` builder is "Legacy") and Reanimated 4. `GestureHandlerRootView` wraps the app in `_layout.tsx`. Callbacks written inline in a gesture config are worklets on the UI thread; helpers they call need `'worklet'`. Reach the JS thread with `scheduleOnRN` from `react-native-worklets`. Use shared values' `.get()`/`.set()` (React Compiler), and respect `useReducedMotion()`. Reanimated 4's `withSpring` defaults `mass` to 4, which halves the damping you'd expect: give `mass: 1` with `stiffness`/`damping` (see `Shelf`'s springs). A drag that ends over a `Pressable` still clicks it on the web, so guard `onPress` (see `Shelf`). Anything draggable needs a non-drag way too (buttons, and `accessibilityActions`): the library has no Arrange mode (the owner's choice), so its books' non-drag way is the ••• menu on each book's page ("Move earlier / later in your library", through `useArrange`), plus screen readers' actions on each book.
- **Dropdowns:** use `Select` from `ui.tsx` (no native picker package). Wide working screens use `<Page wide>`.
- **UI: follow `docs/DESIGN.md`** (Warm Editorial Minimalism, the owner's brief of 2026-11-24, which replaces BRIEF §9's look) whenever you touch a screen: warm ivory, ink, forest green for the one dominant action per screen, Source Serif 4 and Inter, books drawn as physical books (`BookFace`, `Motif`), contents pages, chapter openings, notes in the margin (`MarginNote`), a `TopBar` with a labelled back arrow, and the one dominant action pinned in `Page`'s `footer`. Use the tokens in `app/src/theme.ts` and the pieces in `ui.tsx`, never raw colours or fonts. Contrast must be at least 4.5:1, touch targets at least 44px, every input needs a label, and icon-only buttons need an `accessibilityLabel`. Use typographic apostrophes (’) in JSX text, because lint rejects plain `'`. **No em dashes** in anything a learner reads (the owner's hard rule, issue #80): lint rejects them in app strings and JSX text, and on the server `ReaderView.Reader.apply` runs `Dashes.replace` on every string it touches (AI text as it's written, every learner response on its way out, whatever the links policy); `LessonChecks` filters a diagram's page and `Jobs` its progress messages. Write server messages without them too.
- **Expo Go on a phone:** set `EXPO_PUBLIC_API_URL` in `.env` to the laptop's LAN IP, because `localhost` on the phone means the phone. Native apps skip CORS. `CORS_ALLOWED_ORIGINS` only matters for the web app (default `http://localhost:8081`).
