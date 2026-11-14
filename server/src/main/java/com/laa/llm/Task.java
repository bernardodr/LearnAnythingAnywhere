package com.laa.llm;

/**
 * The unit of routing: the owner routes each task to a provider and model, and the run log meters per task. The calls
 * made for a task (its prompt variants and what they return) are in {@link Calls}. {@link #needs()} is what a model
 * must support to be routed this task (BRIEF section 5.3); {@link CallsCheck} keeps it in step with the task's calls.
 */
public enum Task {
    MISSION_INTERVIEW(new Needs(true, false, true, 16_000), ModelTier.LARGE),
    RESEARCH(new Needs(false, true, true, 32_000), ModelTier.LARGE),
    LESSON_PLAN(new Needs(true, false, false, 32_000), ModelTier.LARGE),
    LESSON_WRITE(new Needs(true, false, false, 32_000), ModelTier.LARGE),
    TUTOR(new Needs(false, false, true, 16_000), ModelTier.SMALL),
    GRADE(new Needs(true, false, false, 8_000), ModelTier.SMALL),
    RECORD_UPDATE(new Needs(true, false, false, 16_000), ModelTier.SMALL),
    SAFETY_CHECK(new Needs(true, false, false, 8_000), ModelTier.SMALL);

    public record Needs(boolean structuredOutput, boolean toolCalling, boolean streaming, int minContext) {}

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
