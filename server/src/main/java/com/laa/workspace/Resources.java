package com.laa.workspace;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RESOURCES.md in /teach's RESOURCES-FORMAT.md: entries under {@code ## Knowledge} are citable and get ids
 * r1, r2, … in the order they appear; communities under {@code ## Wisdom} aren't cited. LAA never invents
 * resources (BRIEF section 6.3): without a research tool, the only entries are ones the learner added.
 */
public final class Resources {

    public record Resource(String id, String title, String url, String note) {}

    private static final Pattern LINKED = Pattern.compile("^- \\[(.+?)]\\((\\S+?)\\)\\s*(.*)$");
    private static final Pattern PLAIN = Pattern.compile("^- (.+)$");

    private Resources() {}

    /** Knowledge entries with their ids. Null or empty content means no resources. */
    public static List<Resource> parse(String md) {
        List<Resource> out = new ArrayList<>();
        if (md == null || md.isBlank()) return out;
        boolean inKnowledge = false;
        String title = null, url = null;
        StringBuilder note = new StringBuilder();
        for (String line : md.split("\n")) {
            if (line.startsWith("## ")) {
                if (title != null) out.add(entry(out.size(), title, url, note));
                title = null;
                inKnowledge = line.substring(3).strip().equalsIgnoreCase("Knowledge");
                continue;
            }
            if (!inKnowledge) continue;
            Matcher linked = LINKED.matcher(line);
            Matcher plain = PLAIN.matcher(line);
            if (linked.matches() || plain.matches()) {
                if (title != null) out.add(entry(out.size(), title, url, note));
                note.setLength(0);
                if (linked.matches()) {
                    title = linked.group(1).strip();
                    url = linked.group(2).strip();
                    note.append(linked.group(3).strip());
                } else {
                    title = plain.group(1).strip();
                    url = null;
                }
            } else if (title != null && line.startsWith("  ") && !line.isBlank()) {
                if (!note.isEmpty()) note.append(' ');
                note.append(line.strip());
            }
        }
        if (title != null) out.add(entry(out.size(), title, url, note));
        return out;
    }

    /** RESOURCES.md with one more Knowledge entry, creating the file or the section if needed. */
    public static String append(String md, String topic, String title, String url, String note) {
        // Square brackets would break the Markdown link, so they become round ones ("[Draft] Guide" reads "(Draft) Guide").
        String entry = "- [" + line(title).replace("[", "(").replace("]", ")") + "](" + url.strip().replace(" ", "%20").replace(")", "%29") + ")\n"
                + (line(note).isEmpty() ? "" : "  " + line(note) + "\n");
        if (md == null || md.isBlank()) {
            return "# " + line(topic) + " Resources\n\n## Knowledge\n\n" + entry;
        }
        String text = md.endsWith("\n") ? md : md + "\n";
        int heading = indexOfHeading(text, "Knowledge");
        if (heading < 0) { // a Knowledge section goes before the first other section (Wisdom, Gaps), or at the end
            String section = "## Knowledge\n\n" + entry;
            int firstSection = text.indexOf("\n## ");
            if (firstSection < 0) return text + "\n" + section;
            return text.substring(0, firstSection + 1) + section + "\n" + text.substring(firstSection + 1);
        }
        int next = text.indexOf("\n## ", heading + 1);
        int insertAt = next < 0 ? text.length() : next + 1;
        String before = text.substring(0, insertAt).replaceAll("\n+$", "\n");
        String after = text.substring(insertAt);
        return before + entry + (after.isEmpty() ? "" : "\n" + after);
    }

    /** http(s) URLs with a host, as typed by the learner. Whether they resolve isn't checked (that's the research stretch). */
    public static boolean isWebUrl(String url) {
        try {
            URI uri = URI.create(url.strip());
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && !uri.getHost().isBlank();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static int indexOfHeading(String text, String name) {
        Matcher m = Pattern.compile("(?m)^## " + Pattern.quote(name) + "\\s*$").matcher(text);
        return m.find() ? m.start() : -1;
    }

    private static Resource entry(int index, String title, String url, CharSequence note) {
        return new Resource("r" + (index + 1), title, url, note.toString().strip());
    }

    private static String line(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").strip();
    }
}
