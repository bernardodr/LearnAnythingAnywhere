package com.laa.textbook;

/**
 * How familiar the learner already is with a book's subject, chosen on a slider when they start it (issue #83; the
 * owner: "So we can skip the easy parts or focus on the basics"). Stored as its level (1 to 5) on the book, and given
 * to the AI for the preface, the mission and every lesson's plan and writing (the {@code familiarity} prompt
 * variable), so lessons start where the learner is. The app has the same five levels, with their own playful words.
 */
public enum Familiarity {
    NEW(1, "Brand new", "They're brand new to it: they've never really studied it. Start from first principles: "
            + "define every term, use everyday examples, and assume no background at all."),
    A_LITTLE(2, "Know a little", "They know a little: they've picked up a few ideas, but nothing organised. Cover the "
            + "foundations briefly, then build steadily; don't dwell on what they've clearly met already."),
    THE_BASICS(3, "Know the basics", "They know the basics: the core ideas and words are familiar. Don't introduce "
            + "basic terms or ideas; build on them and go deeper."),
    COMFORTABLE(4, "Comfortable", "They're comfortable with it: they've practised and know it fairly well. Skip the "
            + "basics entirely; focus on nuance, technique, the mistakes intermediate learners make, and why things work."),
    CONFIDENT(5, "Very confident", "They're very confident: they know it well. Never explain the basics; go straight to "
            + "advanced, specialist material, and make the practice genuinely challenging.");

    private final int level;
    private final String label;
    private final String forPrompt;

    Familiarity(int level, String label, String forPrompt) {
        this.level = level;
        this.label = label;
        this.forPrompt = forPrompt;
    }

    /** 1 (brand new) to 5 (very confident), as stored and as the app sends it. */
    public int level() {
        return level;
    }

    public String label() {
        return label;
    }

    /** The level for a stored or requested number; null for null (a book from before the slider, or none chosen). */
    public static Familiarity of(Integer level) {
        if (level == null) return null;
        for (Familiarity f : values()) if (f.level == level) return f;
        throw new IllegalArgumentException("Familiarity is 1 to 5, not " + level);
    }

    /** The {@code familiarity} prompt variable: what the learner said, and how to pitch the teaching to it. */
    public static String forPrompt(Familiarity f) {
        return f == null
                ? "Not stated (this book was started before LAA asked). Judge it from the mission and the learner's answers."
                : f.label + ". " + f.forPrompt;
    }
}
