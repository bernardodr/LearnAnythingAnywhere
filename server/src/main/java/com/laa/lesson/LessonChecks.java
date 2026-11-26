package com.laa.lesson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.laa.lesson.LessonDraft.Block;
import com.laa.lesson.LessonDraft.ReferenceSheet;
import com.laa.lesson.LessonDraft.Rubric;
import com.laa.lesson.LessonDraft.Type;
import com.laa.llm.GenerationProfile;
import com.laa.profile.Profiles;
import com.laa.safety.Dashes;
import com.laa.safety.Moderation;
import com.laa.safety.ReaderView;
import com.laa.web.ApiException;

/**
 * Judges a lesson draft and says what happens next (BRIEF sections 7 and 8). The only way to a {@link CheckedLesson},
 * which is the only thing {@link LessonRepository#insert} saves.
 *
 * <p>Every attempt, in this order: the learner's links rule ({@link ReaderView}); the safety check on the header, each
 * block (with its rubric and hints) and the reference sheet, in one batch, with the run logged BLOCKED if anything
 * fails; then the lesson rules ({@link LessonRules}), once.
 * <ul>
 *   <li><b>First attempt:</b> {@link Pass}, or {@link Rewrite} with every problem found, of both kinds, so one rewrite
 *       can fix them all.</li>
 *   <li><b>Final attempt:</b> blocks that still fail become a placeholder note and quizzes that still break the rules
 *       are dropped; then {@link Pass}, or {@link Refuse}: for the reader settings (422) if the header or sheet failed
 *       or the placeholders broke the lesson, otherwise for the rules (502).</li>
 * </ul>
 *
 * <p>Diagrams come after: a passed lesson's DIAGRAM blocks hold only a caption until their pages are drawn.
 * {@link #withDiagrams} judges each drawn page ({@link DiagramRules}, then the links rule and the safety check on its
 * words) and puts the ones that pass into the lesson. One that fails, or was never drawn, is left out: a lesson is
 * never refused because of a diagram.
 */
@Component
public class LessonChecks {

    public enum Attempt { FIRST, FINAL }

    /** What a draft is: which lesson, the sources it may cite, whose it is, and the run that wrote it. */
    public record About(int seq, Set<String> sources, UUID userId, UUID textbookId, UUID runId) {
        public About withRun(UUID runId) {
            return new About(seq, sources, userId, textbookId, runId);
        }
    }

    /** One thing wrong with a draft. {@code target}: the block id, "title" or "reference sheet" (null for a rule). */
    public record Problem(Kind kind, String target, String message) {
        public enum Kind { LESSON_RULES, READER_SETTINGS }
    }

    public sealed interface Verdict permits Pass, Rewrite, Refuse {}

    /** Good to save. */
    public record Pass(CheckedLesson lesson) implements Verdict {}

    /** A diagram the writer drew for one DIAGRAM block, and the run that drew it (logged BLOCKED if it fails the check). */
    public record Drawn(DiagramDraft draft, UUID runId) {}

    /** First attempt only: rewrite {@code checked} (the draft with links applied) to fix these. */
    public record Rewrite(List<Problem> problems, LessonDraft checked) implements Verdict {}

    /** Final attempt only: nothing is saved; {@code error} is what the learner sees. */
    public record Refuse(ApiException error, List<Problem> problems) implements Verdict {}

    /**
     * A lesson that has passed its checks: the draft as it will be stored, and its client blocks and server-side
     * marking. Only {@link LessonChecks} can make one.
     */
    public static final class CheckedLesson {
        private final LessonDraft draft;
        private final LessonRules.Result result;
        private final List<String> changes;

        private CheckedLesson(LessonDraft draft, LessonRules.Result result, List<String> changes) {
            this.draft = draft;
            this.result = result;
            this.changes = List.copyOf(changes);
        }

        public LessonDraft draft() {
            return draft;
        }

        public LessonRules.Result result() {
            return result;
        }

        /** What the checks changed on the way: warm-ups or quizzes dropped, citations dropped, placeholders. */
        public List<String> changes() {
            return changes;
        }
    }

