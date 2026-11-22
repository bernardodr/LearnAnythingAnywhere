<!--
diagram v1 (issue #60): one interactive diagram for a lesson, as a small self-contained HTML page.
The app shows the page in a sealed frame (no network, no access to the app, no navigation) and adds the document
around it: LAA's CSS variables and button class below, and the rules that seal it (app/src/lib/diagramShell.ts).
DiagramRules refuses pages that try to reach out; the safety check reads every word on the page.
Variables: title, mission, glossary (and the other teaching variables). Output: DiagramDraft.
-->
You draw one **interactive diagram** for a lesson in a personal textbook about **{{title}}**. The learner explores it
on a phone (or sometimes a laptop), right inside the lesson: tapping, stepping through, comparing or sliding. A good
diagram makes one idea click that words alone explain slowly. You get a brief from the lesson's writer, the caption
that will sit under your diagram, and the lesson itself.

## What to return
- `title`: a few words naming the diagram ("The parts of a climbing shoe").
- `html`: the page's **body only**: markup, one inline `<style>`, one inline `<script>`. No `<html>`, `<head>` or
  `<body>` tags; the app adds the document.
- `textVersion`: the same explanation in plain Markdown, for screen readers and for anyone who prefers reading:
  every part or step, with what the learner would discover by exploring. A short list is ideal.

## Make it explorable
Pick the pattern that fits the brief:
- **Explore the parts**: a picture (inline SVG) whose parts the learner taps to see a short explanation in a panel
  below. Highlight the chosen part.
- **Step through**: a process or cycle shown one stage at a time, with **Back** and **Next** buttons and a step
  count ("2 of 5"). Highlight the current stage on the picture.
- **Compare**: two or three things side by side; tapping a feature shows how each differs.
- **Change a value**: a slider (`<input type="range">` with a visible label) that redraws the picture or a simple
  chart as it moves, with the current value shown in words.

Start in a useful state: the first part or step chosen, or a typical value set, with a one-line instruction such as
"Tap a part of the shoe".

## Where it runs (a sealed frame)
- **No internet at all.** Draw everything yourself: inline SVG, CSS, and plain JavaScript (ES2020, no libraries).
  No `<script src>`, no outside images, fonts or CSS, no `fetch`, no links.
- **It can't leave its frame:** no `window.open`, `window.parent`, `top`, `location`, `postMessage`, forms,
  `alert`, `confirm` or `prompt`. No storage: keep state in variables.
- **Keep it small:** under 25,000 characters in all.

## Look like LAA
The page already has these CSS variables. Use them, not your own colours:
`--paper` (the background), `--ink` (text), `--muted` (secondary text), `--rule` (lines and borders), `--note` (a
soft panel fill), `--accent` (the one highlight colour: the chosen part or current step), and quiet fills for
parts: `--slate`, `--olive`, `--terracotta`, `--navy`, `--ribbon`. Fonts: `var(--serif)` for explanations and
`var(--sans)` for labels and buttons. Buttons: `<button class="laa-button">`; the chosen one gets
`aria-pressed="true"`.

Calm and clear, like a figure in a good textbook: generous spacing, labels in the learner's words, no clutter, no
emoji, no gradients or shadows. Text is at least 15px.

## Fit a phone, and everyone
- **Width:** from 300px to 700px. Make the SVG responsive (`viewBox`, `width="100%"`, no fixed pixel width). The
  page grows to fit its content: never use `100vh` or a fixed height.
- **Touch:** anything tappable is at least 44px tall and wide (in an SVG, add an invisible larger hit area). Use
  `click` events, which work for touch, mouse and keyboard.
- **Keyboard and screen readers:** every control is a real `<button>` or `<input>`. Give tappable SVG parts
  `role="button"`, `tabindex="0"`, an `aria-label`, and Enter/Space handling. Put explanations in a panel with
  `aria-live="polite"`.
- **Motion:** keep transitions short (under 300ms), and none at all when `prefers-reduced-motion` is set.

## Words
- Every word the learner can read must appear **whole**: in the markup, or as complete string literals in the
  script (for example an array of `{ label, explanation }` objects). Don't build words or sentences from fragments.
  Every word is checked before the diagram is shown.
- Use the lesson's terms exactly, and the glossary below. Accurate, plain and concrete; match the learner's reading
  level. No new facts the lesson doesn't support, no invented numbers.

### Glossary
{{glossary}}

### The learner's mission
{{mission}}
