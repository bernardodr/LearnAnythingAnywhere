package com.laa.workspace;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GLOSSARY.md in /teach's GLOSSARY-FORMAT.md:
 * <pre>
 * # {Topic} Glossary
 *
 * {One or two sentence description.}
 *
 * ## Terms
 *
 * **Term**:
 * Definition.
 * _Avoid_: alias, alias
 * </pre>
 * Terms are revised in place when they come up again ("revise as understanding deepens"), otherwise appended.
 */
public final class Glossary {

    public record Term(String term, String definition, List<String> avoid) {}

    private static final Pattern ENTRY = Pattern.compile("(?m)^\\*\\*(.+?)\\*\\*:\\s*\\n(.+?)(?:\\n_Avoid_:\\s*(.+))?$");

    private Glossary() {}

    public static List<Term> parse(String md) {
        List<Term> out = new ArrayList<>();
        if (md == null) return out;
        Matcher m = ENTRY.matcher(md);
        while (m.find()) {
            List<String> avoid = m.group(3) == null ? List.of()
                    : List.of(m.group(3).split("\\s*,\\s*")).stream().map(String::strip).filter(s -> !s.isEmpty()).toList();
            out.add(new Term(m.group(1).strip(), m.group(2).strip(), avoid));
        }
        return out;
    }

    /** The glossary with these terms added or revised, rebuilt in the /teach format. */
    public static String upsert(String md, String topic, List<Term> terms) {
        Map<String, Term> byKey = new LinkedHashMap<>();
        for (Term t : parse(md)) byKey.put(key(t.term()), t);
        for (Term t : terms) {
            Term clean = new Term(line(t.term()), line(t.definition()),
                    t.avoid().stream().map(Glossary::line).filter(s -> !s.isEmpty() && !s.contains(",")).toList());
            if (clean.term().isEmpty() || clean.definition().isEmpty()) continue;
            Term existing = byKey.get(key(clean.term()));
            // Revising keeps the glossary's existing name for the term; only its definition and aliases change.
            byKey.put(key(clean.term()), existing == null ? clean : new Term(existing.term(), clean.definition(), clean.avoid()));
        }
        StringBuilder out = new StringBuilder("# ").append(line(topic)).append(" Glossary\n\n")
                .append(description(md, topic)).append("\n\n## Terms\n");
        for (Term t : byKey.values()) {
            out.append("\n**").append(t.term()).append("**:\n").append(t.definition()).append('\n');
            if (!t.avoid().isEmpty()) out.append("_Avoid_: ").append(String.join(", ", t.avoid())).append('\n');
        }
        return out.toString();
    }

    /** Keeps an existing description line; otherwise a default one. */
    private static String description(String md, String topic) {
        if (md != null) {
            String[] lines = md.split("\n");
            for (int i = 1; i < lines.length; i++) {
                String l = lines[i].strip();
                if (l.startsWith("## ")) break;
                if (!l.isEmpty()) return l;
            }
        }
        return "The words you use correctly in " + line(topic) + ", and the ones to avoid.";
    }

    private static String key(String term) {
        return term.strip().toLowerCase(Locale.ROOT);
    }

    private static String line(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").strip().replace("**", "");
    }
}
