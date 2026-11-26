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
| Email and password sign-in (user request; beyond the brief) | ✅ Done (2026-11-16) |
| Production fixes: review bugs, production defaults, spending caps, email (user request) | ✅ Done (2026-11-16) |
| One account per family: adults only, the parent PIN locks the account (user request) | ✅ Done (2026-11-16) |
| Arrange your shelf: drag books with light physics (user request) | ✅ Done (2026-11-16) |
| A shelf of spines: a compact view with the same physics (user request) | ✅ Done (2026-11-16) |
| A firmer shelf: a solid board, snappy physics, no bounce (user feedback) | ✅ Done (2026-11-16) |
| Production: GitHub Actions pipeline, Hetzner server, web, App Store, Google Play (user request) | 🚧 Phase 1 steps 1–3 and 8 and Phase 3 done; **live at https://learnanythinganywhere.app** (2026-11-22); working through the web-beta checklist (`docs/PRODUCTION-PLAN.md`, *The web beta first*). Tasks are GitHub issues on the *LAA production* board; the process is `docs/WORKFLOW.md` |
| A fresh look: Warm Editorial Minimalism, the owner's design brief (user request; `docs/DESIGN.md`, issue #67) | 🚧 Milestones 1–2 of 5 done: foundation, covers with motifs, the library (2026-11-24); the book page, starting a book, the preface (2026-11-25). Owner feedback (#78, 2026-11-26): leather and linen, arranging from the book page, readable and more hands-on diagrams. Next: reading (#70) |

## Facelift feedback: leather and linen, arranging from the book page, readable and more hands-on diagrams (done 2026-11-26)

Issue #78, part of #67. The owner, after trying milestone 2 on an iPhone: "I think you can remove the arrange button. It doesn't really serve a purpose as it's easy to move the books by dragging them. Also the book cover material texture. I was hoping for more hyper realistic textures of linen or leather book covers… I love the simple images on the book covers… diagrams are looking better but the text in buttons can sometimes be unreadable. It would also be nice if the diagrams could be more interactive if it's appropriate." Decisions (asked first): Arrange moves to each book's page (the mockup's •••), keeping a non-drag way; materials by colour.

**Done**
- **Leather and linen** (`Cover.tsx`): `materialFor` (forest and terracotta: leather; ochre and blue: linen; any other colour: linen) and `MaterialGrain`, which tiles an overlay at 256 points, offset per book (a hash of the title), clipped to the book's corners, under the light and spine gradients. The SVG weave is gone. Spines are bound too.
  - **The overlays** (`app/assets/covers/leather.png`, `linen.png`, 512px, about 330 KB each) come from ambientCG's Leather 030 and Fabric 036 (CC0 1.0). `scripts/cover-textures.cjs` reads each material's colour map (grain) and OpenGL normal map (relief, lit from the top left), blurs out broad blotches with wrap-around so the tile stays seamless, and writes grey-and-alpha pixels: black where darker, white where lighter. Plain alpha compositing over any colour then gives grain and sheen, the same on iPhone, Android and web (React Native Web has no `mixBlendMode`). Sources and settings: `app/assets/covers/README.md`.
