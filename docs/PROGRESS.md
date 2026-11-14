# Progress

## Status

| Milestone | State |
|---|---|
| M0 Scaffold | ✅ Done (2026-11-01) |
| M1 LLM gateway, plus dev login and JWT | ✅ Done (2026-11-02) |
| M2 Library and mission | ✅ Done (2026-11-09) |
| M3 Lessons | ✅ Done (2026-11-09) |
| M4 Tutor | ✅ Done (2026-11-14) |
| M5 Owner console | ✅ Done (2026-11-14) |
| M6 Safety and settings | ✅ Done (2026-11-14) |
| Refactor: the safety layer as one module | ✅ Done (2026-11-14) |
| Refactor: one reader view for everything shown | ✅ Done (2026-11-14) |
| Refactor: one call descriptor per task and variant | ✅ Done (2026-11-14) |
| Refactor: the lesson checks as one module | ✅ Done (2026-11-14) |

## Refactor: the lesson checks as one module (done 2026-11-14)

From the architecture review (card 4). `LessonWriter.writeNext` used to sequence the checks itself:
- pure helpers: `LessonSafety.links/check/problems/withPlaceholders/unrecoverable`;
- `LessonRules.apply` four times, with `dropBrokenQuizzes` flipping;
- the safety check only when the rules passed, and BLOCKED logged in two places;
- rewrite problems re-parsed from `"id: reason"` strings.

The branches could only be reached through a whole lesson job, and nothing stopped an unchecked lesson being saved.

**Done**
- **`lesson/LessonChecks`:** `check(draft, Attempt.FIRST|FINAL, About)` answers `Pass(CheckedLesson)`, `Rewrite(problems, checked draft)` or `Refuse(error, problems)`.
  - **Every attempt, in one order:** the links rule (`ReaderView`), then the safety check in one batch (logged BLOCKED on a fail), then the lesson rules, once.
  - **First attempt:** any problems of either kind mean `Rewrite`.
  - **Final attempt:** blocked blocks become placeholders and broken quizzes are dropped. Then `Pass`, or `Refuse`: 422 for the reader settings (the title or sheet blocked, or placeholders broke the lesson), or 502 for the rules.
- **Typed problems:** `Problem(kind: LESSON_RULES | READER_SETTINGS, target, message)`. `Moderation.Result` now carries a reason per item (`reasons()`), with `flags()` derived from it, so nothing re-parses strings.
- **Only a checked lesson can be saved:** `LessonRepository.insert` takes a `CheckedLesson`, whose constructor is private to `LessonChecks`. `LessonWriter` is down to plan → write → check → rewrite once → check → save.
- **Deleted:** `LessonSafety`. `ArchitectureTest`'s exception reason for `LessonWriter` is updated.

**Verified**
- `./mvnw test`: **137 tests, 0 failures**.
- **`LessonChecksTest`, 10 branch tests** (written first, red until `LessonChecks` existed). They judge hand-built drafts directly, with no job:
  - a clean draft passes, and lesson 1 drops its warm-up;
  - a rule problem, and a block that doesn't fit (logged BLOCKED), each ask for a rewrite, and both kinds come back together;
  - on the final attempt, a broken quiz is dropped, and a still-blocked block becomes a placeholder (logged);
  - refusals: a blocked title (422), a lesson with no practice block (502), and a placeholder that breaks the lesson (422);
  - the links rule is applied.
- `ModerationApiTest`'s end-to-end title refusal is removed; it duplicated a branch test.
- **Browser:** the tutor walk-through, which writes lesson 1, passes with no console errors.

## Refactor: one call descriptor per task and variant (done 2026-11-14)

From the architecture review (card 3). A model call was three loose arguments (task, prompt variant, output type), declared in two places (`Task.promptVariants`, `TaskSchemas`) and re-derived by the gateway, the practice provider (eight hand-copied property-name sets, plus a guess by shape in `setCanned`) and Compare. Nothing checked they agreed, and `TaskSchemas` allowed one type per task, so Compare couldn't reach the content or leak checks.

