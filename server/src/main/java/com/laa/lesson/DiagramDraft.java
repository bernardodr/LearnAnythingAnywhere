package com.laa.lesson;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * What the diagram writer returns for one interactive diagram in a lesson ({@code Calls.DIAGRAM}).
 *
 * <p>A lesson's writer marks where a picture would help with a DIAGRAM block, which holds a brief (what to show and
 * how the learner explores it) and a caption. After the lesson passes its checks, {@link LessonWriter} asks the
 * diagram writer for each brief, and this record is the answer. It is not shown as it is: {@link DiagramRules} checks
 * the page and {@link LessonChecks#withDiagrams} runs the safety check on its words before it becomes part of the
 * stored lesson. A diagram that fails is left out, and the rest of the lesson is still saved.
 *
 * <p>The app shows {@code html} in a sealed frame: no network, no access to the app, no navigation. So the page must
 * be self-contained, with its CSS and JavaScript inline.
 *
 * @param title       a few words naming the diagram, shown above it (and read out by screen readers)
 * @param html        the page's body: markup plus inline {@code <style>} and {@code <script>}, no {@code <html>} or
 *                    {@code <head>}. The app adds the document around it, LAA's colours and fonts, and the rules that
 *                    seal it.
 * @param textVersion the same explanation in plain Markdown, for screen readers, for "Show as text", and as the
 *                    fallback if the page can't run on a device
 */
public record DiagramDraft(
        @NotBlank @Size(max = 90) String title,
        @NotBlank @Size(max = DiagramRules.MAX_HTML) String html,
        @NotBlank @Size(max = 3000) String textVersion) {}