    private static ApiException forReaderSettings() {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT,
                "This lesson didn’t fit your reader settings, so it wasn’t saved. Please try again.");
    }

    private static ApiException forTheRules() {
        return new ApiException(HttpStatus.BAD_GATEWAY, "The lesson didn’t pass its checks, so it wasn’t saved. Please try again.");
    }

    private static final String HEADER = "header";
    private static final String SHEET = "sheet";
    private static final String BLOCK = "block-";
    private static final String DIAGRAM = "diagram-";

    private final Moderation moderation;
    private final ReaderView view;
    private final Profiles profiles;
    private final Random random = new Random();

    LessonChecks(Moderation moderation, ReaderView view, Profiles profiles) {
        this.moderation = moderation;
        this.view = view;
        this.profiles = profiles;
    }

    public Verdict check(LessonDraft draft, Attempt attempt, About about) {
        GenerationProfile profile = profiles.forUser(about.userId());
        LessonDraft lesson = links(draft, view.of(about.userId()));
        Moderation.Result safety = moderate(lesson, profile, about);
        if (!safety.passed()) moderation.markBlocked(about.runId(), safety.flags());
        List<Problem> safetyProblems = problems(safety);

        if (attempt == Attempt.FIRST) {
            LessonRules.Result rules = LessonRules.apply(lesson, about.seq(), about.sources(), random, false);
            List<Problem> problems = new ArrayList<>(rulesProblems(rules));
            problems.addAll(safetyProblems);
            return problems.isEmpty() ? new Pass(new CheckedLesson(lesson, rules, rules.removed())) : new Rewrite(problems, lesson);
        }

        if (safety.blocks(HEADER) || safety.blocks(SHEET)) return new Refuse(forReaderSettings(), safetyProblems);
        List<String> changes = new ArrayList<>();
        LessonDraft placed = withPlaceholders(lesson, safety, changes);
        LessonRules.Result rules = LessonRules.apply(placed, about.seq(), about.sources(), random, true);
        if (!rules.ok()) {
            List<Problem> problems = new ArrayList<>(rulesProblems(rules));
            problems.addAll(safetyProblems);
            return new Refuse(safety.passed() ? forTheRules() : forReaderSettings(), problems);
        }
        changes.addAll(rules.removed());
        return new Pass(new CheckedLesson(placed, rules, changes));
    }

    /**
     * The lesson with its diagrams drawn in. For each DIAGRAM block, in order: no drawing, or a page that breaks
     * {@link DiagramRules}, and the block is left out; otherwise the learner's links rule goes on its title and text
     * version, and its words (title, text version, and everything readable on the page) go through the safety check
     * with the other diagrams, in one batch. A diagram that fails is left out and its run logged BLOCKED.
     *
     * @param drawn the drawn diagrams, by the client id of their DIAGRAM block; blocks missing from it are left out
     * @throws com.laa.safety.Moderation.Unavailable (503) if the safety check can't run: then nothing is saved
     */
    public CheckedLesson withDiagrams(CheckedLesson lesson, Map<String, Drawn> drawn, About about) {
        LessonRules.Result result = lesson.result();
        if (result.diagrams().isEmpty()) return lesson;
        List<String> changes = new ArrayList<>(lesson.changes());
        ReaderView.Reader reader = view.of(about.userId());

        // First the page rules, then one safety check over every diagram still standing.
        Map<String, DiagramDraft> ready = new LinkedHashMap<>();
        for (String id : result.diagrams().keySet()) {
            Drawn d = drawn.get(id);
            if (d == null) {
                changes.add("Diagram " + id + ": left out, it couldn't be drawn");
                continue;
            }
            // No em dashes in the page's words either (issue #80); the reader view filters it again when it's shown.
            String html = Dashes.replace(DiagramRules.normalise(d.draft().html()));
            List<String> problems = DiagramRules.problems(html);
            if (!problems.isEmpty()) {
                changes.add("Diagram " + id + ": left out, its page broke the rules (" + String.join(" ", problems) + ")");
                continue;
            }
            ready.put(id, new DiagramDraft(reader.apply(d.draft().title()), html, reader.apply(d.draft().textVersion())));
        }
        List<Moderation.Item> items = ready.entrySet().stream().map(e -> new Moderation.Item(DIAGRAM + e.getKey(),
                String.join("\n", e.getValue().title(), e.getValue().textVersion(), DiagramRules.words(e.getValue().html())))).toList();
        Moderation.Result safety = items.isEmpty() ? null
                : moderation.check(items, profiles.forUser(about.userId()), Moderation.Kind.OUTPUT, about.userId(), about.textbookId());

        List<Map<String, Object>> blocks = new ArrayList<>();
        for (Map<String, Object> block : result.blocks()) {
            String id = (String) block.get("id");
            if (!result.diagrams().containsKey(id)) {
                blocks.add(block);
                continue;
            }
            DiagramDraft d = ready.get(id);
            if (d == null) continue; // already noted in changes
            if (safety.blocks(DIAGRAM + id)) {
                moderation.markBlocked(drawn.get(id).runId(), safety.flags());
                changes.add("Diagram " + id + ": left out, it didn’t fit the reader settings");
                continue;
            }
            Map<String, Object> filled = new LinkedHashMap<>(block);
            filled.put("title", d.title());
            filled.put("html", d.html());
            filled.put("text", d.textVersion());
            blocks.add(filled);
        }
        LessonRules.Result withPages = new LessonRules.Result(blocks, result.marking(), Map.of(), result.problems(), result.removed());
        return new CheckedLesson(lesson.draft(), withPages, changes);
    }

    // ------------------------------------------------------------------ the steps

    /** The learner's links rule on every piece of text in the draft. */
    private static LessonDraft links(LessonDraft d, ReaderView.Reader reader) {
        List<Block> blocks = d.blocks().stream().map(b -> new Block(b.id(), b.type(), b.style(), reader.apply(b.text()),
                b.items().stream().map(reader::apply).toList(), b.answer(), reader.apply(b.explanation()), b.refs())).toList();
        ReferenceSheet sheet = new ReferenceSheet(d.referenceSheet().title(), reader.apply(d.referenceSheet().md()));
        List<Rubric> rubrics = d.rubrics().stream().map(r -> new Rubric(r.blockId(), r.points().stream().map(reader::apply).toList(),
                reader.apply(r.answerOutline()))).toList();
        return new LessonDraft(reader.apply(d.title()), reader.apply(d.subtitle()), reader.apply(d.win()), blocks, sheet, rubrics);
    }

    /** The safety check on the header, each block (a practice block with its rubric and hints) and the sheet. */
    private Moderation.Result moderate(LessonDraft d, GenerationProfile profile, About about) {
        Map<String, Rubric> rubrics = new HashMap<>();
        for (Rubric r : d.rubrics()) rubrics.put(r.blockId(), r);
        List<Moderation.Item> items = new ArrayList<>();
        items.add(new Moderation.Item(HEADER, String.join("\n", d.title(), d.subtitle(), d.win())));
        for (Block b : d.blocks()) {
            List<String> parts = new ArrayList<>();
            parts.add(b.text());
            parts.addAll(b.items());
            parts.add(b.explanation());
            Rubric r = rubrics.get(b.id());
            if (b.type() == Type.FREE_RESPONSE && r != null) {
                parts.addAll(r.points());
                parts.add(r.answerOutline());
            }
            items.add(new Moderation.Item(BLOCK + b.id(), String.join("\n", parts.stream().filter(p -> p != null && !p.isBlank()).toList())));
        }
        items.add(new Moderation.Item(SHEET, d.referenceSheet().title() + "\n" + d.referenceSheet().md()));
        return moderation.check(items, profile, Moderation.Kind.OUTPUT, about.userId(), about.textbookId());
    }

    private static List<Problem> rulesProblems(LessonRules.Result rules) {
        return rules.problems().stream().map(p -> new Problem(Problem.Kind.LESSON_RULES, null, p.message())).toList();
    }

    private static List<Problem> problems(Moderation.Result safety) {
        List<Problem> out = new ArrayList<>();
        safety.reasons().forEach((id, why) -> {
            String target = switch (id) {
                case HEADER -> "title";
                case SHEET -> "reference sheet";
                default -> id.substring(BLOCK.length());
            };
            String what = switch (id) {
                case HEADER -> "The title, subtitle or win";
                case SHEET -> "The reference sheet";
                default -> "Block " + target;
            };
            out.add(new Problem(Problem.Kind.READER_SETTINGS, target,
                    what + " doesn’t fit this learner’s profile (" + why + "). Rewrite it so it follows the profile rules."));
        });
        return out;
    }

    /** The draft with every block that failed the safety check replaced by a note saying it was left out. */
    private static LessonDraft withPlaceholders(LessonDraft d, Moderation.Result safety, List<String> changes) {
        List<Block> blocks = new ArrayList<>();
        for (Block b : d.blocks()) {
            if (safety.blocks(BLOCK + b.id())) {
                changes.add("Block " + b.id() + " (" + b.type() + "): replaced with a placeholder, it didn’t fit the reader settings");
                blocks.add(new Block(b.id(), Type.CALLOUT, "note", Moderation.PLACEHOLDER, List.of(), -1, "", List.of()));
            } else {
                blocks.add(b);
            }
        }
        return new LessonDraft(d.title(), d.subtitle(), d.win(), blocks, d.referenceSheet(), d.rubrics());
    }
}
