package com.laa.lesson;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.laa.job.Jobs;
import com.laa.lesson.LessonChecks.Attempt;
import com.laa.lesson.LessonChecks.Pass;
import com.laa.lesson.LessonChecks.Refuse;
import com.laa.lesson.LessonChecks.Rewrite;
import com.laa.lesson.LessonChecks.Verdict;
import com.laa.lesson.LessonRepository.Lesson;
import com.laa.llm.Calls;
import com.laa.llm.GenerationProfile;
import com.laa.llm.LlmGateway;
import com.laa.llm.LlmResult;
import com.laa.llm.LlmUnavailableException;
import com.laa.llm.PromptContext;
import com.laa.llm.StructuredOutputException;
import com.laa.llm.runs.Origin;
import com.laa.textbook.Textbook;
import com.laa.web.ApiException;
import com.laa.workspace.Resources.Resource;
import com.laa.workspace.TeachingContext;

import tools.jackson.databind.json.JsonMapper;

/**
 * Writes the next lesson (BRIEF section 6.4): load the workspace, LESSON_PLAN picks one skill in the learner's zone of
 * proximal development, LESSON_WRITE writes it, and {@link LessonChecks} judges it. If the checks ask for a rewrite,
 * the writer gets the problems back and rewrites once, and the checks judge that as the final attempt. Only what the
 * checks pass can be saved.
 *
 * <p><b>Diagrams.</b> The lesson writer may mark up to two places where a picture would help (DIAGRAM blocks, each a
 * brief and a caption). Once the lesson has passed, each brief goes to the diagram writer ({@code Calls.DIAGRAM}),
 * which returns a small interactive page. A page that breaks {@link DiagramRules} is sent back once with the reasons;
 * then {@link LessonChecks#withDiagrams} has the final say. A diagram that can't be drawn (the writer is down, or its
 * answer is the wrong shape) or doesn't pass is left out, and the lesson is saved without it. So diagrams can make a
 * lesson better, but never stop one.
 */
@Service
public class LessonWriter {

    private static final Logger log = LoggerFactory.getLogger(LessonWriter.class);
    /** How many earlier lessons the warm-up can draw on. */
    private static final int WARMUP_LOOKBACK = 3;

    public record Written(int seq, String lessonId, String title) {}

    private final LlmGateway gateway;
    private final TeachingContext teaching;
    private final LessonRepository lessons;
    private final LessonChecks checks;
    private final JsonMapper json;

    LessonWriter(LlmGateway gateway, TeachingContext teaching, LessonRepository lessons, LessonChecks checks, JsonMapper json) {
        this.gateway = gateway;
        this.teaching = teaching;
        this.lessons = lessons;
        this.checks = checks;
        this.json = json;
    }

