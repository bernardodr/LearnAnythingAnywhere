package com.laa.workspace;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.laa.textbook.Familiarity;
import com.laa.textbook.Textbook;

import com.laa.workspace.Resources.Resource;
import com.laa.workspace.WorkspaceFiles.WorkspaceFile;

/**
 * Context assembly (BRIEF section 6.1): every teaching call loads the textbook's workspace into its prompt:
 * mission, glossary, recent learning records, notes and resources. The learner's profile rules are added
 * by the gateway. Missing files render as an explicit "none yet", so the model never fills the gap itself.
 */
@Component
public class TeachingContext {

    /** How many of the most recent learning records go into a prompt. */
    static final int RECENT_RECORDS = 5;

    public record Context(Map<String, String> variables, List<Resource> resources) {}

    private final WorkspaceFiles files;

    TeachingContext(WorkspaceFiles files) {
        this.files = files;
    }

    /**
     * The workspace of this book, as prompt variables: title, mission, glossary, recent learning records, notes,
     * sources, and how familiar the learner said they were when they started it ({@code familiarity}, issue #83).
     */
    public Context load(Textbook tb) {
        UUID textbookId = tb.id();
        List<Resource> resources = Resources.parse(read(textbookId, "RESOURCES.md"));
        List<String> recordPaths = files.list(textbookId).stream()
                .filter(p -> p.startsWith("learning-records/")).sorted().toList();
        StringBuilder records = new StringBuilder();
        for (String path : recordPaths.subList(Math.max(0, recordPaths.size() - RECENT_RECORDS), recordPaths.size())) {
            records.append("### ").append(path).append('\n').append(read(textbookId, path).strip()).append("\n\n");
        }

        Map<String, String> v = new HashMap<>();
        v.put("title", tb.title());
        v.put("familiarity", Familiarity.forPrompt(tb.familiarity()));
        v.put("mission", orNone(read(textbookId, WorkspaceFiles.MISSION), "No mission yet."));
        v.put("glossary", orNone(read(textbookId, "GLOSSARY.md"),
                "None yet. Terms are added once the learner shows they understand them."));
        v.put("records", orNone(records.toString(),
                "None yet. There's no evidence of what the learner can do beyond what the mission says."));
        v.put("notes", orNone(read(textbookId, "NOTES.md"), "None."));
        v.put("resources", resources.isEmpty()
                ? "None. The learner hasn't added any sources, and LAA never invents them. Cite nothing: every refs list "
                        + "stays empty and there are no PRIMARY_SOURCE blocks."
                : formatResources(resources));
        return new Context(v, resources);
    }

    private String read(UUID textbookId, String path) {
        return files.read(textbookId, path).map(WorkspaceFile::content).orElse(null);
    }

    private static String formatResources(List<Resource> resources) {
        StringBuilder out = new StringBuilder();
        for (Resource r : resources) {
            out.append("- ").append(r.id()).append(": ").append(r.title());
            if (r.url() != null) out.append(" <").append(r.url()).append('>');
            if (!r.note().isEmpty()) out.append(". ").append(r.note());
            out.append('\n');
        }
        return out.toString().strip();
    }

    private static String orNone(String content, String none) {
        return content == null || content.isBlank() ? none : content.strip();
    }

    /** Variables merged over a base map (for callers adding their own). */
    public static Map<String, String> with(Map<String, String> base, Map<String, String> extra) {
        Map<String, String> v = new HashMap<>(base);
        v.putAll(extra);
        return v;
    }
}
