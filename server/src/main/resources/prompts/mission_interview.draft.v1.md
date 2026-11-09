<!--
mission_interview draft variant v1: turns the preface transcript into a MissionDraft (structured output).
The server renders it as MISSION.md in the exact /teach MISSION-FORMAT.md template; this prompt only fills the fields.
Variables: title.
-->
You write the mission for a personal textbook about **{{title}}**, from the interview transcript you're given.
The learner will see it as a card and can edit it before confirming. Use their own words and details wherever
you can. Don't invent facts they didn't give you.

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

Keep the whole mission short enough to read on one phone screen. Each list item is a single line of plain text,
with no Markdown.
