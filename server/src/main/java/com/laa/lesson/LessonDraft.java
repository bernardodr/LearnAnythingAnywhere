package com.laa.lesson;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * LESSON_WRITE output: the model-facing wire format. Every block has the same eight fields, because strict
 * structured-output modes require every property; the prompt says what each field means per block type.
 * {@link LessonRules} validates it, enforces the quiz rules and converts it into the client lesson JSON of
 * BRIEF section 7. Rubrics (and free-response hints) stay on the server.
 */
public record LessonDraft(
        @NotBlank @Size(max = 90) String title,
        @NotBlank @Size(max = 240) String subtitle,
        @NotBlank @Size(max = 320) String win,
        @NotNull @Size(min = 5, max = 24) List<@NotNull @Valid Block> blocks,
        @NotNull @Valid ReferenceSheet referenceSheet,
        @NotNull @Size(max = 6) List<@NotNull @Valid Rubric> rubrics) {

    public enum Type { HEADING, TEXT, KEY_IDEA, CALLOUT, QUIZ, STEPS, FREE_RESPONSE, PRIMARY_SOURCE, ASK_TUTOR, DIAGRAM }

    /**
     * {@code style}: quiz {@code warmup|check}, callout {@code win|note|watch_out}, otherwise empty.
     * {@code text}: heading text, Markdown body, key idea, callout text, quiz question, steps title,
     * free-response prompt, primary-source note, or a diagram's brief (what it shows and how the learner explores it;
     * the brief goes to the diagram writer, never to the learner). {@code items}: quiz options, steps, free-response
     * hints, or ask-tutor example questions. {@code answer}: the quiz's correct option index, otherwise -1.
     * {@code explanation}: why a quiz's answer is right, or a diagram's caption.
     * {@code refs}: citation ids for text blocks, or the one resource id of a primary source.
     */
    public record Block(
            @NotBlank @Size(max = 12) String id,
            @NotNull Type type,
            @NotNull @Size(max = 20) String style,
            @NotNull @Size(max = 4000) String text,
            @NotNull @Size(max = 10) List<@NotNull @Size(max = 400) String> items,
            @NotNull Integer answer,
            @NotNull @Size(max = 800) String explanation,
            @NotNull @Size(max = 6) List<@NotNull @Size(max = 12) String> refs) {}

    /** The cheat sheet for this lesson: the compressed essence, for quick reference later. */
    public record ReferenceSheet(@NotBlank @Size(max = 90) String title, @NotBlank @Size(max = 6000) String md) {}

    /** Hidden marking guide for one free-response block. Never sent to the client. */
    public record Rubric(
            @NotBlank @Size(max = 12) String blockId,
            @NotNull @Size(min = 1, max = 6) List<@NotBlank @Size(max = 300) String> points,
            @NotBlank @Size(max = 1200) String answerOutline) {}
}
