<!--
mission_interview draft variant v3 (issue #83): v2 plus where the learner says they're starting.
mission_interview draft variant v2: turns the preface transcript into a MissionDraft (structured output).
The server renders it as MISSION.md in the exact /teach MISSION-FORMAT.md template; this prompt only fills the fields.
v2 adds `motif`, the cover's subject drawing (docs/DESIGN.md "Books"; the set is com.laa.textbook.Motif).
Variables: title, familiarity.
-->
You write the mission for a personal textbook about **{{title}}**, from the interview transcript you're given.
The learner will see it as a card and can edit it before confirming. Use their own words and details wherever
you can. Don't invent facts they didn't give you.

## Where they're starting
When they started this book, they told us how familiar they already are:

{{familiarity}}

Let it shape the mission: someone confident gets goals beyond the basics; someone new gets first steps they can
reach. If the transcript says otherwise, the transcript wins.

Fill these fields:
- **headline**: one short line (at most 12 words) naming the goal in the learner's terms, e.g. "Climb a V3 with
  my friends by spring". No full stop.
- **why**: one to three sentences on the concrete real-world outcome they're chasing: what changes in their life
  or work when they have this skill. Write it in the second person ("You want to…"). Avoid abstract framings like
  "to understand X"; state the outcome underneath.
- **successLooksLike**: one to five specific, observable things they'll be able to do. Each starts with a verb.
- **constraints**: time, budget, equipment, commitments or learning preferences they mentioned. Leave the list
  empty if they mentioned none; don't guess.
- **outOfScope**: adjacent topics they said they don't want right now. Leave the list empty if none came up.
- **motif**: the small drawing on the book's cover. Pick the one that best fits the subject as the learner sees it:
  STARS (astronomy, the night sky), MOON (night, sleep, dreams), LEAF (plants, gardens, biology, nature),
  WHEAT (cooking, baking, food, farming), MOUNTAIN (climbing, hiking, geography, the outdoors), WAVE (the sea,
  water, swimming, sailing), GEAR (engineering, machines, repair, electronics), DOTS (computing, programming, data),
  SHAPES (art, design, architecture, crafts), BRUSH (painting, drawing), NOTE (music), FLASK (chemistry,
  experiments), ATOM (physics, science), GLOBE (travel, the world, cultures), QUILL (writing, literature),
  COLUMN (history, philosophy, law, politics), BARS (money, business, economics, maths, statistics),
  PULSE (health, fitness, medicine, sport), PAWN (chess, games, strategy), SPEECH (languages, speaking,
  communication). Use ORNAMENT only when none of them fits.

Keep the whole mission short enough to read on one phone screen. Each list item is a single line of plain text,
with no Markdown.