**Done**
- **`llm/Calls`: the registry.** It holds 11 descriptors: `TextCall` (streamed) or `StructuredCall<T>`. Each has a task, a variant, an output type and a label, and its id (`TASK[.variant]`) matches the prompt file.
- **The gateway takes descriptors:** `structuredResult(StructuredCall<T>, ctx, profile)` and `stream(TextCall, ctx, profile)`. Passing a text call where a structured result is expected doesn't compile, and the result type comes from the descriptor.
- **The safety layer and every caller pass descriptors.** `SafetyLayer.write(StructuredCall<T>, ctx)`, `reply(TextCall, …)`.
- **Deleted:** `TaskSchemas`, `Task.promptVariants`, `PromptContext.withVariant` and its field, the four `VARIANT` constants, the property-name sets and `sameShape`.
- **`CallsCheck` (a `SmartInitializingSingleton`) refuses to start the app** if a registered call's v1 prompt is missing, its output type has no schema, its task's `Needs` don't cover it, or two calls share an id. RESEARCH's `Needs` now include streaming, because text is only streamed.
- **`ProviderRequest` carries the call.** The practice provider replies per call (`FakeScripts.scripted`), and `setCanned`/`setCannedText` pin a reply to exactly one call.
- **Run log:** each run records its call (migration `V8__run_call.sql`, `model_runs.call`). The owner's run log names the safety checks one by one.
- **Compare:** it runs any registered call (`POST /owner/compare {call, …}`), listed by `GET /owner/calls` with labels. The panel gives a sample request per call, and notes that "Use A/B" moves the whole task when several calls share it.
- **Dev ping:** builds unregistered descriptors (a generic reply on a task's main prompt), and the gateway accepts them.

**Verified**
- `./mvnw test`: **128 tests, 0 failures**.
  - New: `CallsTest` 3 (all registered calls fit; a misfit is caught: a streamed call on an unstreamed task, structured output on a text task, a missing prompt file; lookup by id and task) and `FakeScriptsTest` 1 (every structured call has a practice reply).
  - `OwnerApiTest` +2: Compare runs the content check, and the run log records `SAFETY_CHECK.topic`.
  - `PromptLibraryTest`: a missing prompt names the file.
  - 38 pinned replies moved to calls.
- App typecheck and lint pass.
- **Browser:** the owner-console walk-through passes, plus a new one: Compare on the content check (sample request, the shared-route note, right shape, PASS) and the run log naming *Topic gate* and *Content check*. No console errors.

**Left**
- The practice provider still reads production prompt formats (`- p1:`, `- r1:`, `LEARNER:`, `<item id>`). That was deferred: a structured channel would be a test-only addition to production requests.

## Refactor: one reader view for everything shown (done 2026-11-14)

From the architecture review (card 2). The Decision "links are applied when shown as well as when written" held for only 4 of about 14 surfaces. The misses: lesson titles and subtitles, the tutor's note, hints, the strong answer, tutor-chat and preface history, the topic gate's note, textbook titles, the mission headline, and the raw files endpoint. The allowlist was also built three ways.

**Done**
- **`safety/ReaderView`: the one links rule.**
  - The policy is the learner's current one.
  - The allowlist is every source the learner has added, in any of their textbooks.
  - `of(userId)` gives a `Reader` that applies the rule to a string, or to every string in a JSON tree.
- **`safety/ReaderViewAdvice` (a `ResponseBodyAdvice`).** Every JSON response passes through the reader view on its way out.
  - It's on by default; only `owner..` and `dev..` controllers are left out, since they show raw model output on purpose.
  - It skips error bodies (`ProblemDetail`), unauthenticated requests and Open policies.
  - A new learner endpoint is covered without doing anything.
- **The links policy runs over every string.** It only ever changes web addresses, so ids and other values pass untouched. `LinkPolicy.applyAll` and its `IDENTIFIERS` key list are deleted.
- **Callers on the one rule:** `SafetyLayer` and `LessonWriter`/`LessonSafety.links` now take the rule from `ReaderView`. `LessonController`'s private `Links` record is gone, and pages return stored text as it is.
- **Sources with links Off:** the address now comes back empty (was null); the app already treats both as no link.

**Verified**
- `./mvnw test`: **122 tests, 0 failures**.
- **`ReaderViewTest`:**
  - An address is planted in every stored surface while links are Open, and the test checks the plant landed.
  - Then links go Off, and 12 learner reads (shelf, textbook, mission, contents, lesson, tutor thread, the strong answer, 4 raw files, a new textbook) show no address, with the words around them intact.
  - A unit test covers the tree transform, and another proves the owner console still sees raw output.
- `SafetyLayerTest` adds the cross-textbook allowlist.
- **Browser:** the tutor, strong-answer and reader-settings walk-throughs pass against a fresh database (every adult response now goes through the view), with no console errors.

## Refactor: the safety layer as one module (done 2026-11-14)

From the architecture review (card 1, "Deepen learner-facing generation"): M6 had spread the safety steps over seven files, and two had already gone missing. The choices made are in the Decisions table below.

**Done**
- **`safety/SafetyLayer`, the one way a learner flow reaches a model.** Its interface is three methods:
  - `admit`: checks what the learner typed.
  - `reply`: a reply written in full, with the links policy and the check, returned only once it has passed.
  - `write`: a structured result whose shown text (declared by `ShownText.mapShown`) gets the links policy and the check.
- **What the layer takes over:**
  - It looks up the reader settings itself (from the `PromptContext`).
  - It builds the allowlist from the textbook's sources.
  - It logs a held-back run as BLOCKED.
  - Anything it returns is safe to show: held-back text comes back as the placeholder.
- **Callers:**
  - **Preface:** `MissionController`/`MissionInterview` use `admit`, `reply` (the end marker is stripped in `prepare`) and `write` for the draft.
  - **Tutor:** `TutorController`/`TutorChat` use `admit` and `reply`.
  - **Practice:** `admit`, `write` for grading (a held-back note falls back to the hint) and `write` for evidence (held-back evidence isn't written).
  - None of them depend on `LlmGateway` any more.
- **Every reply is checked before any of it is sent**, for every reader (previously only children's were). The SSE endpoints send one `token` with the whole checked reply; the `replace` event is gone from server and app. The app shows *Your tutor is writing…* meanwhile.
- **Removed:** `ReplyGuard`, `MarkerFilter`, `SafetyRules.strict`.
- **Leak guard:** moved into `safety/` as `LeakCheck`, with `LeakSimilarity` and `LeakVerdict`.
- **`LlmGateway.stream` now returns `LlmStream` (text plus run id).** The callback overload and its default method are gone, so any gateway adapter, test doubles included, has to supply the run id.
- **`Moderation.Unavailable` is now an `ApiException(503)`** with the safety message, not an AI outage. `Sse.stream` shows `ApiException` messages, so the preface, tutor chat and lesson jobs all say "We couldn't run the safety check…".
- **Lesson progress:** shows "Writing lesson n", no longer the model's plan title, since progress is sent before the lesson is checked.
- **`ArchitectureTest` (ArchUnit):**
  - Only `llm..`, `safety..`, `owner..`, `dev..` and a frozen list of exceptions with reasons may depend on `LlmGateway`. Today the only exception is `lesson.LessonWriter`, until card 4.
  - Nothing outside `llm..` may touch Spring AI.
  - A test fixture (`ForgetfulFlow`) proves the first rule fails when broken.
- **`GLOSSARY.md`** (new): the safety vocabulary (safety layer, reader settings, shown text, held back, placeholder, links policy, topic gate, leak guard).

**Defects fixed (each written as a failing test first)**
1. The tutor's note and the mission draft skipped the links policy.
2. When the safety check couldn't run, the preface, tutor chat and lesson writing showed a generic outage message, not the safety message.
3. Learning notes and glossary entries were never checked.
4. Lesson progress sent the model's plan title before any check.
5. Adults' replies were sent before they were checked (the M6 trade-off, now reversed).

**Verified**
- `./mvnw test`: **119 tests, 0 failures**. New or changed:
  - `ArchitectureTest` 3 and `SafetyLayerTest` 8;
  - 6 defect tests in `ModerationApiTest`, trimmed to end-to-end flow checks;
  - `RevealAnswerTest`, split out of `LeakSimilarityTest`.
  - Removed: `MarkerFilterTest`, the strict-profile case in `SafetyRulesTest`, and the child-only reply test.
- App typecheck and lint pass.
- **Browser:** the M4 tutor walk-through and the M6 reader settings walk-through both pass against a fresh test database, with no console errors.

**Left**
- Card 4 (the lesson checks as one module) would remove the `LessonWriter` exception from `ArchitectureTest`.
- Card 2 (one reader view for everything shown) is still open: the links policy at display time covers only some surfaces.

## M6: Safety and settings (done 2026-11-14)

**Done**
- **Generation profiles** (`V7__profiles.sql`, `profile/`):
  - One profile per learner: age (5–18, or adult), child-safe, filters (category keys plus free-text extras), links policy and reveal-after-attempts. `owner_user_id` is who manages it.
  - Adults get `ADULT_DEFAULT` on first use (links ALLOWLIST, answers never revealed).
  - A parent adds a child (`POST /profiles/children`), which creates a learner account under them. Ages under 13 exist only on children's profiles.
  - A child can read their profile but not change it (403). A parent's PIN (BCrypt, `X-Parent-Pin`) is needed for every change once set.
  - Another user's profile is a 404.
  - Every textbook records the profile its topic was checked against (`textbooks.profile_id`).
- **The safety layer (BRIEF §8):** LAA's own deterministic rules plus the `SAFETY_CHECK` classifier, and the stricter wins.
  - **`SafetyRules`:**
    - **Always declined:** intent to cause serious harm (making weapons or drugs, self-harm, sexual content involving minors, account hacking, forgery). It matches intent, not mentions.
    - **Left out by the profile:** the `Category` word lists (all of them in child-safe mode), plus the extras.
    - The word lists avoid common innocent lookalikes ("carbon dating", "naked eye", "bath bomb", "glue gun").
  - **Topic gate:** `TopicGate.withRules` puts the rules on top of the classifier's verdict, and a left-out topic gets a kind decline naming the category.
  - **`Moderation`:** checks a batch of items (the `content` prompt variant, items wrapped in `<item id>` tags). An item the classifier doesn't answer for doesn't pass. If the classifier can't run, it throws `Moderation.Unavailable`, so nothing unchecked is saved or sent (a 503, or an SSE `error`).
  - **Lessons:**
    - `LessonSafety` applies the links policy to every field.
    - It moderates the header, each block (with its rubric and hints) and the reference sheet.
    - A failing block goes back to the writer with the rule problems for one rewrite, then becomes a placeholder note.
    - If the header or sheet still fails, or the placeholders break the lesson rules, nothing is saved (422).
    - Held-back runs are logged `BLOCKED` with `safety_flags`.
  - **Streamed replies** (`ReplyGuard`, interview and tutor chat):
    - **Strict profiles** (child-safe or under 13): the reply is buffered and sent only after the check.
    - **Everyone else:** it streams, then is checked, and a `replace` event swaps the text if the check or the links policy changed it.
    - Only the checked text is stored.
  - **Grading:** the coaching message is moderated after the leak guard; one that fails falls back to the next hint and is logged `BLOCKED`.
  - **Input:** interview answers, practice answers and tutor-chat questions are checked before anything runs. A refused one is a plain 400 and nothing is stored.
  - **Links policy (`LinkPolicy`):**
    - **OFF** keeps the link text but drops the address.
    - **ALLOWLIST** keeps only the learner's own source URLs.
    - It's applied when lessons are written and again when they're shown (`applyAll` over lesson JSON, sheets, glossary, records), so a later change still holds.
    - With OFF, sources are listed without URLs and can't be added (403).
- **Compare** now runs moderation on each side (`checks.moderation`), as for an adult reader, and logs its runs as COMPARE.
- **Reveal answer:**
  - Practice state now carries `revealAfter`.
  - The lesson page offers **Show a strong answer** once hints are used up and enough tries were made, and otherwise says how many tries are left.
- **Practice provider:**
  - It answers the content check with PASS for every item except text containing `[fake:block]` (`FakeBehaviour.BLOCK_MARKER`).
  - A pinned reply (`setCanned`) now answers only calls expecting its shape, so pinning a leak verdict no longer feeds the content check.
- **App (screen 7, `/settings`, linked as *Settings* from the shelf):**
  - **Controls:** child-safe switch; an age stepper (an accessible *adjustable*, 13 to Adult for adults, 5 to 18 for children) with the reading level; "Leave out of lessons" switches plus extras; links (Off / Allowlist / Open) as radios; "Reveal answer after 3 tries".
  - **Parents:** *Your children* (add a child, change each child's settings with the PIN) and the parent PIN lock.
  - **Children:** a read-only summary.
  - **Streaming:** the preface and the tutor chat handle `replace`.
  - **Contrast:** a new `colors.control` token for switch tracks (3:1 on paper).
- **Red team:**
  - `server/src/test/resources/redteam-topics.txt` holds 16 risky topics.
  - `RedTeamTest` runs them for a 9-year-old child-safe profile with the practice provider, whose topic classifier allows everything, so the test proves LAA's own layer holds.
  - `scripts/redteam.sh` runs the same list against whatever `SAFETY_CHECK` is routed to.

**Verified**
- `./mvnw test`: **110 tests, 0 failures**. New:
  - `SafetyRulesTest` 4, `LinkPolicyTest` 5, `ProfilesApiTest` 4, `RedTeamTest` 3, `ModerationApiTest` 8;
  - the reveal test in `TutorApiTest`;
  - the moderation check in `OwnerApiTest`.
- `scripts/redteam.sh` against the practice provider: 16 of 16 declined.
- **Browser:** headless Chrome ran against a fresh test database:
  - a parent opens Settings;
  - the age stepper stops at 13 for an adult;
  - the parent adds Sam (9, child-safe) and sets a PIN;
  - changing Sam's settings: Save is disabled without the PIN, a wrong PIN is refused, the right one saves;
  - Sam sees read-only settings;
  - **demo step 7:** "Sports betting for beginners" is kindly declined;
  - an ordinary topic opens the preface with a checked reply;
  - with reveal on, the lesson offers and shows a strong answer.

  No console errors.
- **Bugs found and fixed:**
  1. The settings screen crashed on load: React Compiler read `me.data.id` from a memoised callback before the profile loaded.
  2. "Saved" vanished at once, because saving refreshed the profile and remounted the form; the parent now holds it.
  3. The app had no way to ask for the answer once the hints ran out.
  4. The links filter collapsed every run of spaces (breaking nested Markdown lists) and swallowed the full stop after an address.

**Left / known gaps (M6)**
- **Real providers:** the red-team script and the moderation prompt haven't been run against a real provider yet. The Anthropic key in `.env` is rejected (401) and needs replacing.
- **Not moderated:** learning records and glossary terms (`RECORD_UPDATE`, written in the background from the learner's own answer). The links policy is applied to them when they're shown. *(Fixed by the safety layer refactor: evidence now goes through `SafetyLayer.write`.)*
- **Fail-closed paths:** the leak guard's own "couldn't be checked" fallback is still there but now rarely reached, because moderation of the learner's answer fails closed first.
- **Profile ownership:** the profile used is the learner's current one, not the one recorded on the textbook (they're the same row today).

## M5: Owner console (done 2026-11-14)

**Done**
- **Screen 8, `/owner`** (web only, OWNER only), wired to the M1 owner API:
  - **Service cards:** status (Default / Ready / Not set up / Turned off), models with tier, and tasks served, with *Make default* and *Turn off/on*.
  - **Default service:** a selector.
  - **Routing table:** per task, the service (*Default* or a specific one), the model (Large / Small / a specific model), fallbacks as ordered toggles, traffic, and the reasons whenever the server refuses a change.
  - **Compare before switching:** task, request, service and size for A and B, side-by-side results with checks, and *Use A/B for this task*.
  - **Run log:** filtered by task and service, with status, timing, tokens, cost and a running total.
- **Compare checks (BRIEF §5.4):**
  - **Quiz rules:** `LessonRules` run on `LESSON_WRITE` output, as for lesson 2 with no sources.
  - **Reading age:** Flesch–Kincaid grade plus 5 (`safety/ReadingAge`), over prose or the strings of a structured result.
  - **Shape check:** a call that fails outright now has no shape verdict, rather than "wrong shape".
  - Moderation follows in M6.
- **App:** a `Select` dropdown and a wide `Page` in `ui.tsx`, and an *Owner console* link on the shelf for owners on web.
- **Owner-facing names:** the stand-in providers are called *Practice mode* / *Practice mode B*, matching the product docs.

**Verified**
- `./mvnw test`: **84 tests, 0 failures** (`ReadingAgeTest` 3, plus compare checks in `OwnerApiTest`).
- **Browser:** headless Chrome ran the console against a fresh test database:
  - signing in as owner and opening the console from the shelf;
  - *Make default*;
  - routing lesson writing to a service with a fallback;
  - a capability refusal shown with its reason;
  - compare with all three checks, then *Use B*;
  - the run log.
  
  No console errors.
- **Bugs found and fixed:**
  1. *Use B* failed when B was already that task's fallback. Switching now removes it from the fallbacks.
  2. Compare defaulted to the first two services. It now defaults to the current default against another one.
  3. Fallback toggles didn't expose a checked state to screen readers. They now set `aria-checked`, as does the Learner/Parent choice at sign-in.

**Left / known gaps (M5)**
- **Traffic splits:** shown but not editable, and not applied by the router (a stretch item since M1).
- **Run log:** shows the latest 50 matching requests, without paging.
- **Prompt versions:** owners can't edit them in the console (the API supports it); there is only v1.

## M4: Tutor (done 2026-11-14)

**Done**
- **Migration `V6__practice.sql`:**
  - `attempts`: every graded answer, with the tutor's note as shown plus leak-guard and run metadata.
  - `practice_progress`: per learner and block, the hints revealed, whether the answer was revealed, when it was met, and the evidence written.
- **Grading** (`tutor/Practice`, BRIEF §6.5):
  - `GRADE` refers to rubric points by id (p1, p2, …), and the server recomputes the verdict from the covered ids (MET / PARTIAL / NOT_YET), so the two can't disagree.
  - The note shows covered points in full (the learner wrote them) and only the **number** still missing, never their text.
  - The coaching move is forced to CONFIRM on MET.
- **Leak guard:** every non-MET coaching message is checked against the missing points and the answer outline.
  - **Two checks:** `LeakSimilarity` runs first (any 5-word run, or at least 60% of the hidden key words, not counting what the learner already wrote), then the `SAFETY_CHECK` "leak" classifier.
  - **On a leak:** the grade is regenerated once with the reason; if it still leaks, the learner gets the next pre-written hint instead.
  - **Fails closed:** if the classifier can't run, the hint is used.
  - Each step is recorded in the attempt's feedback (`leakGuard`); it's never sent to the app.
- **Hint ladder:**
  - `POST …/hint` serves the hints one at a time; a hint used by the leak guard counts as revealed.
  - The answer outline is revealed only if the learner's profile sets `reveal_after_attempts` and that many attempts were made. Everyone has `ADULT_DEFAULT` until M6, so it's never revealed yet.
- **Evidence:**
  - The first time a block is met, `RECORD_UPDATE` runs in the background.
  - It may write `learning-records/000N-slug.md` (`LearningRecords`, /teach format with an Evidence section) and add or revise glossary terms the learner used correctly (`Glossary`, /teach format).
  - Meeting the block again writes nothing new.
- **Tutor chat** (`tutor/TutorChat`): `POST /textbooks/{id}/tutor/messages {content, lessonSeq}` streams a reply, and `GET …?lessonSeq=n` returns the thread, kept per lesson.
  - **What it sees:** the lesson as the learner does (no quiz answers, rubrics or hints) plus the workspace, so it has no hidden answer to leak.
  - **How it answers:** the prompt says to explain knowledge freely and coach practice without solving it.
- **Shared SSE helper:** `web/Sse` now serves both the interview and the tutor chat.
- **Lesson API:** `GET …/lessons/{seq}` includes `practice` state per block (hints shown, latest note, met, evidence), so the page restores on return.
- **Prompts:** `grade.v1.md`, `tutor.v1.md`, `record_update.v1.md` and `safety_check.leak.v1.md`. Records and the glossary follow /teach's rules ("covering material is not evidence", "add a term only when the user understands it").
- **Fake provider:** grades an answer containing "because" as met (otherwise only the first point), passes the leak classifier, and writes a record plus a glossary term.
- **App:**
  - **Practice block (screen 6):**
    - Answer, "Send to your tutor", and the tutor's note: a verdict chip, covered points, "One more point to find", the italic coaching message.
    - "Hint n of 3", "Revise answer", and "Added to What you've learned".
    - "Your tutor won't hand you the answer, but it will help you find it."
  - **"Stuck or curious?"** is a live tutor chat: example questions you can tap, a thread, streamed replies.

**Verified**
- `./mvnw test`: **80 tests, 0 failures** (65 earlier, 15 new).
  - `TutorApiTest` (7):
    - The demo steps 3–4 flow: a partial note with nothing leaked, the hint ladder, met, the learning record and glossary, no duplicate evidence, state restored, metered runs.
    - The leak guard by similarity, by classifier, and failing closed.
    - A grader outage saves no attempt.
    - Validation and privacy.
    - Tutor chat, with the thread kept and nothing saved on a failure.
  - `LeakSimilarityTest` (5) and `EvidenceFormatsTest` (3).
- `npm run typecheck` and `npx expo lint` are clean.
- **Browser:** headless Chrome ran the full tutor journey three times in a row, on a fresh test database where all 6 migrations applied from scratch:
  - a partial answer (the hidden point never on the page), a hint, a revised answer that's met;
  - a tutor chat reply;
  - the learning record and glossary at the back of the book;
  - leaving and coming back with the note and hint restored.
  
  No console errors.
- **Bugs found and fixed by the browser run:**
  1. The contents page didn't refresh after returning from a lesson, so background evidence never appeared; it now refetches on focus.
  2. The first tutor-chat reply could be wiped by a slower history load; the history now loads with the lesson, and a late load is cancelled.
  3. Revisiting a lesson showed stale practice state; the block now remounts when the server's progress changes.
- **Not yet verified:**
  - **Real Claude:** the tutor against a real provider. The Anthropic key in `.env` now returns **401 invalid x-api-key**, although it worked on 2026-11-09 and the file hasn't changed, so it was likely revoked or rotated. The real-provider check of the leak guard and coaching tone is still to do.
  - **Phone:** Expo Go on a phone.

**Left / known gaps (M4)**
- **Moderation:** learner chat input and tutor replies aren't moderated yet (M6).
- **Answer reveal:** never happens until profiles exist (M6), so it's untested end to end.
- **Leak guard tuning:** the similarity check is deliberately simple (a light stemmer, a fixed stop-word list) and may need tuning once real coaching messages are seen.
- **Tutor chat history:** the chat sends the last 20 messages of the lesson thread, with no summarising of longer threads.

## M3: Lessons (done 2026-11-09)

**Done**
- **Migration `V5__lessons.sql`:** a `lessons` table:
  - **Sent to the app:** blocks, reference sheet, title, subtitle, win and mission link.
  - **Kept on the server only:** `rubrics` (rubric points, answer outline and the free-response hints) and the `plan` the lesson was written from.
  - **Tracking:** `model_run_id` and `read_at`.
  - Chat messages now link to lessons.
- **Context assembly** (`workspace/TeachingContext`, BRIEF §6.1): mission, glossary, the 5 most recent learning records, notes and resources go into every lesson prompt. A file that doesn't exist yet is shown as an explicit "none yet".
- **Writing a lesson** (`lesson/LessonWriter`):
  - Load the context, then `LESSON_PLAN` picks one skill in the learner's zone of proximal development, tied to the mission.
  - `LESSON_WRITE` writes the lesson and `LessonRules` checks it.
  - If the check fails, the writer gets the problems listed and rewrites once. On that final attempt, quizzes that still break the rules are dropped, as long as the lesson still works without them. Otherwise nothing is saved.
  - Provider outages and malformed output both end in a clear "nothing was saved" message.
- **`LessonRules`** (BRIEF §7, enforced in code):
  - **Quiz options:** 3–4 distinct options, with lengths within about 20% and word counts within one; no "all/none of the above"-style options. Options are shuffled and the answer index remapped.
  - **Sources:** citations must point to an entry in `RESOURCES.md`; any others are dropped. A primary-source block whose resource doesn't exist is dropped too.
  - **Required parts:** every lesson needs a "check yourself" quiz and a free-response practice with a rubric. From lesson 2 on, it must open with a warm-up; lesson 1's warm-ups are dropped.
  - Block ids are renumbered `b1…bn`.
- **Model format vs app format:** the model writes every block in one compact eight-field shape, because strict structured-output modes require every field. The server converts it into the BRIEF §7 lesson JSON for the app.
- **Jobs** (`job/Jobs`): writing a lesson runs as a job, and `GET /jobs/{id}/events` streams `progress` → `done` / `error`.
  - **Replay:** every event is kept, so a late or returning subscriber gets the whole history.
  - **One per textbook:** asking again returns the running job.
  - **Memory only** (prototype).
- **API:**
  - `POST /textbooks/{id}/lessons/next` (202 plus a job id; 409 until the mission is confirmed and the last lesson is read).
  - `GET /textbooks/{id}/contents`: lessons, the next lesson, and the back of the book.
  - `GET /textbooks/{id}/lessons/{seq}`: no rubrics or hints, plus the learner's sources and previous/next.
  - `POST /lessons/{id}/read`.
  - `POST /textbooks/{id}/resources`: the learner adds a source to `RESOURCES.md`, in /teach format, numbered r1, r2, …
- **Prompts:** `lesson_plan.v1.md` and `lesson_write.v1.md`, ported from /teach and following the structure and tone of the example lessons. Lessons may cite only the learner's own sources; with none, they cite nothing.
- **Fake provider:** writes a lesson containing every block type, which passes the rules, and cites `r1` once a source exists.
- **App:**
  - **Contents page (screen 4):**
    - The mission epigraph, and numbered lessons with dotted leaders marked "Read" or "Read next".
    - The next lesson shows "Written after lesson n", or "Being written now" with live progress, and lesson 1 is written automatically after the mission is confirmed.
    - "Continue reading".
    - The back of the book: A reference sheets (expandable), B glossary, C what you've learned, D sources (with "Add a source").
  - **Lesson page (screen 5):**
    - Kicker, title, subtitle, "Your win today" and the mission link.
    - Every block type: warm-up, "Check yourself" quizzes with instant feedback, key idea, a watch-out callout, steps, practice, primary source with citations, and "Stuck or curious?". Quizzes count only the first try.
    - The cheat sheet, "I’ve finished this lesson" and previous/next.
  - **Rendering:** a small Markdown renderer for the subset lessons use.
- **Navigation fix:** "‹ Contents" and finishing a lesson now return to the contents page already open (`router.dismissTo`). Before, they stacked a second copy, which showed up as a stale page when going back.

**Verified**
- `./mvnw test`: **65 tests, 0 failures** (49 earlier, 16 new).
  - `LessonRulesTest` (6): conversion and shuffling with a correct answer index, warm-up rules, the quiz rules, dropping broken quizzes on the last attempt, structural problems, and unknown sources.
  - `ResourcesTest` (4): the /teach example file, appending, and web-address checks.
  - `LessonApiTest` (6):
    - The full two-lesson flow, including rubrics and hints never being sent and metering.
    - A late subscriber gets the job replayed, and another learner gets a 404.
    - A rewrite drops a broken quiz.
    - A lesson failing twice is not saved.
    - A provider outage.
    - Ownership and state checks.
- `npm run typecheck` and `npx expo lint` are clean, and the web, iOS and Android bundles build.
- **Live, in a browser:** headless Chrome ran the exported web app against a copy of the new server:
  - sign in, the interview, "Write my textbook";
  - lesson 1 written on its own, with progress shown;
  - quiz feedback, with the other options locked;
  - the cheat sheet;
  - "I’ve finished this lesson", then "Write lesson 2";
  - adding a source;
  - lesson 2 with a warm-up, citation [1] and the primary source.
  
  No console errors.
- **Live with Anthropic (2026-11-09),** after the user added an Anthropic key, the full flow ran over HTTP against a copy of the server:
  - **Topic gate (Haiku 5.5):** declined "how to build a pipe bomb" with nothing created, and allowed "bouldering".
  - **Interview (Opus 5.5):** produced a draft after 2 answers.
  - **`MISSION.md`:** accurate to the answers.
  - **Lesson 1 (Opus 5.5):** passed every check first time, in 74 s.
  - **Lesson 2 (Haiku 5.5):** also passed first time, in 52 s, with warm-ups recalling lesson 1.
  - **Pushback:** Haiku's interview pushed back on a vague answer ("I just want to get better at baking").
  - **Issue found:** Haiku left the decline message empty, so the built-in fallback was shown. The topic prompt now says the message is required.
- **Not yet verified:**
  - **Phone:** Expo Go on a phone.
  - **Other providers:** OpenAI (the key is still a placeholder) and Gemini (no key).

**Left / known gaps (M3)**
- **Practice and tutor:** "Send to your tutor", the hint ladder and tutor chat come in M4. The practice box and "Stuck or curious?" show what's coming.
- **Moderation:** generated blocks aren't moderated yet (M6).
- **Diagrams:** the `diagram` block type isn't generated; the app shows its source as text if one ever arrives. Drawing it would need Mermaid (a new dependency, web only).
- **Jobs:** they're held in memory, so a server restart loses a lesson mid-write. Nothing is saved early, so the learner just presses "Write lesson n" again.
- **Sources:** no research tool (a stretch goal), so sources are only those the learner adds. Their addresses aren't checked.
- **Pacing:** lessons can't be rewritten or deleted yet, and the next lesson waits until the last one is finished, as a deliberate pacing rule.

## M2: Library and mission (done 2026-11-09)

**Done**
- **Migration `V4__textbooks.sql`:**
  - `textbooks`: status, cover colour, volume number per learner, the topic gate's verdict, the mission draft, and a one-line mission headline for the shelf.
  - `workspace_files` plus `workspace_file_versions`: every write bumps the version and keeps a copy.
  - `chat_messages`, with `kind` MISSION or TUTOR.
- **Topic gate** (`safety/TopicGate`): every new topic goes through `SAFETY_CHECK` (the "topic" prompt variant) and comes back as one of three verdicts:
  - **ALLOW** creates the textbook with a tidied title.
  - **REFRAME** creates it under the reframed title and keeps the original topic. The gate's note is shown at the top of the preface.
  - **DECLINE** creates nothing and returns the kind message.
  - **Fails closed:** if no provider answers, or the output never validates, the request returns 503 and nothing is created.
- **Mission interview** (`mission/`):
  - `POST /textbooks/{id}/mission/messages` streams one turn as server-sent events: `token`, then `draft` or `draft_failed`, then `done`, or `error` if the turn failed.
  - The interviewer ends by writing `[[MISSION_READY]]`. `MarkerFilter` strips it from the stream even when it's split across chunks, so it's never shown or stored.
  - The server ends the interview after 5 answers whatever the model does, and ignores the marker before the first answer.
  - The draft is a separate structured call (the "draft" prompt variant) over the transcript.
  - A turn's answer and reply are saved together only once the reply finishes, so a failed turn saves nothing and the learner just sends the answer again.
  - `POST …/mission/draft` regenerates the draft.
  - `POST …/mission/confirm` writes `MISSION.md` (the card as shown, including the learner's edits) and moves the textbook to ACTIVE.
- **`MISSION.md`** (`MissionMarkdown`) follows the /teach template exactly. Each field is folded onto one line so typed text can't add headings or bullets, and an empty list is written as "- None stated yet".
- **Library API:** `GET /textbooks`, `POST /textbooks`, `GET /textbooks/{id}`, `GET /textbooks/{id}/mission`, and `GET /textbooks/{id}/files/**` (workspace paths only). Another learner's textbook returns 404.
- **Prompts:**
  - `mission_interview.v1.md` is ported from /teach (mission first, concrete over abstract, push back on vagueness, one question at a time).
  - Two variants: `mission_interview.draft.v1.md` and `safety_check.topic.v1.md`.
- **Prompt variants:** `prompts/<task>.<variant>.<version>.md`. Each task declares its variants (`Task.promptVariants`), and the owner API refuses a prompt version that lacks any of them. Compare runs the same variant the app uses.
- **Fake provider:** a scripted preface (an opening question, one follow-up, then the marker after two answers). Its topic gate allows the topic as typed, and its mission draft is built from the learner's own answers, so the offline demo reads properly. Tests can pin a reply with `FakeBehaviour.setCanned` (structured) and `setCannedText` (streamed).
- **App:**
  - **Title page:** dev sign-in with email and a Learner/Parent choice.
  - **Library:** covers on a shelf with title and volume, and the mission headline in italics underneath. The empty state shows an empty shelf, "Start with" chips and a round + button.
  - **New textbook:** topic entry and the topic gate.
  - **Preface:** the TUTOR / YOU transcript, streaming live, then the mission card with Edit and "Write my textbook".
  - **Contents placeholder:** the title, the mission as an epigraph, and `MISSION.md`.
- **App plumbing:** `lib/session.tsx` keeps the token in the Keychain/Keystore via `expo-secure-store` on phones and in `localStorage` on web. `lib/sse.ts` is a small client for streamed replies over `expo/fetch`.

**Verified**
- `./mvnw test`: **49 tests, 0 failures** (24 from M1, 25 new).
  - `TextbookApiTest` (8): shelf, volumes and covers, isolation between learners, decline, reframe, a reframe with no framing becoming a decline, and fail-closed for both an unavailable provider and output that never validates.
  - `MissionInterviewTest` (6):
    - The full preface ends in an exact `MISSION.md`, with the marker never streamed or stored, events in order, and 4 metered runs.
    - The 5-answer cap.
    - A marker before the first answer is ignored.
    - A failed reply saves nothing.
    - The draft can be retried.
    - Turns and confirmation are validated.
  - Unit tests: `MarkerFilterTest`, `MissionMarkdownTest`, `PromptLibraryTest`.
- `npm run typecheck` and `npx expo lint` are clean. The web, iOS and Android bundles all build (`expo export`).
- **Live, over HTTP:** a copy of the new server on :8090 against the local database ran the whole flow with curl. Replies streamed one `token` event per word, `MISSION.md` was written as version 1, and another learner got a 404.
- **Live, in a browser:** headless Chrome drove the exported web app through sign-in, the empty shelf, a "Start with" chip, the gate, a streamed interview, the mission card, Edit (changed the headline, added a limit), "Write my textbook", the contents page, back to the shelf, then a reload, after which the learner was still signed in. No console errors. The edits reached `MISSION.md`.
- **Not yet verified:**
  - Streaming in Expo Go on a phone, which uses `expo/fetch`'s native streaming. The bundles build, but the phone needs testing by hand.
  - A real provider (no working keys yet).

**Left / known gaps (M2)**
- **Moderation:** chat input and interviewer replies aren't moderated yet; that's M6, which covers all inputs and outputs. Only the topic gate runs now.
- **Profiles:** everyone uses the adult defaults until profiles are stored (M6), so the gate's child-profile behaviour is untested until then.
- **Mission edits after confirming:** a confirmed mission can't be revised yet (/teach's "revise when reality shifts").
- **Interview controls:** there's no "that's enough, draft it now" button; the 5-answer cap covers it.
- **Concurrency:** two turns sent at once on the same textbook aren't prevented.
- **Shelf details:** the reviews-due ribbon isn't shown, because spaced review is a stretch goal. Textbooks can't be archived from the app yet.
- **Contents page:** a placeholder until M3.

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
| 2026-11-09 | **`expo-secure-store`** keeps the sign-in token on iOS and Android (user approved); web uses `localStorage` | BRIEF §0.4 says to ask before adding dependencies. It's Expo's standard token store and works in Expo Go; on web it's an empty stub. |
| 2026-11-09 | Streamed replies use `expo/fetch` plus a small parser, not an SSE package | `expo/fetch` streams response bodies on native and is the browser's fetch on web. `EventSource` can't POST or send an Authorization header. No new dependency. |
| 2026-11-09 | The topic gate **fails closed** (503, nothing created). A REFRAME creates the textbook under the reframed title, keeps the original topic, and shows the gate's note. A REFRAME with no framing counts as a DECLINE. | The safety layer must work as designed (BRIEF §0.7); an unchecked topic must never get through. |
| 2026-11-09 | The interview ends on a model marker (`[[MISSION_READY]]`, stripped server-side), or after 5 answers regardless. The marker is ignored before the first answer. The draft is a separate structured call over the transcript. | Streaming and structured output can't come from one call. The server-side cap enforces "about 2–5 turns" whatever the model does. |
| 2026-11-09 | Prompt variants (`<task>.<variant>.<version>.md`), declared on `Task` | The draft and the topic gate need their own instructions but belong to an existing task's route and version. A version must ship every variant. |
| 2026-11-09 | `MissionDraft` adds a `headline`, stored in `textbooks.mission_headline`, not in `MISSION.md` | The shelf shows a one-line mission under each cover (BRIEF screen 2), and `MISSION.md` keeps the exact /teach format. |
| 2026-11-09 | An answer and its reply are saved together, only once the reply finishes. After the draft appears, the chat closes and edits happen on the card. | A failed turn leaves nothing half-saved, so retrying means sending the same answer again. |
| 2026-11-09 | Spring Security allows `ASYNC` dispatches | A server-sent-events response finishes on an async dispatch of a request that was already authorised. Without this, security rejects it after the response has started. |
| 2026-11-09 | `LESSON_WRITE` returns one compact block shape (`type`, `style`, `text`, `items`, `answer`, `explanation`, `refs`), which the server converts into the BRIEF §7 lesson JSON | Strict structured-output modes require every field, so per-type field names would make the model fill every other type's fields too. The app still gets the BRIEF §7 shape. |
| 2026-11-09 | **Free-response hints stay on the server**, and the block sends `hintCount` instead of `hints` (a change from the BRIEF §7 example) | The hint ladder (M4) serves them one at a time through `POST …/hint`. Sending all three up front would let a learner read the strongest hint first. Easy to reverse if wanted. |
| 2026-11-09 | Quiz rules: lengths within `max(3, 20% of the longest)` characters and word counts within 1. After one rewrite, rule-breaking quizzes are dropped if the lesson still works without them. | That's BRIEF §7's "about 20%" with some slack for very short options. Dropping a quiz beats failing the whole lesson after two attempts. |
| 2026-11-09 | Lesson n+1 can be written only after lesson n is finished. Lesson 1 is written automatically after the mission is confirmed. | /teach plans each lesson from evidence of the last one, and BRIEF screen 4 shows "Written after lesson n". |
| 2026-11-09 | Lesson-writing jobs are held in memory with replayable events | Prototype. Nothing is saved before a lesson passes its checks, so a lost job costs one retry. |
| 2026-11-09 | Learners can add sources (`POST /textbooks/{id}/resources`), in /teach format | BRIEF §6.3: without a research tool, `RESOURCES.md` holds only what the learner adds. This is the only way citations and primary sources can appear. |
| 2026-11-09 | A small in-house Markdown renderer, not a package | Lessons use a small subset (the prompt sets it), so no dependency is needed. |
| 2026-11-09 | **Live routing (user's choice):** the default provider is **Anthropic**, and every task the app uses runs on **Claude Haiku 5.5**. `MISSION_INTERVIEW`, `LESSON_PLAN` and `LESSON_WRITE` moved from LARGE to the SMALL tier; `RESEARCH` (unused) stays LARGE. | Lower cost and faster lessons, and Haiku passed the checks in the live run. This is owner configuration in the local database (`providers`, `model_routes`), set through the owner API, not code. A fresh database still seeds from `Task.java` and `application.yml`. |
| 2026-11-14 | The grader returns rubric point **ids**, the server recomputes the verdict, and the learner sees covered points in full but only the **count** of missing ones | BRIEF screen 6 shows rubric points "covered / not yet". Showing the missing points' text would hand over the answer. |
| 2026-11-14 | Leak guard: similarity first, then the classifier, failing closed. One regeneration, then the next pre-written hint. | BRIEF §6.5, with the safety layer built to spec (§0.7). Pre-written hints are designed not to give the answer away, so they're the safe fallback. |
| 2026-11-14 | The tutor **chat** never sees rubrics or hints; only the grader does, and the leak guard watches the grader | Streaming chat can't be checked before it's shown without losing the streaming. Not giving it the hidden answer prevents leaks by construction. |
| 2026-11-14 | Evidence (`RECORD_UPDATE`) runs in the background, once per met block | The tutor's note shouldn't wait several seconds for record-keeping. The contents page refreshes when it comes back into view. |
| 2026-11-14 | **Product documentation** in `docs/product/` (user guide plus release notes), updated with every new feature (user request) | So the product can be understood, and each release read, without technical knowledge. |
| 2026-11-14 | **LAA's own safety rules run alongside the classifier, and the stricter verdict wins.** The rules match harmful *intent* for everyone, and word lists for categories a profile leaves out. | BRIEF §8: "switching provider never turns it off". The red-team test passes with a classifier that allows everything. |
| 2026-11-14 | Moderation **fails closed by refusing the action** (503 / SSE `error`) rather than showing a placeholder | Nothing unchecked is saved or shown, and the learner can simply retry; a placeholder would store a broken turn. |
| 2026-11-14 | ~~Streamed replies are **buffered for child profiles** (child-safe or under 13) and **streamed then replaced** for everyone else~~ **Superseded the same day** by "every reply is checked before any of it is sent" (below) | BRIEF §8 says replies are checked "before they're saved or sent". For children, nothing unchecked is shown. For adults, streaming stays, and the checked text is what's kept. |
| 2026-11-14 | A lesson block that fails moderation is rewritten once, then replaced by a **placeholder note**. If the title or reference sheet fails, the lesson isn't saved. | BRIEF §8: "regenerated once, then replaced with a safe placeholder and logged". A lesson can't stand without its title or sheet. |
| 2026-11-14 | New adults get links **ALLOWLIST** (was OFF); children get **OFF** | With OFF, adults couldn't add or see their own sources, which M3 relies on. ALLOWLIST still keeps out every link the learner didn't add. |
| 2026-11-14 | The links policy is applied **when shown** as well as when written | A parent who turns links off later expects it to hold on lessons already written. |
| 2026-11-14 | The parent PIN is a 4–8 digit code stored as a BCrypt hash, sent per request in `X-Parent-Pin` | BRIEF §8's "parent PIN stub". It's per change, so there's no unlocked session to forget. `spring-security-crypto` was already a dependency. |
| 2026-11-14 | No new app packages: React Native's `Switch`, and an *adjustable* stepper for the age "slider" | The brief names no slider package. The stepper is accessible (screen-reader increment and decrement) and has 44px targets. |
| 2026-11-14 | **Every reply is checked before any of it is sent, for every reader** (the preface and the tutor chat). Replies arrive whole, not word by word. | BRIEF §8: "before it's saved **or sent**". Streaming-then-replacing sent unchecked words to adults, and made the guide's "nothing was saved or sent" false when the check couldn't run. The cost is waiting for the whole reply (a few seconds); the app says *Your tutor is writing…* |
| 2026-11-14 | **Learner flows never call the LLM gateway; the safety layer does.** This is enforced by `ArchitectureTest` (ArchUnit **1.5.1**, test scope, user-approved), with a frozen exception list whose entries each carry a reason. | Moving the steps into one module wasn't enough: a new flow could still call the gateway and skip them, and no test would fail. The rule lists the allowed callers instead of the four flows, so a new model call inside an existing flow is caught too. |
| 2026-11-14 | Structured results declare their **shown text** (`ShownText.mapShown`). The layer uses that one declaration for both the links policy and the check, and returns held-back text as the placeholder. | A result type can't get one without the other, and a caller that forgets to look at `blocked` still shows only safe text. |
| 2026-11-14 | `LlmGateway.stream` returns the **run id with the text** (`LlmStream`), as `structuredResult` does | The callback had a default method that ignored it, so a second gateway adapter (a test double) would have silently stopped BLOCKED logging. |
| 2026-11-14 | A safety check that can't run is an **`ApiException(503)` with the safety message**, not an AI outage | Every surface (HTTP, SSE, lesson jobs) then shows the message the guide promises, rather than "the interviewer couldn't reply". |
| 2026-11-14 | **The reader view runs on every JSON response by default**, through one `ResponseBodyAdvice`; only the owner and dev tools are left out | Calling it from each endpoint would repeat card 1's problem: the next endpoint forgets, and no test fails. Coverage comes by construction. |
| 2026-11-14 | **One allowlist: every source the learner has added, in any of their textbooks**, used when writing and when showing | It's what the guide promises ("only links to sources you've added"), and read paths like the shelf span several textbooks. |
| 2026-11-14 | **Changed reader settings apply from then on.** Content already written isn't re-checked (user decision); only links follow the new setting at once, because they're applied when shown. | Re-checking older content against stricter settings would cost model calls on every page load, or per-version bookkeeping. |
| 2026-11-14 | With links Off, **no address shows anywhere**, including ones the learner typed | Per-field exceptions for the learner's own words would bring back the key lists card 2 removed. |
| 2026-11-14 | **Every model call is a registered descriptor** (`llm/Calls`), which callers pass instead of task + variant + type. The task stays the unit of routing and metering. | One place to add a call. The three parts can't disagree, the practice provider and Compare read the same list, and the run log can say which call a run was. |
| 2026-11-14 | **The app refuses to start** if a registered call doesn't fit (prompt, schema, task needs) | Mismatches fail when made, in the test suite, not when a learner first reaches the call. |
| 2026-11-14 | Practice-mode replies stay in `FakeScripts`, **keyed by call**, with a test that every structured call has one | Keeps practice-mode content out of the production registry, and still catches a call added without a reply. |
| 2026-11-14 | **Compare runs any registered call;** "Use A/B" still switches the whole task | Routes are per task, so the safety checks always move together. The panel says so. |
| 2026-11-14 | **`LessonChecks` owns the whole judgement of a lesson draft** and answers Pass, Rewrite or Refuse. Safety is checked on every attempt, even when the rules already demand a rewrite. | The branches are testable without a job. One rewrite can fix both kinds of problem; it costs one extra safety-check call, and only when the rules fail. |
| 2026-11-14 | **Only a `CheckedLesson` can be saved.** `LessonWriter` stays the architecture rule's one exception rather than moving lesson writing behind the safety layer. | The type gives the guarantee. A generic "write, review, rewrite once" in the safety layer would widen its interface and pull lesson rules into it. |
| 2026-11-09 | Researched "Sign in with ChatGPT" (user request): see `docs/research/sign-in-with-chatgpt.md` | It exists, but only for selected partners via a waitlist, so it's not available to LAA now. Dev sign-in stays, and a real auth provider is out of scope for the prototype (BRIEF §2). |
