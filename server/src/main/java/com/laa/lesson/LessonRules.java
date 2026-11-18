package com.laa.lesson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

import com.laa.lesson.LessonDraft.Block;
import com.laa.lesson.LessonDraft.Rubric;
import com.laa.lesson.LessonDraft.Type;

/**
 * Turns a {@link LessonDraft} into a stored lesson, enforcing in code what the prompt only asks for
 * (BRIEF section 7):
 * <ul>
 *   <li>Quiz rules: 3–4 distinct options of similar length (characters within about 20%, word counts within
 *       one), none of the "all of the above" kind; options are shuffled and the answer index remapped.</li>
 *   <li>Citations must name a resource in RESOURCES.md; unknown ones are dropped, as is a primary-source
 *       block whose resource doesn't exist.</li>
 *   <li>Structure: required fields per block type, at least one "check yourself" quiz and one free-response
 *       practice with a rubric, and a warm-up first from lesson 2 on (lesson 1 has nothing to recall).</li>
 * </ul>
 * <ul>
 *   <li>Diagrams: a DIAGRAM block needs a brief and a caption, and a lesson keeps at most {@value #MAX_DIAGRAMS};
 *       later ones are dropped. The brief stays on the server, in {@link Result#diagrams}, for the diagram writer.</li>
 * </ul>
 * Block ids are renumbered b1…bn. Problems are either fixable by dropping a quiz ({@link Problem#quiz}) or
 * structural; the writer retries once with them listed, then drops rule-breaking quizzes if that's enough.
 */
public final class LessonRules {

    public record Problem(String message, boolean quiz) {
        static Problem structure(String m) {
            return new Problem(m, false);
        }

        static Problem quizRule(String m) {
            return new Problem(m, true);
        }
    }

    /** Server-side data for one free-response block: the rubric and the hint ladder. */
    public record Marking(List<String> points, String answerOutline, List<String> hints) {}

    /**
     * What the diagram writer is asked to draw for one DIAGRAM block (by its client id), and the caption the learner
     * sees under it.
     */
    public record DiagramBrief(String brief, String caption) {}

    /**
     * A lesson ready to store: client-safe blocks, server-only marking, the briefs for its diagrams (to be drawn
     * next, then filled in by {@link LessonChecks#withDiagrams}), and what was removed on the way.
     */
    public record Result(List<Map<String, Object>> blocks, Map<String, Marking> marking, Map<String, DiagramBrief> diagrams,
                         List<Problem> problems, List<String> removed) {
        public boolean ok() {
            return problems.isEmpty();
        }
    }

    static final Set<String> CALLOUT_KINDS = Set.of("win", "note", "watch_out");
    /** At most this many diagrams per lesson: each one costs a model call, and a page of pictures teaches less, not more. */
    static final int MAX_DIAGRAMS = 2;
    private static final Pattern NONE_OF_THE_KIND = Pattern.compile(
            "(?i)\\b(all|none|both|neither)\\s+of\\s+(the\\s+)?(above|these|those|options|answers)\\b|^\\s*(all|none)\\s*$");

    private LessonRules() {}

