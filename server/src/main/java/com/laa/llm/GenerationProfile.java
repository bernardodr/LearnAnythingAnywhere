package com.laa.llm;

import java.util.Set;

/**
 * The learner's safety and level settings (BRIEF section 8). Rendered into every system prompt
 * by {@link com.laa.llm.prompt.ProfileRules}. Persisted per learner from M6; until then callers
 * pass {@link #ADULT_DEFAULT}.
 */
public record GenerationProfile(
        Integer age, // null = adult
        boolean childSafe,
        Set<String> filters,
        LinksPolicy linksPolicy,
        Integer revealAfterAttempts, // null = never
        String level) {

    public enum LinksPolicy { OFF, ALLOWLIST, OPEN }

    public static final GenerationProfile ADULT_DEFAULT =
            new GenerationProfile(null, false, Set.of(), LinksPolicy.OFF, null, null);

    public GenerationProfile {
        filters = filters == null ? Set.of() : Set.copyOf(filters);
        linksPolicy = linksPolicy == null ? LinksPolicy.OFF : linksPolicy;
    }
}
