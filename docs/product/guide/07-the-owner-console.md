# 7. The owner console

[← Good to know](06-good-to-know.md) · [Guide home](../README.md)

*For the people who run Learn Anything Anywhere.*

LAA uses AI services to interview learners, write lessons, tutor and check safety. The **owner console** is where you choose which service does each of those jobs, see what they're doing and what they cost, and try a service out before switching to it. Changes take effect on the very next request, for every learner, with no need to restart anything.

## Opening the console

The console works in a **web browser** (on a phone it shows a note asking you to use a computer).

1. Sign in with an owner email address.
2. On your shelf, tap **Owner console** at the top.

Only owner accounts can open it.

## AI services

Each service has a card:

- **Status:** *Default* (the one most jobs use), *Ready*, *Not set up* (it's missing its access key; the card says what's needed) or *Turned off*.
- **Handles *n* tasks:** how many jobs it's doing right now.
- **Its models:** each marked *Large* (best quality) or *Small* (faster and cheaper).

**To change the default service,** pick it in **Default service**, or tap **Make default** on its card. Every job set to *Default* in the routing table moves to it at once.

**To take a service out of use,** tap **Turn off**. LAA won't let you turn off a service that's still doing a job, or make one the default if it isn't ready; it tells you why.

**Practice mode** and **Practice mode B** are stand-in services that need no AI at all, for trying LAA out and testing. See [Practice mode](06-good-to-know.md#practice-mode-text-that-starts-with-fake).

## Routing: who does which job

The routing table lists each job LAA does:

| Job | What it is |
|---|---|
| Mission interview | The preface conversation and the mission card |
| Lesson planning | Choosing what each lesson teaches |
| Lesson writing | Writing each lesson |
| Tutor chat | *Stuck or curious?* conversations |
| Grading practice | Marking practice answers and coaching |
| Learning records | Notes on what learners have shown they can do |
| Safety checks | Checking topics, lessons, replies and what learners type |

For each job you choose:

- **Service:** *Default* (follow the default service) or a specific service. A job set to a specific service stays with it when you change the default.
- **Model:** *Large* (best quality) or *Small* (faster and cheaper), or one particular model.
- **If it fails, try:** backup services, in order. Tap a service to add or remove it; the number shows its place in line. If the main service is down or too slow, LAA quietly tries the next one, and learners don't notice.

**Some choices are refused.** Some jobs need particular abilities. If you pick a model that can't do what a job needs, the change isn't made and the reason appears under the row.

## Compare before switching

Try two services on the same request, side by side, before you switch.

1. Choose the **Task**, and edit the **Request to send** if you like.
2. Choose a service and size for **A** and **B**.
3. Tap **Run comparison**.

Each side shows what came back, how long it took, roughly what it cost, and automatic checks:

- **Right shape:** the result is in the form LAA needs.
- **Passes the lesson rules** (lesson writing): the quizzes are fair and the lesson has every part it should.
- **Reads like it's for age *n*:** a rough guide to how hard the writing is to read.

Happy with one? Tap **Use A for this task** or **Use B for this task** to switch the job to it straight away.

Comparisons are real requests, so a comparison with a paid service costs a little.

## Run log

Every request LAA sends to an AI service, newest first:

- **Status:** *OK*, *FALLBACK* (a backup service answered), *ERROR* (it failed; the reason is shown) or *BLOCKED* (held back by a safety check).
- **The job and service**, when it happened, how long it took, how much text went in and out, and roughly what it cost. Requests from comparisons are marked *compare*.

Filter by **Task** or **Service**, and tap **Refresh** to see the latest. The total at the top is a rough cost for the requests shown.
