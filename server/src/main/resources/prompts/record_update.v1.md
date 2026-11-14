<!--
record_update v1 (BRIEF 6.5). Ported from /teach LEARNING-RECORD-FORMAT.md ("when to write a learning record",
"what does not qualify") and GLOSSARY-FORMAT.md ("add a term only when the user understands it").
Runs once, after the learner's answer met every rubric point. The server writes the record and glossary files.
Variables: title, mission, glossary, lesson_title, skill, prompt, rubric, existing_records. Output: RecordUpdate.
-->
The learner just answered a practice question in their textbook about **{{title}}** (lesson "{{lesson_title}}")
and met every point of the rubric. Decide what this answer is *evidence* of, for future lessons.

## The practice question
{{prompt}}

## What a full answer covers
{{rubric}}

The skill this lesson builds: {{skill}}

## Their mission
{{mission}}

## Learning records so far
{{existing_records}}

## Current glossary
{{glossary}}

## Learning record
Learning records capture non-obvious things the learner has *demonstrated*, so future lessons can be pitched in
their zone of proximal development. They're decision-grade insights, not a journal.
- Set `writeRecord` to true when the answer shows genuine understanding of something non-trivial: they used the
  idea correctly, in their own words. Set it to false if the answer only repeats the lesson back without showing
  they can use it, or if an existing record already says the same thing.
- `title`: a short statement of what's now known, e.g. "Uses straight arms to delay forearm pump".
- `slug`: the title in a few dash-case words, e.g. "straight-arms-delay-pump".
- `body`: one to three sentences: what they've shown they understand, and what that means for what to teach next.
- `evidence`: one sentence on how the answer showed it, quoting a few of their words if useful.
When `writeRecord` is false, set title, slug, body and evidence to empty strings.

## Glossary
Add a term only when the learner **used it correctly** in this answer. The glossary records compressed knowledge,
not a dictionary to learn from. For each term: a tight definition of what it *is* (one or two sentences), and
`avoid`: looser words for the same thing that the glossary should steer away from (may be empty). Use the
existing glossary's wording if the term is already there. Most answers add zero to two terms; an empty list is fine.
