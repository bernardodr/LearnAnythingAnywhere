package com.laa.llm;

/**
 * The unit of routing: the owner routes each task to a provider and model, and the run log meters per task. The calls
 * made for a task (its prompt variants and what they return) are in {@link Calls}. {@link #needs()} is what a model
 * must support to be routed this task (BRIEF section 5.3); {@link CallsCheck} keeps it in step with the task's calls.
 */
public enum Task {
    MISSION_INTERVIEW(new Needs(true, false, true, false, 16_000), ModelTier.LARGE),
    /** Finding a lesson's sources by searching the web (issue #82): the model must be able to search. */
    RESEARCH(new Needs(true, false, false, true, 32_000), ModelTier.LARGE),
    LESSON_PLAN(new Needs(true, false, false, false, 32_000), ModelTier.LARGE),
    LESSON_WRITE(new Needs(true, false, false, false, 32_000), ModelTier.LARGE),
    /** An interactive diagram for a lesson: a small self-contained HTML page, written after the lesson passes its checks. */
    DIAGRAM(new Needs(true, false, false, false, 32_000), ModelTier.LARGE),
    TUTOR(new Needs(false, false, true, false, 16_000), ModelTier.SMALL),
    GRADE(new Needs(true, false, false, false, 8_000), ModelTier.SMALL),
    RECORD_UPDATE(new Needs(true, false, false, false, 16_000), ModelTier.SMALL),
    SAFETY_CHECK(new Needs(true, false, false, false, 8_000), ModelTier.SMALL);

    /** {@code webSearch}: the model must search the web itself (its provider's own search tool), not answer from memory. */
    public record Needs(boolean structuredOutput, boolean toolCalling, boolean streaming, boolean webSearch, int minContext) {}

    private final Needs needs;
    private final ModelTier defaultTier;

    Task(Needs needs, ModelTier defaultTier) {
        this.needs = needs;
        this.defaultTier = defaultTier;
    }

    public Needs needs() {
        return needs;
    }

    public ModelTier defaultTier() {
        return defaultTier;
    }

    /** Prompt file stem: {@code prompts/<stem>.<version>.md}. */
    public String promptStem() {
        return name().toLowerCase();
    }
}