- **No Arrange mode** (`Shelf.tsx`): the Arrange/Done button, the hint, the covers' arrow buttons and the spines' select-and-move bar are gone; dragging and screen readers' *Move earlier / later* actions stay. **The book page's ••• menu** (`TopBar` gains a `right` slot) opens over the cover with *Move earlier / later in your library* (disabled at the ends) and *Book 2 of 5 in your library*, live. **`useArrange`** (`lib/arrange.ts`) is the one way to save an order (optimistic, back to the server's on failure), used by the library and the menu.
- **The contrast guard** (`diagramShell.ts`): when a diagram's page changes (class, style, pressed or selected state, hidden, disabled, text; at most every 120ms, and on `transitionend`), every HTML element with its own text is measured against the first fill behind it at 50% opacity or more; below 4.5:1 it's marked `data-laa-text="ink"` or `"paper"`, whichever contrasts more, and a mark is switched again if its fill changes. It runs inside the sealed frame and sends or loads nothing. The cause the owner saw: the page restyled a chosen button's fill, while LAA's own `[aria-pressed="true"]` rule still gave it ivory text.
- **Diagram prompt v2** (`diagram.v2.md`, V16 moves routes still on v1): "Make it hands-on", with drag to explore, predict then reveal, and build it up added to the four patterns; how to drag on a phone (pointer events, `setPointerCapture`, `touch-action: none` only on the dragged thing, a 44px handle, buttons and arrow keys too); don't restyle `.laa-button`; set the text colour with any fill; the new colour names (`--forest`, `--ochre`, `--blue`).
- **Docs:** product: the guide's covers, *Arranging your library* (the ••• menu), the book page's menu, the diagram patterns and readable text, release notes 0.16. Developer: `DESIGN.md` (the binding, the guard), `CLAUDE.md`, `README.md`, `DATABASE.md` (V16), `app/assets/covers/README.md`.

**Verified**
- **`./e2e/run.sh library`:** each cover is bound in its material (The Night Sky and The World of Plants leather, the art and computing books linen, by volume); no Arrange button; **a real mouse drag** of the first cover onto the second swaps them, the server keeps it, and the drag doesn't open the book.
- **`./e2e/run.sh book`:** the ••• menu says *Book 3 of 3*, *Move later* is disabled at the end, *Move earlier* makes it *Book 2 of 3*, and the server's order agrees.
- **`./e2e/run.sh diagrams`:** a chosen button given a pale fill by the page's CSS turns ink, then ivory when its fill turns dark; the diagram's own readable button keeps its green; the seal's 15 attacks, the message rules and the fallbacks all still pass.
- **A bug the walk-through caught:** the guard first chose the wrong colour every time. Its colour parser sat in a JavaScript template string, where `\(` becomes `(` and `\s` becomes `s`, so every colour read as "not a number". The backslashes are doubled now, and the walk-through checks the colours.
- **Server tests:** `PromptLibraryTest` (diagrams start on v2; v2 has the patterns, the dragging rules and the readable-text rule; v1 and v2 both state the frame's limits), `DiagramPromptMigrationTest` (new: V16 moves v1 diagram routes only, and keeps a version the owner chose). Full suite: see the pull request.

**Not covered:** iPhone and Android: the materials are plain `Image` tiles, which both support, but this didn't run on a phone. Diagrams already written keep their pages (only new ones are drawn from v2), though the guard fixes their colours.

## Facelift 2: the book page, starting a book, and the preface (done 2026-11-25)

Issue #69, part of #67. The owner: "Let's do milestone 2", after merging milestone 1 (#76). Built to `docs/DESIGN.md` and the book-page mockup.

**Done**
- **The book page** (`textbooks/[id]/index.tsx`, renamed `BookPage`), as the mockup:
  - a top bar (`TopBar`: a back arrow, "Your library" centred);
  - the drawn cover (about 45% of the column), the title, the mission's headline, and a line of facts (*Volume III · 2 lessons · 1 read*);
  - **Inside this book** as a contents page: numbered *01*, *02* with fine dividers, a tick for a read lesson, *Up next* (and its number) in green, then the next lesson in grey italic (*Written after lesson 1*, *Being written now* with its progress);
  - the mission's why as a note in the margin (`MarginNote`);
  - the back of the book as contents entries A–D that fold open, each with a count on the right like a page number (glossary terms counted from its `**Term**:` lines); Sources holds **Add a source**;
  - the one dominant action pinned at the bottom: **Start reading**, **Continue reading**, or **Write lesson *n*** once every lesson is read (it replaces the old outlined button).
- **Starting a book** (`new.tsx`): a bare back arrow, "What would you like to learn?", and **a preview of the new cover** beside its volume: a dashed outline until a topic is typed, then `BookFace` with the topic tidied as the server will title it, the cloth of the next volume (`coverForVolume`), and the plain ornament (the motif is chosen at the end of the preface). **Begin the preface** is pinned at the bottom.
- **The preface** (`preface.tsx`), as an opening: the book's title in the bar, then *Preface*, *Before we begin*, the ornament and the intro, centred. The tutor's turns are reading text under a *Your tutor* kicker; the learner's answers are margin notes (*You*). The pinned action follows the state: **Send** (busy while a reply streams), **Write my textbook** once the draft is shown (none while editing), **Write the mission draft** after a failed draft, **Try again** if the opening question failed.
- **The mission card** (`MissionCard.tsx`): a flat `noteFill` panel set like a dedication page (green *Your mission* kicker, the headline, ornament, the why in italic, then Success / Limits / Not now under fine rules), with **Edit your mission** as a quiet button. The preface now owns the mission shown and the editing flag, since confirming lives in the pinned footer; the card owns only the form's text.
- **`ui.tsx`:** `TopBar`, `MarginNote` (a 2px forest rule: Chrome draws 1.5px as 1px), and a `check` icon. **`Cover.tsx`:** `coverForVolume`.
- **Server:** the contents response carries the cover (`coverColor`, `coverMotif`, `volume`), so the book page can draw it without loading the library.
- **Docs:** product: the guide's *Your book's page* (was *Your contents page*; the file name and its anchors stay), *Starting a textbook* (the cover preview, the opening, answers in the margin, **Edit your mission**, the pinned buttons), the book page's new name everywhere, and release notes 0.15. Developer: `DESIGN.md` (the book page, openings, the top bar, the pinned action, the preview), `CLAUDE.md`, `README.md`, `GLOSSARY.md` (*Book page*).

**Verified**
- **`./e2e/run.sh book` (new), in Chrome at phone size**, the whole path through the real app:
  - starting a book: the outline, then the preview cover in dusty blue (the third cloth, for volume III), drawn, with *Volume III*; **Begin the preface** pinned;
  - the preface: the opening, the book's title in the bar, **Send** pinned; an answer is a margin note (a 2px forest rule, no fill, a *You* kicker); the mission card with **Write my textbook** pinned;
  - editing the mission: no pinned action while the form is open; the edited goal is shown, and the server keeps it;
  - the book page: the edited goal, *Volume III · 1 lesson · 0 read*, lesson 1 *Up next*, *Written after lesson 1*, the why, the cover drawn; the server's cover is the previewed cloth and the AI's leaf; **Start reading** pinned;
  - the back of the book: A opens to its sheet, B to its empty note, D to **Add a source**, and an added source makes D's count 1;
  - reading lesson 1 ticks it (*all read*), **Write lesson 2** writes it, then **Continue reading**; back in the library the new book is there;
  - no console errors or warnings.
- **Server tests:** `LessonApiTest` checks the contents' cover; `CoverColorsTest` (new) keeps `theme.ts`'s cover colours the same as `Textbook.COVER_COLORS`, in order, so the preview always matches. Full suite: see the pull request.
- `./e2e/run.sh library` and `./e2e/run.sh diagrams` still pass.

**Not covered:** iPhone and Android: nothing here ran on a phone. The pinned footer with the keyboard open (the preface's **Send**) relies on `KeyboardAvoidingView`, which needs a check on a real iPhone.

## Facelift 1: foundation, covers with motifs, and the library (done 2026-11-24)

The owner: "I need you to do a major face lift to the app here is the design brief. And some reference images. Make sure you uses this design brief when touching the ui… rework all the screens." Decisions (asked first):
- Source Serif 4 and `react-native-svg` (new dependencies);
- the library as a grid on ivory, keeping arranging and spines;
- the AI picks each cover's motif from a hand-drawn set;
- notes and highlights included (whole passages, milestone 4);
- search, bookmarks and reading time later (#73–#75).

Five milestones under #67; this is #68. Then: "Make sure to update the product documentation and developer documentation when you are done."

**Done**
- **`docs/DESIGN.md`:** the brief word for word, the mockups (`docs/design/`), and how LAA applies it: colour tokens with contrast ratios, the type scale, space and shape, books, screens and voice. `CLAUDE.md` makes it the rule for any screen change, and BRIEF §9 now points to it for the look.
- **Tokens** (`theme.ts`):
  - colours: ivory `#F7F4EC`, ink `#292823`, forest `#384E41` as the accent, a `field` fill, `danger`, and cover cloths in forest, ochre, dusty blue and terracotta (every text pair checked at 4.5:1 or more);
  - Source Serif 4 and Inter (adding Inter Medium);
  - a type scale (display, h1–h3, body 18/30, small, ui, meta, kicker), and `radius` 10.
  - Newsreader is removed. Raw `#FFFFFF` fills became `colors.field`, so every screen takes the new palette now, ahead of its own milestone.
- **`ui.tsx`:**
  - `Page` gains a pinned `footer` for the screen's one dominant action;
  - buttons are modestly rounded (the primary 52 tall, secondary outlined, quiet green text);
  - fields are sentence-case labels on a lighter fill;
  - new `Icon` and `IconButton` (line icons), and `Divider`;
  - the ornament is now dots and a four-pointed star.
- **Covers** (`Cover.tsx`): `BookFace` draws a physical book:
  - cloth with an SVG weave, light from the top left, and spine depth (a hinge band and crease);
  - a soft two-layer shadow;
  - the title top left in ink or ivory, whichever contrasts with the cloth, sized so no word breaks;
  - one motif, and a terracotta ribbon on a preface in progress.
- **Motifs** (`Motif.tsx`): 21 hand-drawn line drawings in a 100-unit box: stars, moon, leaf, wheat, mountain, wave, gear, dots, shapes, brush, note, flask, atom, globe, quill, column, bars, pulse, pawn, speech, plus the neutral ornament.
- **The library** (`library.tsx`, `Shelf.tsx`):
  - the mockup's page: a centred masthead, "Your library" with its line, a settings icon in place of three header buttons, and **Create a textbook** pinned at the bottom;
  - covers two to a row, with no wooden shelves; spines on a fine ledge;
  - Covers/Spines as quiet underlined choices;
  - the hint shown only while arranging;
  - an empty library shows a dotted outline and the starter topics.
- **Settings** now holds **Sign out** and, for the owner on the web, **Owner console** (they lived in the library's header). The back button is an icon.
- **Diagrams' frame:** the CSS variables take the new palette. The old names (`--slate`, `--olive`, `--navy`) still work for diagrams already written, and buttons are radius 10.
- **Server:**
  - `Motif` (enum, `key()`, `fromKey`, and `guess`, which matches words from the title, with whole-word matching for short words);
  - `MissionDraft.motif` (offered to the model as an enum), and the confirmed draft's motif saved as `textbooks.cover_motif` (V15);
  - `TextbookView.coverMotif`, always set (the pick, or `guess` of the title);
  - cover colours in the new palette, with V15 moving the four old colours by volume;
  - preface prompt v2: the interview is unchanged, and the draft gains the motif list. V15 moves routes still on `v1`.
  - Practice mode's fake draft picks a motif from the book's title.
- **Docs:**
  - product: the guide's *Your library* (renamed from *Your shelf*), every mention of the shelf, the settings icon and signing out, release notes 0.14, and old links fixed;
  - developer: `DESIGN.md`, `DATABASE.md` (V15), `CLAUDE.md` (layout, the cover-motif rule, the library walk-through), `README.md`, and `GLOSSARY.md` (*Library*, *Cover motif*).

**Verified**
- **`./e2e/run.sh library` (new), in Chrome at phone size:**
  - four books with finished prefaces get stars, shapes, dots and leaf; a preface in progress gets wheat, from its title;
  - the page shows the masthead, the title, the settings icon and Create a textbook, with the title in Source Serif 4 and controls in Inter;
  - all five covers are drawn (cloth and motif), two to a row, and no title word is split across lines;
  - spines, and back to covers;
  - the arrows move a book and the server keeps the order;
  - the settings icon opens settings with Sign out, and signing out works;
  - a new learner's empty library invites a first topic;
  - no console errors or warnings.
- **The walk-through found a real bug, now fixed:** on the web, the first book's **›** saved the new order but the book didn't move on screen. A press on an arrow inside the book's drag area left the book "held". The arrows now sit under the book, outside the drag area.
- **It also found title type that split long words** ("Computer / s Think"): titles are now sized so the longest word fits.
- **Server tests:**
  - `MotifTest` (20): titles to motifs; whole-word matching ("art" isn't "artificial", "star" isn't "start"); keys round-trip; the app's `MOTIFS` match the enum; the draft's schema offers every motif;
  - `CoversMigrationTest` (2): V15 recolours the old four by volume, leaves other colours, allows only lower-case motifs, and moves preface routes on `v1` but keeps one the owner chose;
  - `MissionInterviewTest`: the draft carries MOUNTAIN, and the confirm keeps it even when the app sends STARS;
  - `TextbookApiTest`: a new book's `coverMotif` is matched from its title;
  - `PromptLibraryTest`: the preface is on v2, the draft lists every motif, and the interview text is unchanged.
  - Two safety tests' pretend drafts gained a motif, since the schema now requires one. Full suite: see the pull request.
- `./e2e/run.sh diagrams` still passes in the new palette.

**Not covered:** iPhone and Android (the covers and icons use `react-native-svg`, which Expo supports on both, but nothing here ran on a phone). The other screens have the new palette and fonts but not yet their new layouts (milestones 2–5).

## Production: the server, its setup, and nightly backups (done 2026-11-22)

Web-beta checklist items 2–7 (GitHub issues #24–#29, #7). Items 2–6 were mostly done by the owner, with step-by-step lists; this records what was built and checked.

**Done**
- **The server** (#24, #25): `laa-prod-1`, a Hetzner CX23 in Nuremberg, on Ubuntu 26.04, at `2.28.71.82` / `2a01:4f8:1c1b:dee5::1`. Its Cloud Firewall lets in only TCP 22, 80 and 443, UDP 443 and ping. Hetzner's own backups are optional (the owner's call).
- **`deploy/bootstrap.sh`** (#26, PR #63; fix in #64):
  - updates and automatic security updates, restarting at 04:30 UTC when needed;
  - SSH keys only, fail2ban and 2 GB of swap;
  - Docker with capped logs;
  - `/opt/laa`, with `.env`'s secrets generated on the server;
  - and now **step 7, nightly backups** (below).
- **DNS** (#27): A and AAAA records for the apex and `api.`, *DNS only*. Cloudflare's proxy is #65, before opening to new users.
- **Email** (#29): the server has its own Cloudflare token, with only *Email Sending: Edit*.
- **AI** (#7): an OpenAI project `LAA production` with its own key, funded by the owner's ChatGPT Pro API credits benefit.
- **First deploy:** `./deploy.sh build main`, run by the owner. HTTPS certificates came from Let's Encrypt.
- **Nightly backups** (#28):
  - **On the server:** `deploy/backup.sh nightly`, run by `laa-backup.timer` at 03:15 UTC, dumps the database, checks that the dump reads back, and keeps 7 in `/var/backups/laa`.
  - **The fetch:** `backup.sh serve` is the forced command of a `laabackup` user's key. It answers only `latest` and `get <name>`, with a strict name pattern, and its key line is `restrict`. `backup.sh allow` adds a key line.
  - **On the Mac:** `scripts/mac-backup.sh` (`setup`, `fetch`, `status`) uses a launchd agent to fetch at 09:00, or when the Mac wakes. It keeps 30 copies in `~/LAA-backups` and shows a notification on failure.
  - **The restore drill:** `scripts/restore-drill.sh` restores a copy into a scratch database and counts what's in it.
- **Docs:** `docs/DEPLOYING.md` (setting up a new server, nightly backups), `docs/PRODUCTION-PLAN.md`, `CLAUDE.md` and `README.md`. The architecture diagram for the owner is a private page on claude.ai.

**Verified**
- **Firewall, from outside:** 80 and 443 answer through temporary listeners. 8080 is dropped even with something listening, and 3000 and 5432 never reach the server (watched with tcpdump).
- **`bootstrap.sh` on the real server:**
  - password logins are refused, and `sshd -T` confirms it;
  - fail2ban counted two deliberate failed logins (sshd 10 logs as `sshd-session`, and the jail matches it);
  - swap is 2 GB, Docker 29.10.0 runs `hello-world`, and `.env` is 600 with its generated secrets in place (checked by length only).
- **Email:** a test from the server passed SPF, DKIM and DMARC at Outlook, but landed in Junk (SCL 5: a new domain's reputation). Sign-up confirmations work in production.
- **AI:** the key reaches all six of LAA's OpenAI models. A first lesson cost about $0.064: 5 interview turns, a plan, a write plus one rewrite, and 11 safety checks.
- **`./e2e/production.sh` step 7 (new):**
  - the nightly backup reads back;
  - `serve` sends it byte for byte, and refuses `get ../.env`, a `;` after a name, a path, an unknown name, other commands and an empty request;
  - `allow` writes exactly one restricted line however often it's run, and refuses other key types and quote tricks;
  - the Mac's script fetches through a stand-in for ssh that runs the forced command, the copy matches byte for byte, and a second fetch keeps it;
  - the restore drill restores it (1 account, 1 textbook, 1 lesson, migration 14).
- **The trial found a bug, now fixed:** `allow` checked the key line by line, so a value with two lines passed on its first line. It now refuses anything with a line break.
- ShellCheck (warnings and above): no findings in any of the scripts.

**Not covered yet:** the real `sshd` forced command, the systemd timer and the launchd agent. They run when the owner sets backups up on the server and the Mac.

## Production 1.8: production packaging (done 2026-11-22)

Phase 1, step 8 of `docs/PRODUCTION-PLAN.md` (GitHub issue #18), and item 1 of the web-beta checklist. The user: "How can I get my application deployed on hetzner cloud so I can get the web app in people's hands… For beta testing purposes", then "Lets go through this list 1 by 1 like a check list". Nothing a learner sees changes, so there are no product docs.

**Done**
- **Two images**, built from the repository root:
  - `deploy/server.Dockerfile`: the jar, built in a JDK stage and split into Spring Boot's layers. It runs on `eclipse-temurin:21-jre-noble` as an unprivileged user, sized from its memory limit (75% heap), and exits on running out of memory, so Docker restarts it.
  - `deploy/web.Dockerfile`: `npx expo export --platform web` with the API's address as a build argument, then served by `caddy:2.11-alpine`.
  - Each has an allowlist `.dockerignore`, so `.env` can't reach an image (checked: no `.env*` file in either).
- **`deploy/compose.yml`**: Caddy (`web`), `server` and `postgres:18`.
  - Postgres is on an internal network with no route out.
  - The server runs on a read-only file system with all capabilities dropped. Its health check is `/health`.
  - Memory limits: 1.5 GB server, 768 MB Postgres, 256 MB Caddy, on the 4 GB server.
  - Database address and credentials, CORS and the email link address all follow from `.env`.
- **`deploy/Caddyfile`**:
  - automatic HTTPS for both addresses;
  - security headers (HSTS, nosniff, `X-Frame-Options: DENY`, a referrer policy, a permissions policy, no `Server`);
  - a year's caching for hashed scripts and `no-cache` for the page;
  - any unknown path gets the app's page;
  - bodies capped at 1 MB at the door;
  - no access log.
- **`deploy/deploy.sh`**:
  - `build [ref]`, `<tag>`, `rollback` and `status`;
  - a lock so only one deploy runs at a time;
  - a `pg_dump` before every change (the last 10 kept);
  - `docker compose up --wait`, then both sites must answer through this server's own Caddy (`curl --connect-to`), whatever DNS says;
  - on failure, it prints the logs and goes back to the previous version by itself;
  - after a success, older images are removed.
  - It runs on macOS's bash 3, so the local trial uses this exact script.
- **`deploy/.env.example`**: the production settings, with the beta's caps of $1 per learner and $5 in total per day. The laptop-only switches are listed as never to set.
- **`e2e/production.sh` + `e2e/production.mjs`**: the local trial (below). `e2e/lib/lesson.mjs` now holds the setup it shares with `diagrams.mjs`.
- **Docs:** `docs/DEPLOYING.md` (new: what runs, the server's layout, deploying, rolling back, restoring a backup, troubleshooting), `README.md`, `CLAUDE.md`, `docs/WORKFLOW.md` (deploying by hand until Phase 4), and `docs/PRODUCTION-PLAN.md` (step 8 done, and the web-beta checklist).

**Verified**
- **`./e2e/production.sh`: passes** (about 45 seconds once the image layers are cached), with the real images, `compose.yml` and `deploy.sh`:
  1. a first deploy from the working copy;
  2. `production.mjs`, from outside:
     - `/health` and its headers through Caddy;
     - streamed replies (the preface interview and the lesson job) pass through the proxy;
     - the page's headers and caching, and the hashed scripts compressed, cached for a year and built for this API;
     - an app address gets the app;
     - in Chrome, a learner signs in, reloads a lesson by its address, and its diagram runs, with no console errors;
     - 20 failed sign-ins, each claiming a different `X-Forwarded-For`, and the 21st is still paused (429);
  3. a second deploy takes a backup that `pg_restore --list` reads, with the textbooks in it;
  4. a broken server image fails its checks, the logs are shown, and the previous version is live again;
  5. `rollback` goes back one version, keeping the data;
  6. with the textbooks deleted, the restore commands in `DEPLOYING.md` bring them back, and the server is healthy on them.
- **The trial found two bugs in `deploy.sh`, now fixed:**
  - its image clean-up stopped the script when there was nothing to remove (a `grep` with no match, under `pipefail`);
  - the clean-up also matched images outside `LAA_IMAGES`.
- `./e2e/run.sh diagrams` still passes after moving its setup into `lib/lesson.mjs`.
- No server or app code changed. Server tests, typecheck and lint: see the pull request.

**Not covered yet:** the real server, with a real certificate from Let's Encrypt, real DNS and the Hetzner firewall (checklist items 2–4).

## Interactive diagrams in lessons (done 2026-11-18)

The user: "I have noticed a distinct lack of interactive diagrams… it's important that interactive diagrams [are used] if they would be good in helping the learner understand." Then, offered diagrams drawn from data or AI-written HTML: "I really like the html you generate… is there no way we can get away with that style?" Decisions (asked first): AI-written HTML in a sealed frame, `react-native-webview` (new dependency), diagrams as their own AI call with at most 2 per lesson, built now with no prototype. GitHub issue #60.

**Done**
- **Server:**
  - **New task `DIAGRAM`** with `Calls.DIAGRAM` → `DiagramDraft` (title, html ≤ 40,000 characters, text version), prompt `diagram.v1.md`. **V14** allows the task and moves lesson-writing routes from prompt v1 to **`lesson_write.v2.md`**, which can place `DIAGRAM` blocks (a brief and a caption). A new route starts on the newest complete prompt version (`PromptLibrary.latest`).
  - **`LessonRules`:** a DIAGRAM block needs a brief and a caption, a third diagram is dropped, and the brief stays server-only (`Result.diagrams`). The client block starts with only its caption.
  - **`LessonWriter`:** after the lesson passes, it asks for each diagram (progress stage `draw`, "Drawing the diagrams"), redrawing once with the reasons if `DiagramRules` refuses the page. If drawing fails (an outage, or the wrong shape), the diagram is left out.
  - **`LessonChecks.withDiagrams`:** the final say. Page rules, then the links rule on the title and text version, then one safety check over each diagram's words (title, text version, and `DiagramRules.words`: markup text, readable attributes and every script string). A failing diagram is left out and its run marked BLOCKED.
  - **`DiagramRules`:** refuses outside addresses, script `src`, iframes, objects, forms, links, network calls, modules, `eval`, storage, cookies, `window.open`, `parent`, `top`, navigation, `postMessage`, device APIs and `alert`. It strips any document wrapper and SVG `xmlns`.
  - **Practice mode** writes a diagram (the small-step loop), so the whole path can be tried for free.
- **App:**
  - **`lib/diagramShell.ts`:** the sealed document. It has a CSP with only inline script and style plus `data:` images and fonts, LAA's colours as CSS variables, a `laa-button` class, reduced-motion handling, and a bridge that reports only height and errors.
  - **`DiagramFrame.web.tsx`:** `<iframe sandbox="allow-scripts">`, with no referrer and no permissions. It listens only to its own frame.
  - **`DiagramFrame.tsx`:** a `react-native-webview` that loads only the document. It's incognito, with no storage, files, windows, data detectors or scrolling.
  - **`Diagram.tsx`:** the title, the frame, the caption, and *Show as text* / *Show the diagram* (44 px). If the page fails, it falls back to the text version with a note.
  - **Owner console:** a *Diagrams* job label and a Compare sample.
- **Docs:**
  - the guide (*Interactive diagrams* in "Reading a lesson", and the owner's routing table);
  - release notes 0.13;
  - the glossary (Interactive diagram, Diagram brief, Sealed frame);
  - `DATABASE.md`, `CLAUDE.md`.

**Verified**
- `./mvnw test`: **227 passed**.
  - **`DiagramRulesTest` (30):** 24 pages that reach out are refused; ordinary words like "fetch" and "location" pass; wrappers and `xmlns` are stripped; the size limit holds; words are found in markup, attributes, script strings and template literals, and CSS is ignored.
  - **`LessonDiagramsTest` (5):** a drawn diagram is stored with its page and at most 2 are kept (the third is never drawn). A page that keeps reaching out is redrawn once, then left out. A diagram whose words fail the reader settings (text only shown after a tap) is left out, with its run BLOCKED. A wrong-shape answer is left out. When the *Diagrams* route's service is down, the lesson is saved without it.
  - **`LessonApiTest`:** the `draw` stage, and the stored diagram with its page, text and caption. The brief never reaches the app.
- `npm run typecheck` and `npx expo lint`: clean.
- **Browser** (exported web app; practice mode on a scratch database `laa_e2e`, with no AI keys, so no spend):
  - the lesson job runs context → plan → write → check → draw → save, and the diagram shows in LAA's style, the frame growing to fit (372×491);
  - **real clicks** on Next step it from "1 of 4" to "3 of 4", highlighting *Notice*;
  - **from inside the frame**, its own storage, the app's storage, the parent page, the network (`fetch` to the server), cookies and `window.open` are all blocked, and its origin is `null`;
  - *Show as text* and back work, with no console errors or warnings.
- **Not checked:** a real phone (iOS or Android web view), and a diagram drawn by a real AI service. Both are for the user to try: switch the owner console's default from practice mode, or route *Diagrams* to a real service.

**Rigorous testing (user request: "make sure we have rigorous testing for the diagram feature")**

Writing the tests found and fixed four problems:
- **A lesson lost to the allowance.** If the learner hit their daily cap partway through a lesson, the diagram call's refusal threw the whole lesson away, although it was already written and checked. Now the diagram is left out. A safety check that's down still stops everything.
- **Words shown by CSS weren't checked.** `content: "…"` can put words on screen. `DiagramRules.words` now reads CSS strings too.
- **Prose tripped the JavaScript rules.** "Please confirm (once)", or a line starting "Import…", would have refused a page. The rules are now split: page rules cover tags, attributes and CSS; script rules cover only scripts and `on…=` handlers, case-sensitively, with strings and comments blanked. `Function('…')` and `document.write` are now refused, and an unclosed `<script>` is read to the end.
- **A web frame could navigate itself away.** No sandbox flag or CSP rule stops that. The frame can't take any of LAA's data with it, but it can make one request. `DiagramFrame.web` now spots the second load and shows the text version. Phones refuse the navigation before it starts.

The tests now:
- **Server: 281 passed.**
  - `DiagramRulesTest` (70): 53 hostile pages refused (other spellings, unquoted values, SVG `image`/`xlink:href`, `srcset`, `@font-face`, meta refresh, `on…=` handlers, unclosed scripts, …). 7 kinds of prose and ordinary code pass. Words are found in CSS, every readable attribute, script strings and template literals, decoded and each listed once. Normalising twice changes nothing. An empty page is refused.
  - `LessonDiagramsTest` (12): metering (origin APP, user, textbook). Practice still works after a diagram is left out. Reaching the daily cap mid-lesson keeps the lesson. A safety check that's down while diagrams are checked saves nothing. A brief that fails the reader settings becomes a placeholder and is never drawn. The links rule applies to a diagram's text. The stored page is normalised. The owner can route and compare diagrams. Plus the 5 earlier cases.
  - `LessonRulesTest` (+4): only the caption reaches the app and the brief stays under the new id; a missing brief or caption asks for a rewrite; a third diagram is dropped, not sent back; no diagrams means no briefs.
  - `PromptLibraryTest` (+2): the newest complete version per task (lesson writing v2), and what the diagram prompt promises.
  - `DiagramsMigrationTest` (2): the real migrations in a schema of their own, up to V13, with existing routes, then V14. DIAGRAM is refused before and allowed after, only v1 lesson-writing routes move, and a version the owner chose is kept.
- **Browser: `./e2e/run.sh diagrams`**, committed and repeatable; it starts a scratch stack in practice mode with no keys. 8 checks, **passed 3 runs out of 3**:
  - the diagram steps with real clicks, and *Show as text* works and back;
  - **the frame attacked from inside:** 15 kinds of request (fetch, XHR, beacon, WebSocket, EventSource, img, stylesheet, prefetch, iframe, CSS background, font, video, object, script, form) went to a canary that counts hits, and **0 arrived**. Storage, app storage, the app's page, cookies, top navigation, eval, `window.open` and link pop-ups are all blocked, and the origin is `null`;
  - messages: height is clamped to 120–1600, and other message kinds, or messages from the app's own page, change nothing;
  - a script error, or navigating away, shows the text version;
  - no console errors.
  - DevTools' `Runtime.evaluate` lets eval past the CSP by default (`allowUnsafeEvalBlockedByCSP`), which first made eval look allowed. The harness turns that off, so it sees what a real page sees.
- **Still not covered:** iOS and Android web views (no device here); a real AI service drawing diagrams.

## Developer docs (done 2026-11-17)

The user asked for developer documentation that gets a new developer up to speed on the app and the process quickly (GitHub issue #54). Docs only.

**Done**
- **`README.md`** (new; the repo had none), the start-here page:
  - what LAA is, and a diagram of how the parts fit together;
  - the first hour: what to install, `.env`, `./dev.sh`, developer sign-in, Mailpit, and the checks to run;
  - a map of the code, the index of the developer docs, the six rules that matter most, and the board.
- **`docs/DATABASE.md`:** conventions, an entity diagram of all 13 tables, a table per group, what deleting does (and why deleting a user is blocked today, for #14), how to change the database, and the migration history. Read from the local database after `V13`.
- **`docs/EMAIL.md`:** the two emails, sequence diagrams of sign-up and reset, how the links work, the limits, the sending modes (SMTP, outbox, refusing to start), where email goes in each setup, the settings, the tests and the gotchas.
- **`CLAUDE.md`:** points to the three, and asks for them to be updated in the same pull request as the change they describe.
- **`docs/WORKFLOW.md`:** the dashed arrow for store releases now uses the standard `-.->|label|` form.

**Verified**
- The tables, columns, keys, checks and deletion rules come from the local database's catalogue, and the migration history from the files.
- **Not checked:** whether the Mermaid diagrams render. Fetching Mermaid's renderer was refused here, so they were reviewed by hand. GitHub's rich diff on the pull request shows them.

## Production 1.3: email must be set up (done 2026-11-17)

Phase 1, step 3 of `docs/PRODUCTION-PLAN.md` (GitHub issue #13). Server only. The first milestone built on a branch (`13-email-required`) under the pull-request rule; 1.1 and 1.2 were merged in #52.

**Done**
- **`Mailer` refuses to start the server without an SMTP host** (`SPRING_MAIL_HOST`), unless `laa.mail.outbox` (`LAA_MAIL_OUTBOX`, off by default) is on. The message names both settings. Before, a missing host only logged a warning, and in production nobody would have got a confirmation or reset email.
- **The outbox is opt-in:** `application-test.yml` turns it on, and `./dev.sh` is unchanged because it always points SMTP at Mailpit.
- **Port 465** (Cloudflare Email Sending, the user's choice for D8): Spring Boot's own `SPRING_MAIL_SSL_ENABLED=true`, documented in `.env.example` and `application.yml`. No code.
- **`.env.example`** quotes `LAA_MAIL_FROM`: unquoted, its spaces and `<>` break `source .env` (and so `./dev.sh`).
- **Docs:** `CLAUDE.md` (production defaults, the mail module, running the server alone), `.env.example`, `application.yml`. Nothing for the product docs: it's a setting for whoever runs the server.

**Verified**
- `MailerSmtpTest`: `Mailer` sends through a real SMTP server (a Mailpit container), and the email read back from Mailpit's API has the subject, sender name and address, recipient and link. Before it, the SMTP path had no automated test.
- Signing in to Cloudflare's SMTP with the user's `.env` (connect and authenticate, nothing sent): fails with STARTTLS settings, succeeds with SSL on.
- `MailerSetupTest` (3): no SMTP host and no outbox refuses to start, naming `SPRING_MAIL_HOST` and `LAA_MAIL_OUTBOX`; the outbox keeps emails when asked for; an SMTP host is enough on its own. It failed to compile before the change.
- By hand, the packaged server with the local `.env` (which has no SMTP host): it refused to start with the message; with `LAA_MAIL_OUTBOX=true` it started, with the warning; with SMTP pointed at Mailpit it started.

## Production 1.2: a health check that says "down" (done 2026-11-17)

Phase 1, step 2 of `docs/PRODUCTION-PLAN.md` (GitHub issue #12). Server only. Also the workflow document and the task board.

**Done**
- **`GET /health`** already existed and checked the database, but an outage reached the generic error handler as a 500. Now it answers **503 `{"status":"DOWN","database":"DOWN"}`** and logs one warning. Still public, and it says nothing more than up or down. The plan wrongly said there was no health check; it's corrected.
- **`docs/WORKFLOW.md`:** the path from a task to the stores, in Mermaid diagrams (the whole path, what protects `main`, a deploy), with who does what, the day-to-day commands, the phone release steps and what to do when something goes wrong. Planned parts are marked with their phase. `CLAUDE.md` points to it and states the pull-request rule.
- **The board:** GitHub project *LAA production* (https://github.com/users/bernardodr/projects/1), linked to the repo, with all 51 issues on it.

**Verified**
- `HealthControllerTest`: a database that can't be reached gives 503 (it failed to compile before: there was no status to read). `ServerApplicationTests` still gets 200 with the database up.
- By hand: a packaged server on port 8091 answered 200, then 503 at once after `docker compose stop postgres`, then 200 again after the database was started.

## Production 1.1: real client addresses behind the proxy (done 2026-11-17)

The first milestone of `docs/PRODUCTION-PLAN.md` (Phase 1, step 1; GitHub issue #11). Server only; no new dependency.

**Done**
- **Repo setup for the plan:**
  - `main` is protected by the GitHub ruleset *Protect main*: pull requests only, no force pushes or deletion, nobody bypasses, 0 approvals (the user reviews by merging).
  - `.claude/hooks/protect-main.sh` (in `.claude/settings.json`) blocks Claude from pushing to `main`, merging or approving a pull request, and changing the branch rules. 28 cases were tried by hand, and it blocked a real dry-run push.
  - The plan is GitHub issues #1–#51: one per phase, each step a sub-issue; label `you` for the user's own steps.
- **Behind a proxy, the client's address is used:**
  - `server.forward-headers-strategy: native` makes Tomcat's `RemoteIpValve` set `getRemoteAddr()` from `X-Forwarded-For`. It does so only on connections from an internal proxy (private and loopback addresses by default; `SERVER_TOMCAT_REMOTEIP_INTERNALPROXIES` narrows them).
  - So the sign-in throttle, the sign-up limit and the reset-request limit count each person again, not the proxy's one address. No controller changed.
- **Docs:** `CLAUDE.md` (the network address rule) and the `Throttle` comment. Nothing for the product docs: people won't see a difference until LAA runs behind the proxy.

**Verified**
- `ForwardedAddressTest`: the sixth sign-up from one forwarded client is refused while another client behind the same proxy carries on, and addresses a client puts ahead of the proxy's entry don't count. It failed before the change.
- `UntrustedProxyTest` (loopback taken off the internal list): a forwarded address on a connection from anyone else is ignored.
- Server suite 187 passed; app typecheck and lint clean.

## A firmer shelf (done 2026-11-16)

User feedback on the spines: "I want the bookshelf to be solid and the books less bouncy, the physics should feel snappy." App only; no server change and no new dependency. It replaces the bounce, wobble, swing and board dip described in the two sections below.

**Done**
- **Why it was bouncy:** Reanimated 4's `withSpring` defaults `mass` to 4, and the springs gave only `stiffness` and `damping`. So every spring was about half as damped as written (the drop's bounce had a damping ratio near 0.15).
- **New springs in `Shelf`, each with `mass: 1`:**
  - `GLIDE` (books making room): damping ratio 0.94.
  - `LIFT` (pick up, hover, a chosen spine rising): 0.92.
  - `SETTLE` (landing, and anything coming back upright): stiffness 900, ratio 0.7, with `overshootClamping`, so it arrives fast and stops dead at its place.
  - `BOUNCE`, `WOBBLE` and `SWAY_BACK` are gone.
- **A drop:**
  - Covers and spines both land with `SETTLE`.
  - The knock is a quick tap that goes straight back: 1.5° for covers; for spines, three books each side, 2.5° divided by distance, 30 ms apart.
- **The lean while dragging** is up to 6° (was 10°) and comes back upright with `SETTLE`.
- **The board is solid:** no dip, no animation. A 14 px plank (was 8) with a lit top edge, a front face and a shadow on the wall. The bookend is in the same wood. The colours are new `colors.shelf` tokens.
- **Two things that looked like a bounce:**
  - The saved order coming back from the server re-ran the glide reaction and restarted the landing with the looser spring. It now glides only when the place changes.
  - On the web, hover ends and restarts when the mouse button comes up, so a dropped spine landed and then rose 8 px under the pointer. A 250 ms `landing` window after a drop skips that restart. The next real hover pulls it out as before.
- **Docs:**
  - the guide (*Arranging your shelf*);
  - release notes 0.10 and 0.11, edited in place since neither has shipped;
  - CLAUDE.md (the `mass` gotcha).

**Verified**
- `npm run typecheck` and `npx expo lint`: clean.
- **A frame-by-frame trace of a spine drop** (`drop-trace`, exported web app). The dropped spine:
  - starts 31 px above its place and rests by 131 ms;
  - never goes past its place (0.00 px) and never changes direction.
  - Its lean is upright by 131 ms.
  - The spine beside it is knocked 2.5° and back by 182 ms, without passing upright.
  - The boards' positions didn't change in any frame.
  - Hover still pulls the spine out 8 px once the pointer leaves and comes back.
- **The covers and spines walk-throughs** both pass with no console errors.
- **Not checked:** a real phone.

## A shelf of spines (done 2026-11-16)

The user asked for a compact ("minimised") view of the shelf, with the books spine out, standing on the shelf, and the same physics. App only; no server change and no new dependency.

**Done**
- **`Shelf` shows covers or spines through one layout model.** `place(id, order, layout)` and `target(...)` (worklets) cover both, so drag, glide, saving the order, the arrange buttons and reduced motion work the same in each view.
  - **Spines:**
    - Books flow left to right from a bookend and wrap onto more shelves.
    - Each spine's thickness (44–58 px, so at least the 44 px touch target) and height (150–190 px) come from a hash of its id, so they're stable.
    - **The spine itself:** the cloth colour, a lit and a shaded edge, two bands, and the title set down the spine. The type fits the length: 15 down to 13 px on one line, or two lines. The volume numeral sits at the foot.
    - A book still in its preface has a red ribbon bookmark.
  - **Spine physics:**
    - A held spine swings from its top (`transformOrigin` at the top) and lifts 14 px.
    - A drop bounces it back onto the board (a lighter-damped spring).
    - It sends a ripple of knocks along the row: up to four books either side lean away, 4° divided by their distance, each 45 ms later than the last. The knocks pivot at the books' feet.
    - The board still dips, and hovering lifts a spine 8 px.
  - **Arrange on spines:** the spines are too narrow for buttons under each, so a tap chooses a book (it rises, with an outline) and *‹ Earlier* / *Later ›* move it. Screen readers keep *Move earlier/later* on every book.
  - **Switching view:** the books glide from one layout to the other.
- **The Covers / Spines switch** is a radio group in the shelf's bar. The choice is kept per device by `lib/prefs.ts` (localStorage on the web, SecureStore on phones) and read through TanStack Query.
- `Cover` now exports `volumeNumeral`, `bookLabel` and `MOVE_ACTIONS` for the spines to share.
- **Docs:** the guide (*Covers or spines*, and the arranging text) and release notes 0.11.

**Verified**
- `npm run typecheck` and `npx expo lint`: clean. No server code changed (the suite was 184 passing before this).
- **Browser** (a test account with 12 books, one in its preface; no console errors):
  - switching to spines: 12 books on 2 shelves, and the ribbon on the book in progress;
  - dragging a spine along the row: lifted and swinging mid-drag, with the ripple and dip on the drop, and the order saved;
  - a reload keeping spines and the order;
  - Arrange choosing a spine and moving it later;
  - back to covers;
  - a click on a spine opening the book.
  - The covers walk-through passes again.
  - The screenshots showed long titles cut short ("Chess Openi…"), which the type fitting fixed.
- **Not checked:** a real phone.

## Arrange your shelf (done 2026-11-16)

The user asked for books on the shelf to be draggable into a new order, with light physics, "be creative". No new dependencies: `react-native-gesture-handler` 3.3 and Reanimated 4.7 were already installed (Expo's template), and this is the first code to use them.

**Done**
- **Server:**
  - V13 `textbooks.shelf_position`.
  - `GET /textbooks` lists arranged books first, then the rest by volume, so new books go at the end.
  - `PUT /textbooks/order {ids}` (204). Other people's ids are ignored, and a repeated id is a 400. Arranging isn't PIN-locked.
- **`components/Shelf`:** books are absolutely placed in shelf slots, with one shared value holding the order.
  - **Dragging:**
    - A drag starts at once with a mouse; on a phone, after a 220 ms press.
    - The held book lifts (rising, scaling up 6%, with a deeper shadow).
    - It leans with the pointer's speed (up to 10°), swinging back upright on a damped spring when the hand stops.
    - The others glide to their new slots.
  - **A drop:**
    - The book settles with a little overshoot and wobble.
    - The books either side are knocked (a 2.5° lean away and back).
    - That shelf's board dips 2.5 px.
  - **Touch feedback:** a touch presses the book in slightly. On the web, hovering pulls a book out 5 px.
  - **Reduced motion:** books still glide, without the lean, knock or dip.
  - **Not dragging:** *Arrange* shows ‹ › buttons (44 px) under each book. Each cover has *Move earlier / later* accessibility actions.
  - **Saving:** the new order shows at once (`setQueryData`) and is saved. If saving fails, the shelf reloads with a note.
  - **Clicks:** a drag that ends on the web doesn't open the book (a 300 ms guard).
- `GestureHandlerRootView` at the app root. `Cover` takes `onMove`, `footer` and `dimOnPress`.
- **Docs:** the guide (*Arranging your shelf*), release notes 0.10 and CLAUDE.md (gesture notes).

**Verified**
- `./mvnw test`: **184 tests, 0 failures**, including **`ShelfOrderTest` (3)**: the order is kept and new books go at the end; other people's books are untouched; a repeated id is refused.
- `npm run typecheck` and `npx expo lint`: clean.
- **Browser** (exported web app, a test account with 5 books on two shelves; no console errors):
  - dragging the first book onto the fourth's place, with the mid-drag view showing it lifted and the others moved aside;
  - the drop saving the order without opening a book;
  - a reload keeping the order;
  - *Arrange*'s arrow moving a book;
  - a plain click opening a book.
  - A trace of the book's angle tuned the swing: up to 10°, back upright within about a second.
- **Not checked:** a real phone (no device or development build here), so the press-and-hold timing and the page scrolling alongside a drag are untested on touch.

## One account per family (done 2026-11-16)

The user asked for accounts to be for parents (adults) only, with children using the parent's account, locked and limited by a PIN. That replaces M6's child accounts (BRIEF §8: "child profiles can only be created under a PARENT account"). Decisions (asked first):
- one shared shelf and settings per account;
- the PIN locks the settings and the password, and a forgotten PIN is replaced with the password;
- a PIN is suggested, not forced, for child settings;
- no age check at sign-up, only a note that accounts are for adults;
- existing child accounts deleted.

I also told the user that this reduces children's-privacy obligations without removing them. COPPA can still apply to a child-directed service whose children type into it on a parent's account, and the UK Children's Code applies to services children are likely to use. Legal review is still needed.

**Done**
- **V12:**
  - Child accounts are deleted with their attempts, practice, textbooks (lessons, files, chat), email links and profiles; run log rows keep, without the user.
  - PARENT becomes LEARNER, the role check allows only `LEARNER` and `OWNER`, and `users.parent_user_id` is dropped.
- **`auth/PinLock`** (the parent PIN, moved from `Profiles`): `check`, which is throttled, plus `set` (needs the current PIN), `remove` (with the PIN) and `resetWithPassword` (throttled like a sign-in).
- **Profiles:**
  - Your own profile only; anyone else's is a 404.
  - Any age from 5, for any account.
  - A change needs `X-Parent-Pin` once the PIN is set.
  - `ProfileView` has `pinSet`, in place of `managedByParent` and `canEdit`.
  - Gone: the `/profiles/children` endpoints, `addChild`, children's password resets and `requireParent`.
- **Endpoints:** `PUT /profiles/pin`, `DELETE /profiles/pin`, `POST /profiles/pin/reset`. `PUT /auth/password` takes `pin`.
- **Sign-up has no role.** `Allowance` and email verification no longer look at a parent.
- **App:**
  - The title page has no Learner/Parent choice; sign-up says accounts are for adults.
  - **Settings:**
    - one form, ages 5 to Adult, which suggests a PIN for child settings;
    - a *Parent PIN* card: set, change, turn off, and *Forgotten the PIN?* with the account password;
    - *Your password* asks for the PIN when one is set.
- **`scripts/redteam.sh`:** sets up one account for a child of 9.
- **Docs:** the guide (getting started; page 7 is now "Reader settings and the parent PIN", with *Letting a child use your account*), release notes 0.9, the glossary (Account, Parent PIN) and CLAUDE.md.

**Verified**
- `./mvnw test`: **181 tests, 0 failures**.
  - **`ProfilesApiTest`** is rewritten (8): adult defaults and child settings on one account; another's settings 404; the PIN locking the settings; the PIN locking the password; a forgotten PIN reset with the password; turning the lock off; 5 wrong PINs pausing it; the topic gate.
  - **The helper `childReader(age, childSafe)`** replaces `family(...)` in the safety tests.
- `npm run typecheck` and `npx expo lint`: clean.
- **Local database:** V12 ran on it. The 2 child accounts are gone, the 3 parents are now LEARNER, and `parent_user_id` is dropped.
- **Browser** (no console errors):
  - sign-up with no role choice, and the adults note;
  - child-safe on suggesting a PIN;
  - setting the PIN;
  - saving the settings needing the PIN, with a wrong PIN refused;
  - the password needing the PIN;
  - a forgotten PIN reset with the password;
  - the lock turning off.
  - The email walk-through passes again.

## Production fixes (done 2026-11-16)

The user asked for the urgent items from the production-readiness review to be fixed at once. Decisions (asked first): SMTP through Spring Boot's mail starter with Mailpit locally, email verification before any AI use, a sign-up limit rather than a CAPTCHA, and caps of $1 per learner and $25 in total per day.

**Done**
- **The profile race:** `ProfileRepository.forUserOrCreate` locks the user's row (`SELECT … FOR UPDATE`), checks again, then creates, so parallel first requests share one profile. V10 deletes orphaned profiles that nothing points at.
- **The content check is harder to trick:**
  - `Moderation.itemsBlock` numbers items by position, so an id can't carry markup.
  - It escapes `&`, `<` and `>` in the text, so no spelling of a tag in any case can end an item or forge one.
  - Every verdict for an item counts, and a BLOCK beats a PASS. Verdicts for unknown ids are ignored, and an unanswered item is "not checked".
- **Request size:** `web/RequestSizeLimit` caps bodies at 256 KB. A declared length over the cap gets 413 before anything reads it; a streamed body stops at the cap, and `ApiExceptionHandler` turns that into a 413 too.
- **Production defaults (all off):**
  - `/dev` endpoints: `LAA_DEV_ENDPOINTS`.
  - The practice providers: `LAA_PRACTICE_PROVIDERS`. Without it, FAKE and FAKE_B have no client and say why.
  - Dev sign-in: `LAA_DEV_LOGIN`.
  - `LAA_DEFAULT_PROVIDER` has no default. A new database with none set, or with one that can't be used, refuses to start (`RoutingSeeder.firstDefault`) instead of falling back to FAKE.
  - `./dev.sh` turns the local switches on, and the test profile sets its own.
- **`llm/Allowance`**, checked by the gateway before every call (`structuredResult`, `stream`, `runOnce`):
  - a learner's own requests (origin APP) need a verified email (403 `email-unverified`; a child counts through their parent);
  - their spend since midnight UTC must be under `LAA_LEARNER_DAILY_USD` (429 `learner-daily-limit`);
  - all spend must be under `LAA_TOTAL_DAILY_USD` (503 `total-daily-limit`), the owner's Compare included.
  - Spend is the run log's cost estimate, with a new `model_runs (user_id, created_at)` index.
- **Email** (`spring-boot-starter-mail`; `mail/Mailer`):
  - **Sending:** SMTP in the background when `SPRING_MAIL_HOST` is set. Otherwise an in-memory outbox, with a startup warning. Bodies and addresses are never logged.
  - **`auth/EmailLinks`:** 256-bit tokens, stored only as SHA-256 (V11 `email_tokens`). One use; a new link cancels older ones. Verification links last 24 hours, reset links 30 minutes.
  - **`Accounts` flows:**
    - Sign-up sends a verification link; resending needs signing in.
    - A reset request answers 202 the same way for any email.
    - A reset checks the password rules before using the link up, ends older sign-ins, signs in, and marks the email verified.
    - V11 marks existing accounts as verified, and dev sign-in accounts count as verified.
- **`Throttle` additions:** 5 sign-ups per address, 3 emails to one address, and 10 reset requests per address, each per 15 minutes.
- **Mailpit** (`axllent/mailpit:v1.33`) is in `docker-compose.yml`, and `./dev.sh` starts it and points SMTP at it. Read the emails at http://localhost:8025.
- **App:**
  - *Forgotten your password?* on the title page, plus `/forgot-password` and `/reset-password?token=`;
  - `/verify-email?token=`, which runs once and is never refetched;
  - `ConfirmEmail` on the shelf, rechecked on focus, with *Send the link again*;
  - `User.emailVerified`.
- **Docs:**
  - the guide (confirming your email, a forgotten password, the daily allowance, the owner's daily limits, practice mode only on test setups);
  - release notes 0.8;
  - the glossary, CLAUDE.md and `.env.example`.

**Verified**
- `./mvnw test`: **181 tests, 0 failures**.
  - **`EmailLinksTest` (9):** a link verifies once; expiry; a new link replacing the old; a child verified through their parent; a reset (rules first, older sign-ins end, once only); the same answer for an unknown email; 3 emails per address; 10 reset requests per address; 5 sign-ups per address.
  - **`AllowanceTest` (4):** an unverified account blocked until it verifies, with nothing reaching a model; the learner cap; yesterday's spend and Compare not counting toward a learner; the total cap pausing the owner too.
  - **`ProfileRaceTest`:** 10 rounds of 8 parallel first requests, one profile each.
  - **`ModerationItemsTest` (2), `ModerationVerdictsTest` (2), `RequestSizeLimitTest` (3), `FirstDefaultTest` (3).**
- `npm run typecheck` and `npx expo lint`: clean.
- **A server started with only `.env`** (production defaults): `/auth/options` says dev sign-in is off, `/auth/dev-login` is 404, the practice providers aren't configured, an unverified account gets 403 `email-unverified`, and the missing email setup is warned about at startup.
- **Browser, with real SMTP into Mailpit** (no console errors):
  - sign-up, and the *Confirm your email* note;
  - a textbook blocked until confirmed;
  - the emailed link confirming once, then the note gone;
  - *Forgotten your password?*, with the same answer for any email;
  - the emailed reset link setting a new password and signing in;
  - the old password refused.
  - The earlier password walk-through passes again.

**Not done**
- **The Anthropic key in `.env` is still rejected (401), and the OpenAI entry is a placeholder.** Only the user can replace them.
- **No CAPTCHA**, by decision. Universal links (opening email links in the phone app) wait for real app builds and a domain.

## Email and password sign-in (done 2026-11-16)

The user asked for email and password sign-in following best practice, after weighing Google and Apple sign-in (deferred: native Google needs development builds, Apple on the web needs a paid developer account and a domain, and Apple refuses most under-13 child accounts). BRIEF §2 had put a real auth provider out of scope; this is a deliberate change. Every choice below was agreed in a design round (Q1–Q7).

**Done**
- **`auth/Accounts`**, the only place a token is issued: sign-up, sign-in, dev sign-in, a password change (your own, which returns a fresh token) and a reset (a parent's, for a child).
- **`auth/Passwords`:**
  - **Storing:** Argon2id with 19 MiB, 2 iterations, parallelism 1, a 16-byte random salt and a 32-byte hash (OWASP), tagged `{argon2}` through `DelegatingPasswordEncoder`. Hashes made with weaker settings are redone at the next sign-in.
  - **Rules** (NIST SP 800-63B-4, password as the only factor): 15–128 characters, counted as code points after NFKC, which is also applied before hashing, so full-width input matches. No composition rules. Refused: `auth/common-passwords.txt` (the NCSC top 100k, kept to 15+ characters, plus LAA's own name), the account's email, and one character repeated.
  - **Unknown emails:** a sign-in for one is compared against a decoy hash, so it takes as long as a wrong password and gets the same 401 message.
- **`auth/Throttle`** (in memory, Caffeine): 5 failures per email and 20 per network address within 15 minutes, then a 15-minute pause (429), checked before the secret. The parent PIN gets the same treatment (5 failures), which fixes the review's "unlimited PIN guesses".
- **Sign-ins end on a password change:** tokens carry `pv`, the user's `password_version` (V9). A validator on the decoder checks it, caching versions for a minute, and the cache is forgotten on a change.
- **Owners:**
  - `LAA_OWNER_EMAILS` sign up only with `LAA_OWNER_SETUP_CODE`, compared in constant time and limited per address. The error carries `code: owner-setup-code`, so the app shows the field.
  - An OWNER whose email is off the list becomes a LEARNER at their next sign-in. That fixes the review's "owner never revoked", except for tokens already issued.
- **Dev sign-in** is off unless `LAA_DEV_LOGIN=true`. `./dev.sh` and the test profile turn it on, and `GET /auth/options` tells the title page.
- **Children:** a parent creates the child's account with a password (`POST /profiles/children` now needs one) and can reset it (`PUT /profiles/children/{id}/password`, with the PIN once set), which ends the child's sign-ins.
- **`ApiException`** can carry a `code` for the app to act on.
- **App:**
  - **Title page:** sign in, create an account (name, email, password with a live count, Learner or Parent), the owner setup code when asked for, and *Developer sign-in* when it's on.
  - **`PasswordField`** has a Show / Hide switch and the right autocomplete hints.
  - **Settings:** *Your password* (change it, or set one when the account has none), a child's password in their card, and the password in *Add a child*.
- **Docs:** the guide (getting started, parents, owner accounts), release notes 0.7, the glossary, CLAUDE.md, `.env.example`, and `scripts/redteam.sh` (the child now needs a password).

**Verified**
- `./mvnw test`: **158 tests, 0 failures**.
  - **`PasswordSignInTest` (17):** sign-up and sign-in; salted Argon2id; the same answer for a wrong password and an unknown email; each rule; NFKC; the email and address pauses; a success clearing the count; a change ending other sign-ins; setting a first password; upgrading a weak hash; the owner setup code; an owner taken off the list.
  - **`ProfilesApiTest` (+3):** a child signs in with the password their parent chose; a reset with the PIN ends the child's sign-ins; 5 wrong PINs pause the PIN.
- `npm run typecheck` and `npx expo lint`: clean.
- **Browser** (exported web app against a server on :8090), with no console errors:
  - sign-up, with the live count and *Show*;
  - a wrong password, then signing in;
  - changing the password and signing in with the new one;
  - a parent adding a child with a password and resetting it;
  - the child's old password refused and the new one working.

**Not done (agreed)**
- A forgotten-password email: there's no email service.
- Google and Apple sign-in.
- Email verification. Sign-up says when an email is taken, which can't be avoided without sending email.
- Throttle counts are in memory, per server. Behind a proxy, the address is the proxy's.

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
| 2026-11-16 | **The shelf order is the learner's and kept by the server** (`shelf_position`), with arranged books first and new ones at the end | It's the same on every device, and a new book doesn't jump into the middle of an arranged shelf. |
| 2026-11-16 | **Drag uses the already-installed Gesture Handler 3 and Reanimated 4**, with no sound or haptics | No new dependency. Haptics (`expo-haptics`) and a soft "thunk" sound would need approval. |
| 2026-11-16 | **Accounts are for adults; children use a parent's account**, whose reader settings the parent sets for them and locks with the parent PIN. Child accounts are removed. (user decision) | The user wants no child accounts. It also removes child emails and passwords. It doesn't by itself take LAA outside children's-privacy law; see the section above. |
| 2026-11-16 | **The parent PIN locks the reader settings, the password and the PIN**; a forgotten PIN is replaced with the account password; it's suggested, not required, for child settings (user decision) | A child using the account can't loosen their settings or lock the parent out, and the parent can always recover. |
| 2026-11-16 | **No age check at sign-up**, only a note that accounts are for adults (user decision) | A neutral date-of-birth screen was offered and declined. |
| 2026-11-16 | **Email over SMTP** through `spring-boot-starter-mail` (user approved), with **Mailpit** (`axllent/mailpit:v1.33`) for local development | Any provider's SMTP works by changing `.env`, so there's no lock-in. Mailpit shows every email locally without a real account. |
| 2026-11-16 | **A verified email is needed before any AI use** (user decision); children count through their parent; existing and dev sign-in accounts count as verified | Script-made accounts can't spend without an inbox each. Looking around stays open. |
| 2026-11-16 | **Daily caps of $1 per learner and $25 in total**, from the run log's estimates, reset at midnight UTC (user decision); the owner's Compare counts toward the total only | Bounds the bill whatever happens. Set through `LAA_LEARNER_DAILY_USD` and `LAA_TOTAL_DAILY_USD`. |
| 2026-11-16 | **No CAPTCHA**: 5 sign-ups per network address per 15 minutes, plus verification and the caps (user decision) | Avoids a third-party service and a WebView package on phones. |
| 2026-11-16 | **The server's defaults are the production ones**: dev sign-in, `/dev`, practice providers and the default provider are all off or unset, and `./dev.sh` turns the local ones on | A deployment that forgets a setting fails safe or refuses to start, rather than serving practice text or dev tools. |
| 2026-11-16 | **Email and password sign-in**, replacing email-only sign-in as the default (user request; BRIEF §2 had a real auth provider out of scope). Google and Apple sign-in are deferred. | The user wanted real accounts now. Google on phones needs development builds (no Xcode or Android tools here), and Apple on the web needs a paid developer account and an HTTPS domain. |
| 2026-11-16 | Passwords are **Argon2id** (19 MiB, t=2, p=1, 16-byte salt) through Spring Security's `Argon2PasswordEncoder`, adding **BouncyCastle `bcprov-jdk18on` 1.87** (user approved) | OWASP's first choice. bcrypt needed no new dependency, but OWASP lists it for legacy systems and it truncates at 72 bytes. |
| 2026-11-16 | Password rules follow **NIST SP 800-63B-4**: 15–128 characters, no composition rules, NFKC, a blocklist built from the **NCSC top-100k list via SecLists (MIT)**, kept to 15+ characters (user approved) | 15 is NIST's minimum when the password is the only factor. Shorter list entries already fail the length rule. |
| 2026-11-16 | **Guessing limits:** 5 failures per email and 20 per network address per 15 minutes, then a 15-minute pause; the parent PIN gets 5. Kept in memory. | NIST requires rate limiting. A lockout can be triggered by someone else, which is accepted for the prototype. |
| 2026-11-16 | **A password change ends every older sign-in** through a password version in the token, checked against the database with a one-minute cache | Stateless tokens otherwise last 12 hours whatever happens. |
| 2026-11-16 | **Parents create their children's accounts, with a password,** and can reset it with the PIN | Under-13s shouldn't sign themselves up. Creating the account is the parent's permission. A family code to link an existing account is a possible later step. |
| 2026-11-16 | **Owner emails need a setup code to sign up** (`LAA_OWNER_SETUP_CODE`); an OWNER taken off the list is demoted at sign-in. Existing accounts are only promoted through dev sign-in. | Without email verification, whoever signed up first with the owner's email would get the console. |
| 2026-11-16 | **Dev sign-in is off by default** (`LAA_DEV_LOGIN`); `./dev.sh` and the tests turn it on | The security review rated "dev login on by default" high if exposed. |
| 2026-11-09 | Researched "Sign in with ChatGPT" (user request): see `docs/research/sign-in-with-chatgpt.md` | It exists, but only for selected partners via a waitlist, so it's not available to LAA now. Dev sign-in stays, and a real auth provider is out of scope for the prototype (BRIEF §2). |
| 2026-11-16 | **The shelf's motion is firm** (user feedback): critically or near-critically damped springs with `mass: 1`, and a clamped landing. No bounce, wobble or board dip. | The user asked for a solid shelf and snappy, less bouncy books. A clamped spring arrives quickly and stops at its place, which reads as solid. |
| 2026-11-17 | **Production domain `learnanythinganywhere.app`**: the web app at the bare domain, the server at `api.` | The user bought it (plan D1). Email deliverability and an API address that's final before the first store build both need a domain. |
| 2026-11-17 | **Personal Apple and Google developer accounts** | The user's choice (plan D2). Google's 14-day closed test with 12 testers comes before the public release. |
| 2026-11-17 | **Nobody pushes to `main`**: every change arrives by pull request, and the user reviews by merging. A ruleset with no bypass, 0 approvals required; a project hook (`.claude/hooks/protect-main.sh`) stops Claude from pushing to `main`, merging or approving, or changing the branch rules | The user's rule (plan D5, option (a)). GitHub doesn't let anyone approve their own pull request, and Claude pushes with the user's sign-in, so "1 approval" would block every merge. |
| 2026-11-17 | **App identity:** *Learn Anything Anywhere*, `app.learnanythinganywhere` as the iOS bundle ID and Android package | The user's choice (plan D3). The domain reversed. It can't change once published. |
| 2026-11-17 | **Tasks live in GitHub Issues + Projects** | The user's choice: free, next to the code, and Claude reaches it through `gh`. Issues in this public repo are public, so security problems never go in one. |
| 2026-11-17 | **Email: Cloudflare.** Email Sending (SMTP, port 465, `SPRING_MAIL_SSL_ENABLED=true`) for what LAA sends; Email Routing forwards `support@learnanythinganywhere.app` to the user's inbox | The user's choice (plan D8). The domain and its DNS are on Cloudflare, so the records are added automatically. Email Sending is in beta and needs the Workers Paid plan. |
| 2026-11-18 | **Code comments are written for a newcomer** (standard in `CLAUDE.md`; catch-up in issue #56, one area per pull request, in between the Phase 1 steps). **Pull requests are assigned to the user.** | The user: "I still don't see good explainer comments in the code or good javadoc", and wants new developers up to speed quickly. Their own pull requests never appear under *Review requests*. |
| 2026-11-18 | **GPT-6.2 Sol ($2/$10 per 1M tokens) and GPT-6.2 Astra ($8/$40) added**, after the older OpenAI models so the LARGE default stays GPT-6.1 Sol | The user asked for them (issue #58); both are available to their key. Prices and capabilities from OpenAI's model pages on 2026-11-18. They're reasoning models, and LAA sets no reasoning effort, so OpenAI's default applies. |
| 2026-11-18 | **Interactive diagrams: AI-written HTML in a sealed frame**, as their own AI call (task `DIAGRAM`), at most 2 per lesson; **`react-native-webview` 14.0.1** (Expo's supported version) for phones | The user chose the HTML style over diagrams drawn from data. A diagram can't stop a lesson, and every readable word is checked. A frame with no network, no app access and no navigation keeps an AI-written page harmless. |
| 2026-11-22 | **Web beta first:** packaging, the server, DNS, email, AI limits, backups and a privacy page, before CI, the deploy workflow and the stores | The user: "so I can get the web app in people's hands… for beta testing purposes". None of the store work is needed for the web. |
| 2026-11-22 | **Until the deploy workflow exists, images are built on the server** (`deploy.sh build main`) rather than pushed from the laptop | The laptop is ARM and the server x86, so building on the laptop means slow emulation. The public repository needs no credentials to clone. Later the workflow builds and pushes to GitHub's registry, and `deploy.sh <tag>` pulls. |
| 2026-11-24 | **The look is Warm Editorial Minimalism** (`docs/DESIGN.md`), replacing BRIEF §9's rust textbook look. **Source Serif 4** (`@expo-google-fonts/source-serif-4`) replaces Newsreader, and **`react-native-svg` 15.16.0** (Expo SDK 58's version) draws covers, motifs and icons | The owner's design brief and mockups. Both packages were approved before they were added. |
| 2026-11-24 | **The library is a grid of covers on ivory** with no wooden shelves; **arranging and spines stay**. **Each cover's motif is picked by the AI** in the preface's mission draft, from a fixed set of 21, at no extra AI call; until then it's matched from the title | The owner's choices over a fixed grid, keeping the shelf, matching titles only, or an AI-drawn motif per book. The motif is part of the draft's schema (an enum), and the confirm keeps the server's stored pick. |
| 2026-11-24 | **Notes and highlights are in the facelift** (milestone 4), on whole passages; search, bookmarks and reading time are later (#73–#75) | The owner's choices. Phones can't reliably report which words inside a paragraph were selected, so passages work the same everywhere. |
| 2026-11-22 | **Nightly backups go to the owner's Mac for now**, fetched daily by a key that can only fetch backups (forced command, `restrict`), with 7 copies on the server and 30 on the Mac | The owner's choice over a Hetzner Storage Box or Cloudflare R2: free, and enough for a beta. The catch is that copies arrive only while the Mac is on. A storage service can replace the Mac without changing the server's half. |
| 2026-11-22 | **Base images:** `eclipse-temurin:21-jre-noble` (build: `21-jdk-noble`), `node:24-bookworm-slim` (the export only), `caddy:2.11-alpine`, `postgres:18`, each pinned to a major or minor version | Official images, current on 2026-11-22. Pinning to a version line gets security patches when an image is rebuilt, without surprise upgrades. Node 24 matches local development. |
| 2026-11-25 | **The screen's one dominant action is pinned at the bottom and follows its state** (the book page: Start reading, Continue reading or Write lesson *n*; the preface: Send, then Write my textbook) | The brief's "one visually dominant action", placed where the library's mockup puts it. One place for it means it's always in reach on a phone, and never two green buttons at once. |
| 2026-11-25 | **The new-book screen previews the cover** in the cloth of its volume, with the plain ornament until the preface picks the motif. The app's cover colours must match the server's, in order (`CoverColorsTest`) | Books are drawn as physical books from the first moment. The preview never guesses the motif, so it can't promise one the AI then doesn't pick. |
| 2026-11-26 | **No Arrange mode in the library;** the non-drag way to arrange is the book page's ••• menu (*Move earlier / later in your library*), and screen readers keep their actions | The owner found dragging enough and the button purposeless; WCAG 2.5.7 and CLAUDE.md still need a way without dragging, and the mockup's book page already has a •••. |
| 2026-11-26 | **Covers are bound in photographed leather and linen, by colour** (forest and terracotta leather, ochre and blue linen), from ambientCG's CC0 materials, as one grey-and-alpha overlay per material | The owner asked for hyper-realistic linen or leather and chose by colour over all-one-material or learner-chosen. One overlay per material works on every colour without blend modes, which React Native Web lacks. CC0 needs no credit; it's credited in `app/assets/covers/README.md`. |
| 2026-11-26 | **Diagrams get a contrast guard in the sealed frame, and prompt v2 asks for hands-on patterns** | A fix in the frame covers every diagram, old ones included, whatever CSS the model writes; the prompt can only make new ones better. The guard reads and restyles the page only, so the seal is unchanged (`./e2e/run.sh diagrams` passes). |
