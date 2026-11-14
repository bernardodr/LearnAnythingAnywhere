package com.laa.workspace;

import java.util.List;
import java.util.Locale;

/**
 * Learning records in /teach's LEARNING-RECORD-FORMAT.md: {@code learning-records/0001-<dash-case>.md}, numbered by
 * scanning for the highest existing number; a short title, 1–3 sentences, and an optional Evidence section.
 */
public final class LearningRecords {

    private LearningRecords() {}

    /** The next record's path, given the workspace's existing paths. */
    public static String nextPath(List<String> existingPaths, String slug) {
        int max = 0;
        for (String p : existingPaths) {
            if (!p.startsWith("learning-records/")) continue;
            String name = p.substring("learning-records/".length());
            if (name.length() >= 4 && name.substring(0, 4).chars().allMatch(Character::isDigit)) {
                max = Math.max(max, Integer.parseInt(name.substring(0, 4)));
            }
        }
        return "learning-records/%04d-%s.md".formatted(max + 1, slug(slug));
    }

    public static String render(String title, String body, String evidence) {
        StringBuilder md = new StringBuilder("# ").append(line(title)).append("\n\n").append(line(body)).append('\n');
        if (evidence != null && !evidence.isBlank()) md.append("\n## Evidence\n").append(line(evidence)).append('\n');
        return md.toString();
    }

    /** dash-case, ascii letters and digits, at most 6 words; "record" if nothing usable is left. */
    static String slug(String raw) {
        String s = raw == null ? "" : raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        String[] parts = s.isEmpty() ? new String[0] : s.split("-");
        if (parts.length == 0) return "record";
        return String.join("-", java.util.Arrays.copyOf(parts, Math.min(parts.length, 6)));
    }

    private static String line(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").strip().replaceFirst("^#+\\s*", "");
    }
}
