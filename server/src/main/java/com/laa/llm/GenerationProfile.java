package com.laa.llm;

import java.util.Set;

/**
 * The learner's safety and level settings (BRIEF section 8). Rendered into every system prompt
 * by {@link com.laa.llm.prompt.ProfileRules}. Persisted per learner ({@code generation_profiles}, M6);
 * {@link #ADULT_DEFAULT} is what a new adult starts with, and what calls with no learner (owner tools) use.
 */
public record GenerationProfile(
        Integer age, // null = adult
        boolean childSafe,
        Set<String> filters,
        LinksPolicy linksPolicy,
        Integer revealAfterAttempts, // null = never
        String level) {

    public enum LinksPolicy { OFF, ALLOWLIST, OPEN }

    /** Adults start with links to their own sources only; a child's profile starts with links off. */
    public static final GenerationProfile ADULT_DEFAULT =
            new GenerationProfile(null, false, Set.of(), LinksPolicy.ALLOWLIST, null, null);

    public GenerationProfile {
        filters = filters == null ? Set.of() : Set.copyOf(filters);
        linksPolicy = linksPolicy == null ? LinksPolicy.OFF : linksPolicy;
    }
}
