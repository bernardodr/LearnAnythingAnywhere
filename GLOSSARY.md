# Learn Anything Anywhere

LAA turns a learner's *why* into a personal textbook, written lesson by lesson by AI services and kept safe for the reader by LAA itself.

## AI services

**Task**:
A job LAA gives an AI service, such as lesson writing or the safety checks. The owner chooses which service and model handles each task.
_Avoid_: job type, route (a route is the owner's choice for a task)

**Call**:
One kind of request LAA makes for a task: which prompt it sends and what it expects back. A task can have several, as the safety checks do (topic gate, content check, leak check), and they always share the task's route.
_Avoid_: variant (only the prompt part of it), request type

## Accounts

**Account**:
How a person signs in to LAA: their email, a password, and whether they're a learner, a parent or an owner. A parent makes their child's account, which then sits under the parent's.
_Avoid_: login, profile (that's reader settings)

**Dev sign-in**:
Signing in with an email alone, with no password, for building and testing LAA. It's off unless someone running LAA turns it on.
_Avoid_: dev login (the address it answers on), guest sign-in

**Owner setup code**:
The secret, chosen by whoever sets up LAA's server, that making an owner account needs.

## Safety

**Safety layer**:
LAA's own part that stands between learners and the AI services: everything a learner types, and everything written for them to read, passes through it. It holds whichever AI service is in use.
_Avoid_: moderation (one of its checks, not the whole), filter, guard

**Reader settings**:
What a learner's textbooks may contain and how they're pitched: age and reading level, child-safe mode, what's left out, links, and whether answers can be shown. A child's are managed by their parent.
_Avoid_: profile, generation profile (the brief's term for the same thing), preferences

**Shown text**:
Text an AI service wrote that a learner will see. The safety layer checks all of it; text a learner never sees (a lesson plan, a leak verdict) isn't shown text.
_Avoid_: output, content

**Held back**:
Shown text that didn't fit the reader settings, so the learner never sees it: they get a placeholder or a prepared hint instead, and it's logged.
_Avoid_: blocked (the run log's word for it), censored, filtered

**Placeholder**:
The note a learner sees where something was held back: "This part was left out because it didn't fit your reader settings."

**Links policy**:
The reader setting for links: off, only links to sources the learner has added in any of their textbooks (allowlist), or open. It applies to everything they read, as soon as it changes.

**Reader view**:
What a learner may see of any text LAA has stored, under their reader settings as they are now.
_Avoid_: display filter

**Topic gate**:
The check a topic passes before a textbook is created: it goes ahead, is approached in a different way, or is declined kindly.

**Leak guard**:
The check that a tutor's note doesn't give away the answer to a practice question before the learner has found it.
