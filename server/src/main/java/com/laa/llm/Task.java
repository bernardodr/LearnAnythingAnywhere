package com.laa.llm;

/**
 * Every model call names one of these. Routing, prompts and capability checks are all per task.
 * {@link #needs()} is what a model must support to be routed this task (BRIEF section 5.3).
 */
public enum Task {
    /** Variant "draft": the structured mission draft that ends the interview. */
    MISSION_INTERVIEW(new Needs(true, false, true, 16_000), ModelTier.LARGE, "draft"),
    RESEARCH(new Needs(false, true, false, 32_000), ModelTier.LARGE),
    LESSON_PLAN(new Needs(true, false, false, 32_000), ModelTier.LARGE),
    LESSON_WRITE(new Needs(true, false, false, 32_000), ModelTier.LARGE),
    TUTOR(new Needs(false, false, true, 16_000), ModelTier.SMALL),
    GRADE(new Needs(true, false, false, 8_000), ModelTier.SMALL),
    RECORD_UPDATE(new Needs(true, false, false, 16_000), ModelTier.SMALL),
    /** Variant "topic": the topic gate at textbook creation (BRIEF section 8). */
    SAFETY_CHECK(new Needs(true, false, false, 8_000), ModelTier.SMALL, "topic");

    public record Needs(boolean structuredOutput, boolean toolCalling, boolean streaming, int minContext) {}

    private final Needs needs;
    private final ModelTier defaultTier;
    private final java.util.Set<String> promptVariants;

    Task(Needs needs, ModelTier defaultTier, String... promptVariants) {
        this.needs = needs;
        this.defaultTier = defaultTier;
        this.promptVariants = java.util.Set.of(promptVariants);
    }

    /**
     * Extra prompt files a version of this task must ship besides the main one
     * ({@code prompts/<stem>.<variant>.<version>.md}), so a route can't be moved to a version missing one.
     */
    public java.util.Set<String> promptVariants() {
        return promptVariants;
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
