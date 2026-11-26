# Learn Anything Anywhere

LAA turns a learner's *why* into a personal textbook, written lesson by lesson by AI services and kept safe for the reader by LAA itself.

## AI services

**Task**:
A job LAA gives an AI service, such as lesson writing or the safety checks. The owner chooses which service and model handles each task.
_Avoid_: job type, route (a route is the owner's choice for a task)

**Call**:
One kind of request LAA makes for a task: which prompt it sends and what it expects back. A task can have several, as the safety checks do (topic gate, content check, leak check), and they always share the task's route.
_Avoid_: variant (only the prompt part of it), request type

**Daily allowance**:
How much AI one learner can use in a day, measured by its estimated cost and reset at midnight UTC. All of LAA together has a daily limit too.
_Avoid_: quota, credits, budget

## Accounts

**Account**:
How a person signs in to LAA: their email and a password. Accounts are for adults. A child uses a parent's account, with reader settings the parent chooses for them; children don't have accounts of their own. An owner's account also runs LAA.
_Avoid_: login, profile (that's reader settings), child account (there's no such thing)

**Parent PIN**:
A short number code an account holder can set so a child using their account can't change its reader settings, its password or the PIN. Everything else stays open to the child.
_Avoid_: lock code, password (that's for signing in)

**Dev sign-in**:
Signing in with an email alone, with no password, for building and testing LAA. It's off unless someone running LAA turns it on.
_Avoid_: dev login (the address it answers on), guest sign-in

**Owner setup code**:
The secret, chosen by whoever sets up LAA's server, that making an owner account needs.

**Confirmed email**:
An account whose holder has opened the link LAA emailed them, proving the address is theirs. Using AI waits for it.
_Avoid_: verified (fine in code; the guide says confirmed), activated

## Books

**Library**:
Where a learner's textbooks are: their covers (or spines), in the order the learner arranged them.
_Avoid_: shelf (the old name; the code's `Shelf` component and `shelf_position` column still use it), bookcase, dashboard

**Book page**:
A book's own page, set out like the front of a printed book: its cover, title and goal, the contents (its lessons, in order), the why of its mission, and the back of the book.
_Avoid_: contents page (only the list of lessons on it), book details, dashboard

**Reading ribbon**:
The bookmark ribbon on the book a learner read last (the one whose lesson they opened most recently), with "Continue reading" under it. Only one book wears it at a time.
_Avoid_: bookmark (saving a place in a lesson is a different thing, still to come), current book

**Cover motif**:
The one small drawing of a book's subject on its cover (stars, a leaf, a mountain…), from a fixed hand-drawn set. The AI chooses it when the preface ends.
_Avoid_: icon, illustration, cover art, logo

## Lessons

**Source**:
A real page, book or video a learner can trust for their subject: one LAA found for a lesson by searching the web and checked, or one the learner added. Never made up. Each lesson lists its sources at its end; the book collects them all at the back.
_Avoid_: reference (fine in prose, but "source" in the app), citation (the small number in a lesson pointing to a source), link

**Interactive diagram**:
A picture in a lesson that the learner explores (tapping parts, stepping through, comparing, sliding), drawn by an AI service for that lesson. It always comes with a text version that says the same thing, and it's left out rather than shown if it doesn't pass its checks.
_Avoid_: widget, figure, visual, applet

**Diagram brief**:
What a lesson's writer asks a diagram to show and how the learner explores it. It goes to the AI that draws the diagram, never to the learner.
_Avoid_: diagram prompt

**Sealed frame**:
Where an interactive diagram runs: it can't go online, open links, or reach the app, the learner's account or the device.
_Avoid_: sandbox (only one part of it), iframe, web view

## Safety

**Safety layer**:
LAA's own part that stands between learners and the AI services: everything a learner types, and everything written for them to read, passes through it. It holds whichever AI service is in use.
_Avoid_: moderation (one of its checks, not the whole), filter, guard

**Reader settings**:
What an account's textbooks may contain and how they're pitched: age and reading level, child-safe mode, what's left out, links, and whether answers can be shown. When a child uses a parent's account, the parent sets them for the child and can lock them with the parent PIN.
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
