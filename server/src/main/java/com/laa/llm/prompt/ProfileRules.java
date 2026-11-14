package com.laa.llm.prompt;

import java.util.ArrayList;
import java.util.List;

import com.laa.llm.GenerationProfile;
import com.laa.safety.Category;
import com.laa.safety.SafetyRules;

/**
 * Renders a learner's generation profile into explicit rules appended to every system prompt
 * (BRIEF section 8). The prompt is only one layer: moderation and link stripping (M6) enforce the
 * same rules on the output regardless of provider.
 */
public final class ProfileRules {

    private ProfileRules() {}

    public static String render(GenerationProfile profile) {
        List<String> rules = new ArrayList<>();
        if (profile.age() == null) {
            rules.add("Reading level: adult. Plain, precise language; define jargon on first use.");
        } else {
            rules.add("Reading level: write for a typical " + profile.age() + "-year-old (short sentences, everyday words).");
        }
        if (profile.childSafe()) {
            rules.add("Child-safe mode is ON: keep all content suitable for children; no graphic, frightening or adult material.");
        }
        List<String> leaveOut = new ArrayList<>();
        for (Category c : SafetyRules.excluded(profile)) leaveOut.add(c.label());
        leaveOut.addAll(SafetyRules.extras(profile));
        if (!leaveOut.isEmpty()) {
            rules.add("Leave out entirely (don't mention, describe or allude to): " + String.join(", ", leaveOut) + ".");
        }
        rules.add(switch (profile.linksPolicy()) {
            case OFF -> "Links: include no URLs or links.";
            case ALLOWLIST -> "Links: only link to resources listed in the learner's RESOURCES.md.";
            case OPEN -> "Links: you may cite links, but never invent a URL.";
        });
        if (profile.level() != null && !profile.level().isBlank()) {
            rules.add("Learner level: " + profile.level() + ".");
        }
        return "## Learner profile rules (always apply)\n- " + String.join("\n- ", rules);
    }
}
