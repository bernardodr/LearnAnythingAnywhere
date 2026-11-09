package com.laa.mission;

import java.util.List;

/**
 * Renders MISSION.md in the exact template of /teach's MISSION-FORMAT.md:
 * {@code # Mission: {Topic}}, then {@code ## Why}, {@code ## Success looks like}, {@code ## Constraints} and
 * {@code ## Out of scope}, each list as {@code - } bullets. Line breaks inside a field are folded so the
 * structure can't be broken by what the learner typed.
 */
public final class MissionMarkdown {

    /** /teach expects a bullet under every list heading; this stands in when a list is empty. */
    static final String NONE = "None stated yet";

    private MissionMarkdown() {}

    public static String render(String topic, MissionDraft d) {
        StringBuilder md = new StringBuilder();
        md.append("# Mission: ").append(line(topic)).append("\n\n");
        md.append("## Why\n").append(line(d.why())).append("\n\n");
        md.append("## Success looks like\n").append(bullets(d.successLooksLike())).append("\n");
        md.append("## Constraints\n").append(bullets(d.constraints())).append("\n");
        md.append("## Out of scope\n").append(bullets(d.outOfScope()));
        return md.toString();
    }

    private static String bullets(List<String> items) {
        List<String> clean = items == null ? List.of() : items.stream().map(MissionMarkdown::line).filter(s -> !s.isEmpty()).toList();
        if (clean.isEmpty()) clean = List.of(NONE);
        StringBuilder out = new StringBuilder();
        for (String item : clean) out.append("- ").append(item).append('\n');
        return out.toString();
    }

    /** One line of plain text: whitespace folded, and no leading characters that would start new Markdown structure. */
    static String line(String s) {
        if (s == null) return "";
        String t = s.replaceAll("\\s+", " ").strip();
        return t.replaceFirst("^(#+|[-*+]|>)\\s*", "").strip();
    }
}