    /**
     * @param seq       the lesson's number; lesson 1 drops warm-ups, later lessons require one first
     * @param resources the ids citable from RESOURCES.md
     * @param dropBrokenQuizzes on the final attempt, drop quizzes that break the rules instead of failing
     */
    public static Result apply(LessonDraft draft, int seq, Set<String> resources, Random random, boolean dropBrokenQuizzes) {
        List<Problem> problems = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        Map<String, Rubric> rubrics = new HashMap<>();
        for (Rubric r : draft.rubrics()) rubrics.put(r.blockId(), r);

        Set<String> seen = new HashSet<>();
        for (Block b : draft.blocks()) {
            if (!seen.add(b.id())) problems.add(Problem.structure("Block id " + b.id() + " is used more than once."));
        }

        List<Block> kept = new ArrayList<>();
        int diagrams = 0;
        boolean warmupsOpen = true; // warm-ups may only lead the lesson
        for (Block b : draft.blocks()) {
            String where = "Block " + b.id() + " (" + b.type() + ")";
            if (b.type() == Type.QUIZ && "warmup".equals(b.style())) {
                if (seq == 1) {
                    removed.add(where + ": warm-up dropped, as lesson 1 has no earlier lesson to recall");
                    continue;
                }
                if (!warmupsOpen) problems.add(Problem.structure(where + ": warm-up quizzes must come first."));
            } else {
                warmupsOpen = false;
            }
            if (b.type() == Type.DIAGRAM && ++diagrams > MAX_DIAGRAMS) {
                // Dropped rather than sent back: the lesson is fine without it, and a rewrite would cost a whole lesson.
                removed.add(where + ": dropped, a lesson has at most " + MAX_DIAGRAMS + " diagrams");
                continue;
            }
            List<Problem> own = check(b, where, rubrics);
            if (b.type() == Type.QUIZ && dropBrokenQuizzes && !own.isEmpty() && own.stream().allMatch(Problem::quiz)) {
                removed.add(where + ": dropped for breaking the quiz rules (" + own.getFirst().message() + ")");
                continue;
            }
            problems.addAll(own);
            if (b.type() == Type.PRIMARY_SOURCE && (b.refs().isEmpty() || !resources.contains(b.refs().getFirst()))) {
                removed.add(where + ": primary source dropped, its resource isn't in RESOURCES.md");
                continue;
            }
            kept.add(b);
        }

        if (kept.stream().noneMatch(b -> b.type() == Type.QUIZ && "check".equals(b.style()))) {
            problems.add(Problem.structure("The lesson needs at least one \"check yourself\" quiz (style check)."));
        }
        if (kept.stream().noneMatch(b -> b.type() == Type.FREE_RESPONSE)) {
            problems.add(Problem.structure("The lesson needs at least one FREE_RESPONSE practice block."));
        }
        if (seq > 1 && (kept.isEmpty() || kept.getFirst().type() != Type.QUIZ || !"warmup".equals(kept.getFirst().style()))) {
            problems.add(Problem.structure("Lesson " + seq + " must open with a warm-up quiz recalling an earlier lesson."));
        }
        if (!problems.isEmpty()) return new Result(List.of(), Map.of(), Map.of(), problems, removed);

        List<Map<String, Object>> blocks = new ArrayList<>();
        Map<String, Marking> marking = new LinkedHashMap<>();
        Map<String, DiagramBrief> briefs = new LinkedHashMap<>();
        int n = 0;
        for (Block b : kept) {
            String id = "b" + (++n);
            blocks.add(toClient(id, b, resources, random, removed));
            if (b.type() == Type.FREE_RESPONSE) {
                Rubric r = rubrics.get(b.id());
                marking.put(id, new Marking(r.points(), r.answerOutline(), clean(b.items())));
            }
            if (b.type() == Type.DIAGRAM) briefs.put(id, new DiagramBrief(b.text().strip(), b.explanation().strip()));
        }
        return new Result(blocks, marking, briefs, List.of(), removed);
    }

    private static List<Problem> check(Block b, String where, Map<String, Rubric> rubrics) {
        List<Problem> p = new ArrayList<>();
        boolean noText = b.text().isBlank();
        switch (b.type()) {
            case HEADING, TEXT, KEY_IDEA -> {
                if (noText) p.add(Problem.structure(where + ": text is empty."));
            }
            case CALLOUT -> {
                if (!CALLOUT_KINDS.contains(b.style())) p.add(Problem.structure(where + ": style must be win, note or watch_out."));
                if (noText) p.add(Problem.structure(where + ": text is empty."));
            }
            case QUIZ -> {
                if (!"warmup".equals(b.style()) && !"check".equals(b.style())) {
                    p.add(Problem.structure(where + ": style must be warmup or check."));
                }
                if (noText) p.add(Problem.quizRule(where + ": the question is empty."));
                if (b.explanation().isBlank()) p.add(Problem.quizRule(where + ": the explanation is empty."));
                p.addAll(quizRules(where, b.items(), b.answer()));
            }
            case STEPS -> {
                if (noText) p.add(Problem.structure(where + ": the steps need a title in text."));
                if (clean(b.items()).size() < 2) p.add(Problem.structure(where + ": steps need at least 2 items."));
            }
            case FREE_RESPONSE -> {
                if (noText) p.add(Problem.structure(where + ": the prompt is empty."));
                int hints = clean(b.items()).size();
                if (hints < 2 || hints > 4) p.add(Problem.structure(where + ": give 3 hints (gentle to strong), not " + hints + "."));
                if (!rubrics.containsKey(b.id())) p.add(Problem.structure(where + ": no rubric with blockId " + b.id() + "."));
            }
            case PRIMARY_SOURCE -> {
                if (noText) p.add(Problem.structure(where + ": the note is empty."));
            }
            case ASK_TUTOR -> {
                int examples = clean(b.items()).size();
                if (examples < 1 || examples > 3) p.add(Problem.structure(where + ": give 1 to 3 example questions."));
            }
            case DIAGRAM -> {
                if (noText) p.add(Problem.structure(where + ": text needs the brief: what the diagram shows and how the learner explores it."));
                if (b.explanation().isBlank()) p.add(Problem.structure(where + ": explanation needs the caption shown under the diagram."));
            }
        }
        return p;
    }