    public Written writeNext(Textbook tb, UUID userId, GenerationProfile profile, Jobs.Progress progress) {
        progress.stage("context", "Reading your mission and notes");
        TeachingContext.Context ctx = teaching.load(tb.id(), tb.title());
        List<Lesson> earlier = lessons.list(tb.id());
        int seq = earlier.size() + 1;
        Set<String> resourceIds = ctx.resources().stream().map(Resource::id).collect(Collectors.toSet());
        Map<String, String> vars = TeachingContext.with(ctx.variables(), Map.of(
                "seq", String.valueOf(seq),
                "earlier_lessons", earlierLessons(earlier),
                "warmup_material", warmupMaterial(earlier)));

        try {
            progress.stage("plan", "Choosing what to teach next");
            LessonPlan plan = gateway.structured(Calls.LESSON_PLAN, prompt(vars, "Plan lesson " + seq + ".", userId, tb.id()), profile);

            // Fixed wording only: progress reaches the learner before the lesson is checked (BRIEF section 8).
            progress.stage("write", "Writing lesson " + seq);
            String brief = "Write lesson " + seq + " from this plan:\n" + json.writeValueAsString(plan);
            LlmResult<LessonDraft> draft = gateway.structuredResult(Calls.LESSON_WRITE, prompt(vars, brief, userId, tb.id()), profile);

            progress.stage("check", "Checking the quizzes, sources and reader settings");
            LessonChecks.About about = new LessonChecks.About(seq, resourceIds, userId, tb.id(), draft.runId());
            Verdict verdict = checks.check(draft.value(), Attempt.FIRST, about);
            if (verdict instanceof Rewrite rewrite) {
                List<String> problems = rewrite.problems().stream().map(LessonChecks.Problem::message).toList();
                log.info("Lesson {} for textbook {} failed its checks, rewriting: {}", seq, tb.id(), problems);
                progress.stage("rewrite", "Tidying up a few things");
                String fix = brief + "\n\nYour previous version had these problems:\n- " + String.join("\n- ", problems)
                        + "\n\nYour previous version, to correct:\n" + json.writeValueAsString(rewrite.checked())
                        + "\n\nReturn the whole corrected lesson.";
                draft = gateway.structuredResult(Calls.LESSON_WRITE, prompt(vars, fix, userId, tb.id()), profile);
                verdict = checks.check(draft.value(), Attempt.FINAL, about.withRun(draft.runId()));
            }
            LessonChecks.CheckedLesson lesson = switch (verdict) {
                case Pass pass -> pass.lesson();
                case Refuse refuse -> {
                    log.warn("Lesson {} for textbook {} wasn't saved: {}", seq, tb.id(),
                            refuse.problems().stream().map(LessonChecks.Problem::message).toList());
                    throw refuse.error();
                }
                case Rewrite r -> throw new IllegalStateException("A final check never asks for another rewrite");
            };
            if (!lesson.result().diagrams().isEmpty()) {
                progress.stage("draw", "Drawing the diagrams");
                Map<String, LessonChecks.Drawn> drawn = new LinkedHashMap<>();
                for (var diagram : lesson.result().diagrams().entrySet()) {
                    draw(diagram.getValue(), seq, lesson.draft(), vars, userId, tb, profile).ifPresent(d -> drawn.put(diagram.getKey(), d));
                }
                lesson = checks.withDiagrams(lesson, drawn, about);
            }
            if (!lesson.changes().isEmpty()) log.info("Lesson {} for textbook {}: {}", seq, tb.id(), lesson.changes());

            progress.stage("save", "Binding the pages");
            String missionLink = tb.missionHeadline() == null ? tb.title() : tb.missionHeadline();
            Lesson saved = lessons.insert(tb.id(), seq, lesson, missionLink, plan, draft.runId());
            return new Written(saved.seq(), saved.id().toString(), saved.title());
        } catch (LlmUnavailableException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The writer couldn’t be reached just now. Nothing was saved; please try again.");
        } catch (StructuredOutputException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "The lesson came back in the wrong shape twice, so it wasn’t saved. Please try again.");
        }
    }

    /**
     * Asks the diagram writer for one diagram, and once more with the reasons if its page breaks {@link DiagramRules}.
     * Empty when no diagram came back at all; a page that still breaks the rules is returned anyway, for
     * {@link LessonChecks#withDiagrams} to leave out and note.
     */
    private Optional<LessonChecks.Drawn> draw(LessonRules.DiagramBrief brief, int seq, LessonDraft lesson, Map<String, String> vars,
                                              UUID userId, Textbook tb, GenerationProfile profile) {
        String ask = "Draw one interactive diagram for lesson " + seq + ", \"" + lesson.title() + "\".\n\n"
                + "What it shows, and how the learner explores it:\n" + brief.brief() + "\n\n"
                + "The caption under it: " + brief.caption() + "\n\n"
                + "The lesson it belongs to, so the diagram uses the same words and examples:\n" + lessonText(lesson);
        try {
            LlmResult<DiagramDraft> first = gateway.structuredResult(Calls.DIAGRAM, prompt(vars, ask, userId, tb.id()), profile);
            List<String> problems = DiagramRules.problems(DiagramRules.normalise(first.value().html()));
            if (problems.isEmpty()) return Optional.of(new LessonChecks.Drawn(first.value(), first.runId()));
            log.info("A diagram for lesson {} of textbook {} broke the page rules, redrawing: {}", seq, tb.id(), problems);
            String fix = ask + "\n\nYour previous page had these problems:\n- " + String.join("\n- ", problems)
                    + "\n\nYour previous page, to correct:\n" + first.value().html() + "\n\nReturn the whole corrected diagram.";
            LlmResult<DiagramDraft> second = gateway.structuredResult(Calls.DIAGRAM, prompt(vars, fix, userId, tb.id()), profile);
            return Optional.of(new LessonChecks.Drawn(second.value(), second.runId()));
        } catch (LlmUnavailableException | StructuredOutputException e) {
            log.warn("A diagram for lesson {} of textbook {} couldn't be drawn, so it's left out: {}", seq, tb.id(), e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** The lesson's teaching text (headings, paragraphs, the key idea and callouts), as context for the diagram writer. */
    private static String lessonText(LessonDraft lesson) {
        StringBuilder out = new StringBuilder(lesson.subtitle()).append("\n");
        for (LessonDraft.Block b : lesson.blocks()) {
            switch (b.type()) {
                case HEADING -> out.append("\n## ").append(b.text()).append('\n');
                case TEXT, KEY_IDEA, CALLOUT -> out.append(b.text()).append('\n');
                default -> { }
            }
        }
        return out.length() > 6000 ? out.substring(0, 6000) + "…" : out.toString();
    }

    private static PromptContext prompt(Map<String, String> vars, String message, UUID userId, UUID textbookId) {
        return new PromptContext(vars, List.of(), message, userId, textbookId, Origin.APP);
    }

    private static String earlierLessons(List<Lesson> earlier) {
        if (earlier.isEmpty()) return "None. This is the first lesson.";
        StringBuilder out = new StringBuilder();
        for (Lesson l : earlier) {
            out.append("- Lesson ").append(l.seq()).append(": ").append(l.title())
                    .append(". Skill: ").append(l.plan().skill())
                    .append(" Win: ").append(l.win())
                    .append(l.readAt() == null ? " (not finished yet)" : " (finished)").append('\n');
        }
        return out.toString().strip();
    }

    /** What the warm-up can be built from: the recent lessons' check questions (with answers) and practice prompts. */
    private static String warmupMaterial(List<Lesson> earlier) {
        if (earlier.isEmpty()) return "None: lesson 1 has no warm-up.";
        StringBuilder out = new StringBuilder();
        for (Lesson l : earlier.subList(Math.max(0, earlier.size() - WARMUP_LOOKBACK), earlier.size())) {
            out.append("Lesson ").append(l.seq()).append(" (").append(l.title()).append("):\n");
            for (Map<String, Object> b : l.blocks()) {
                if ("quiz".equals(b.get("type")) && "check".equals(b.get("variant"))) {
                    @SuppressWarnings("unchecked")
                    List<String> options = (List<String>) b.get("options");
                    int answer = ((Number) b.get("answer")).intValue();
                    out.append("- Check question: ").append(b.get("question"))
                            .append(" Correct answer: ").append(options.get(answer)).append('\n');
                } else if ("free_response".equals(b.get("type"))) {
                    out.append("- Practice prompt: ").append(b.get("prompt")).append('\n');
                }
            }
        }
        return out.toString().strip();
    }
}
