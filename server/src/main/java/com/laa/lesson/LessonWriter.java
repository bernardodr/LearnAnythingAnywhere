package com.laa.lesson;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.laa.job.Jobs;
import com.laa.lesson.LessonRepository.Lesson;
import com.laa.llm.GenerationProfile;
import com.laa.llm.LlmGateway;
import com.laa.llm.LlmResult;
import com.laa.llm.LlmUnavailableException;
import com.laa.llm.PromptContext;
import com.laa.llm.StructuredOutputException;
import com.laa.llm.Task;
import com.laa.llm.runs.Origin;
import com.laa.safety.Moderation;
import com.laa.textbook.Textbook;
import com.laa.web.ApiException;
import com.laa.workspace.Resources.Resource;
import com.laa.workspace.TeachingContext;

import tools.jackson.databind.json.JsonMapper;

/**
 * Writes the next lesson (BRIEF section 6.4): load the workspace, LESSON_PLAN picks one skill in the learner's
 * zone of proximal development, LESSON_WRITE writes it, the links policy is applied, and {@link LessonRules} and
 * the safety check ({@link LessonSafety}, BRIEF section 8) check it. If either finds problems, the writer gets them
 * back and rewrites once. On that last attempt, quizzes that still break the rules are dropped if the lesson stands
 * without them, and blocks that still don't pass the safety check become a placeholder note. Only a lesson that
 * passes is stored.
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
    private final Moderation moderation;
    private final JsonMapper json;
    private final Random random = new Random();

    LessonWriter(LlmGateway gateway, TeachingContext teaching, LessonRepository lessons, Moderation moderation, JsonMapper json) {
        this.gateway = gateway;
        this.teaching = teaching;
        this.lessons = lessons;
        this.moderation = moderation;
        this.json = json;
    }

    public Written writeNext(Textbook tb, UUID userId, GenerationProfile profile, Jobs.Progress progress) {
        progress.stage("context", "Reading your mission and notes");
        TeachingContext.Context ctx = teaching.load(tb.id(), tb.title());
        List<Lesson> earlier = lessons.list(tb.id());
        int seq = earlier.size() + 1;
        Set<String> resourceIds = ctx.resources().stream().map(Resource::id).collect(Collectors.toSet());
        Set<String> allowedLinks = ctx.resources().stream().map(Resource::url).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, String> vars = TeachingContext.with(ctx.variables(), Map.of(
                "seq", String.valueOf(seq),
                "earlier_lessons", earlierLessons(earlier),
                "warmup_material", warmupMaterial(earlier)));

        try {
            progress.stage("plan", "Choosing what to teach next");
            LessonPlan plan = gateway.structured(Task.LESSON_PLAN,
                    prompt(vars, "Plan lesson " + seq + ".", userId, tb.id()), LessonPlan.class, profile);

            progress.stage("write", "Writing lesson " + seq + ": " + plan.title().strip());
            String brief = "Write lesson " + seq + " from this plan:\n" + json.writeValueAsString(plan);
            LlmResult<LessonDraft> draft = gateway.structuredResult(Task.LESSON_WRITE,
                    prompt(vars, brief, userId, tb.id()), LessonDraft.class, profile);

            progress.stage("check", "Checking the quizzes, sources and reader settings");
            LessonDraft lesson = LessonSafety.links(draft.value(), profile, allowedLinks);
            LessonRules.Result result = LessonRules.apply(lesson, seq, resourceIds, random, false);
            // The safety check runs once the rules pass; a lesson rewritten for the rules is checked after the rewrite.
            Moderation.Result safety = result.ok() ? LessonSafety.check(moderation, lesson, profile, userId, tb.id()) : null;
            if (!result.ok() || !safety.passed()) {
                List<String> problems = new ArrayList<>(result.problems().stream().map(LessonRules.Problem::message).toList());
                if (safety != null && !safety.passed()) {
                    moderation.markBlocked(draft.runId(), safety.flags());
                    problems.addAll(LessonSafety.problems(safety));
                }
                log.info("Lesson {} for textbook {} failed its checks, rewriting: {}", seq, tb.id(), problems);
                progress.stage("rewrite", "Tidying up a few things");
                String fix = brief + "\n\nYour previous version had these problems:\n- " + String.join("\n- ", problems)
                        + "\n\nYour previous version, to correct:\n" + json.writeValueAsString(lesson)
                        + "\n\nReturn the whole corrected lesson.";
                draft = gateway.structuredResult(Task.LESSON_WRITE, prompt(vars, fix, userId, tb.id()), LessonDraft.class, profile);
                lesson = LessonSafety.links(draft.value(), profile, allowedLinks);
                result = LessonRules.apply(lesson, seq, resourceIds, random, true);
                if (!result.ok()) {
                    log.warn("Lesson {} for textbook {} failed its checks twice: {}", seq, tb.id(),
                            result.problems().stream().map(LessonRules.Problem::message).toList());
                    throw new ApiException(HttpStatus.BAD_GATEWAY,
                            "The lesson didn’t pass its checks, so it wasn’t saved. Please try again.");
                }
                safety = LessonSafety.check(moderation, lesson, profile, userId, tb.id());
                if (!safety.passed()) {
                    moderation.markBlocked(draft.runId(), safety.flags());
                    List<String> placeholders = new ArrayList<>();
                    lesson = LessonSafety.withPlaceholders(lesson, safety, placeholders);
                    result = LessonSafety.unrecoverable(safety) ? null : LessonRules.apply(lesson, seq, resourceIds, random, true);
                    if (result == null || !result.ok()) {
                        log.warn("Lesson {} for textbook {} didn't pass the safety check twice: {}", seq, tb.id(), safety.flags());
                        throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT,
                                "This lesson didn’t fit your reader settings, so it wasn’t saved. Please try again.");
                    }
                    log.info("Lesson {} for textbook {}: {}", seq, tb.id(), placeholders);
                }
            }
            if (!result.removed().isEmpty()) log.info("Lesson {} for textbook {}: {}", seq, tb.id(), result.removed());

            progress.stage("save", "Binding the pages");
            String missionLink = tb.missionHeadline() == null ? tb.title() : tb.missionHeadline();
            Lesson saved = lessons.insert(tb.id(), seq, lesson, missionLink, result, plan, draft.runId());
            return new Written(saved.seq(), saved.id().toString(), saved.title());
        } catch (LlmUnavailableException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The writer couldn’t be reached just now. Nothing was saved; please try again.");
        } catch (StructuredOutputException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "The lesson came back in the wrong shape twice, so it wasn’t saved. Please try again.");
        }
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