    /** The quiz rules of BRIEF section 7, on the options as written. */
    static List<Problem> quizRules(String where, List<String> rawOptions, Integer answer) {
        List<Problem> p = new ArrayList<>();
        // Options keep their positions (the answer is an index into them), so a blank one is an error, not skipped.
        List<String> options = rawOptions.stream().map(String::strip).toList();
        if (options.stream().anyMatch(String::isEmpty)) {
            p.add(Problem.quizRule(where + ": an option is empty."));
            return p;
        }
        if (options.size() < 3 || options.size() > 4) {
            p.add(Problem.quizRule(where + ": a quiz needs 3 or 4 options, not " + options.size() + "."));
            return p;
        }
        if (answer == null || answer < 0 || answer >= options.size()) {
            p.add(Problem.quizRule(where + ": answer must be the index (0-" + (options.size() - 1) + ") of the correct option."));
        }
        Set<String> distinct = new HashSet<>();
        for (String o : options) {
            if (!distinct.add(o.toLowerCase(Locale.ROOT))) p.add(Problem.quizRule(where + ": options repeat (\"" + o + "\")."));
            if (NONE_OF_THE_KIND.matcher(o).find()) {
                p.add(Problem.quizRule(where + ": options like \"" + o + "\" aren't allowed."));
            }
        }
        int minChars = options.stream().mapToInt(String::length).min().orElse(0);
        int maxChars = options.stream().mapToInt(String::length).max().orElse(0);
        if (maxChars - minChars > Math.max(3, Math.round(0.2 * maxChars))) {
            p.add(Problem.quizRule(where + ": option lengths range from " + minChars + " to " + maxChars
                    + " characters; keep them within about 20% so length gives nothing away."));
        }
        int minWords = options.stream().mapToInt(LessonRules::words).min().orElse(0);
        int maxWords = options.stream().mapToInt(LessonRules::words).max().orElse(0);
        if (maxWords - minWords > 1) {
            p.add(Problem.quizRule(where + ": options have " + minWords + " to " + maxWords
                    + " words; keep word counts within one of each other."));
        }
        return p;
    }

    private static Map<String, Object> toClient(String id, Block b, Set<String> resources, Random random, List<String> removed) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("type", b.type().name().toLowerCase(Locale.ROOT));
        String text = b.text().strip();
        switch (b.type()) {
            case HEADING, KEY_IDEA -> m.put("text", text);
            case TEXT -> {
                List<String> citations = new ArrayList<>();
                for (String ref : b.refs()) {
                    if (resources.contains(ref)) citations.add(ref);
                    else removed.add("Block " + b.id() + ": citation " + ref + " dropped, it isn't in RESOURCES.md");
                }
                m.put("md", text);
                m.put("citations", citations);
            }
            case CALLOUT -> {
                m.put("kind", b.style());
                m.put("text", text);
            }
            case QUIZ -> {
                List<String> options = b.items().stream().map(String::strip).toList();
                List<Integer> order = new ArrayList<>();
                for (int i = 0; i < options.size(); i++) order.add(i);
                java.util.Collections.shuffle(order, random);
                List<String> shuffled = new ArrayList<>();
                for (int i : order) shuffled.add(options.get(i));
                m.put("variant", b.style());
                m.put("question", text);
                m.put("options", shuffled);
                m.put("answer", order.indexOf(b.answer()));
                m.put("explanation", b.explanation().strip());
            }
            case STEPS -> {
                m.put("title", text);
                m.put("items", clean(b.items()));
            }
            case FREE_RESPONSE -> {
                m.put("prompt", text);
                m.put("hintCount", clean(b.items()).size()); // the hints themselves are served one at a time (M4)
            }
            case PRIMARY_SOURCE -> {
                m.put("resource", b.refs().getFirst());
                m.put("note", text);
            }
            case ASK_TUTOR -> m.put("examples", clean(b.items()));
            // Only the caption for now: the brief stays on the server, and the page is added once it's drawn and checked.
            case DIAGRAM -> m.put("caption", b.explanation().strip());
        }
        return m;
    }

    private static List<String> clean(List<String> items) {
        return items.stream().map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    private static int words(String s) {
        String t = s.strip();
        return t.isEmpty() ? 0 : t.split("\\s+").length;
    }
}
