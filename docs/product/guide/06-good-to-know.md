# 6. Good to know

[← Practice and your tutor](05-practice-and-your-tutor.md) · [Guide home](../README.md) · Next: [Reader settings and the parent PIN →](07-reader-settings.md)

## Practice mode: text that starts with "[FAKE]"

LAA can run in **practice mode**, without a real AI service. It's used for trying LAA out and for testing, and only on test setups: a real copy of LAA never uses it. In practice mode, everything still works (interviews, lessons, quizzes, the tutor), but the writing is a simple stand-in rather than real teaching, and much of it starts with **[FAKE]**.

If you see [FAKE], you're in practice mode. Whoever runs your copy of LAA can switch it to a real AI service in the [owner console](08-the-owner-console.md).

## When something goes wrong

LAA is careful never to lose your work. If something fails, you'll see a short message saying what happened. Usually you just try again:

| You see | What to do |
|---|---|
| *"…couldn't reply just now. Your answer wasn't lost: send it again."* | Tap **Send** again. Your answer is still in the box. |
| *"We couldn't check this topic just now, so nothing was created."* | Wait a moment, then try your topic again. |
| *"The lesson didn't pass its checks, so it wasn't saved."* | Tap **Write lesson** again on your book's page. |
| *"No sources could be checked for this lesson."* | LAA couldn't find pages it could check this time, so it showed none rather than guess. Add sources you trust at the back of the book, and later lessons can cite them. |
| *"Your tutor couldn't read your answer just now. Nothing was lost."* | Tap **Send to your tutor** again. |
| *"We couldn't run the safety check just now, so nothing was saved or sent."* | Wait a moment, then try again. LAA never skips its safety check. |
| *"Let's keep this about learning, in a way that fits your reader settings."* | Put your message another way. Nothing was saved. |
| *"This lesson didn't fit your reader settings, so it wasn't saved."* | Tap **Write lesson** again on your book's page. |
| *"This part was left out because it didn't fit your reader settings."* | Nothing to do: part of a lesson or a reply was held back. See [How LAA keeps things safe](07-reader-settings.md#how-laa-keeps-things-safe). |
| *"Confirm your email address first…"* | Open the link in the email LAA sent you, or tap **Send the link again** in your library. See [Creating your account](01-getting-started.md#creating-your-account). |
| *"You've used today's AI allowance."* | Come back tomorrow: each learner has a daily allowance for AI, which starts again at midnight UTC. See [Daily allowance](#daily-allowance). |
| *"LAA has reached today's limit for AI use…"* | Come back tomorrow: everything that uses AI pauses for everyone until midnight UTC. Nothing was saved. |
| *"That request is too large."* | Make what you typed shorter. |
| *"Can't reach the server."* | Check your internet connection, then try again. |

Nothing half-finished is ever saved: an answer is kept together with the reply to it, and a lesson is saved only once it's complete and checked.

## Daily allowance

AI costs money each time it writes, so LAA has two daily limits, set by the people who run it:

- **Each learner** has a daily allowance for AI: about five lessons a day on the largest AI models, and far more on smaller ones. Reading what's already written, and your library, never count.
- **All of LAA together** has a daily limit too. If it's reached, everything that uses AI pauses for everyone until the next day.

Both start again at **midnight UTC**. The allowance is for the account, so a child using a parent's account shares it.

## Coming in future releases

- **Signing in with Google or Apple.**

See the [release notes](../release-notes.md) for what each release added.
