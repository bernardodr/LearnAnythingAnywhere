package com.laa.tutor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.laa.lesson.LessonRepository.Lesson;
import com.laa.lesson.LessonRules.Marking;
import com.laa.llm.Calls;
import com.laa.llm.GenerationProfile;
import com.laa.llm.LlmUnavailableException;
import com.laa.llm.PromptContext;
import com.laa.llm.StructuredOutputException;
import com.laa.llm.runs.Origin;
import com.laa.safety.LeakCheck;
import com.laa.safety.SafetyLayer;
import com.laa.security.SecretRedactor;
import com.laa.textbook.Textbook;
import com.laa.tutor.GradeResult.CoachingMove;
import com.laa.tutor.GradeResult.Verdict;
import com.laa.tutor.PracticeRepository.Attempt;
import com.laa.tutor.PracticeRepository.Progress;
import com.laa.web.ApiException;
import com.laa.workspace.Glossary;
import com.laa.workspace.LearningRecords;
import com.laa.workspace.TeachingContext;
import com.laa.workspace.WorkspaceFiles;
import com.laa.workspace.WorkspaceFiles.WorkspaceFile;

/**
 * Free-response practice (BRIEF section 6.5): grade an answer against the hidden rubric, coach without giving the
 * answer away, serve the hint ladder, and turn a met answer into evidence.
 *
 * <p><b>Grading.</b> GRADE refers to rubric points by id (p1, p2, …). The verdict is recomputed from which points
 * are covered, so it can't contradict them. The learner sees covered points in full (they wrote them) and only
 * the number still to find, never the hidden text.
 *
 * <p><b>Safety</b> (BRIEF section 8). Everything goes through the {@link SafetyLayer}: the learner's answer is checked
 * before it's graded (one it refuses isn't graded or stored), and the grader's coaching message comes back with the
 * links policy applied and checked. A message that doesn't pass is replaced by the next pre-written hint (hints were
 * checked with the lesson).
 *
 * <p><b>Leak guard.</b> A coaching message is also checked against the missing points and the answer outline
 * ({@link LeakCheck}). A leak gets one regeneration with the reason; if that leaks too, the learner gets the next
 * pre-written hint instead. If the check can't run, it fails closed and uses the hint.
 *
 * <p><b>Evidence.</b> The first time a block is met, RECORD_UPDATE runs in the background and may write a learning
 * record and glossary terms in the /teach formats. Covering material alone never does. It's shown at the back of the
 * book, so it goes through the safety layer too; evidence that doesn't pass isn't written.
 */
@Service
public class Practice {

    private static final Logger log = LoggerFactory.getLogger(Practice.class);
    public static final int MAX_ANSWER_CHARS = 4_000;
    private static final String MET_FALLBACK = "Well done: your answer covers everything this question asks for.";
    private static final String NO_HINT_LEFT =
            "You’re on the right track. Look again at what the question asks, and add the part your answer doesn’t cover yet.";

    /** The tutor's note as the learner sees it (BRIEF screen 6). */
    public record Note(int attemptNo, Verdict verdict, List<String> covered, int missing, CoachingMove coachingMove,
                       String message, Integer hintNo, boolean evidencePending) {}

    public record HintView(int hintNo, int total, String text, String answer, boolean exhausted) {}

    private final SafetyLayer safety;
    private final LeakCheck leaks;
    private final PracticeRepository practice;
    private final TeachingContext teaching;
    private final WorkspaceFiles workspace;
    private final ExecutorService executor;

    Practice(SafetyLayer safety, LeakCheck leaks, PracticeRepository practice, TeachingContext teaching, WorkspaceFiles workspace,
             @Qualifier("llmExecutor") ExecutorService executor) {
        this.safety = safety;
        this.leaks = leaks;
        this.practice = practice;
        this.teaching = teaching;
        this.workspace = workspace;
        this.executor = executor;
    }

    // ------------------------------------------------------------------ grading

    public Note attempt(Textbook tb, Lesson lesson, String blockId, UUID userId, String rawAnswer) {
        Map<String, Object> block = freeResponse(lesson, blockId);
        Marking marking = lesson.marking().get(blockId);
        String answer = rawAnswer == null ? "" : rawAnswer.strip();
        if (answer.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Write an answer first.");
        if (answer.length() > MAX_ANSWER_CHARS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please keep answers under " + MAX_ANSWER_CHARS + " characters.");
        }
        safety.admit(userId, tb.id(), answer);

        Map<String, String> points = pointIds(marking);
        List<Attempt> earlier = practice.attempts(lesson.id(), blockId, userId);
        Progress progress = practice.progress(lesson.id(), blockId, userId);
        Map<String, String> vars = TeachingContext.with(teaching.load(tb.id(), tb.title()).variables(), Map.of(
                "lesson_title", lesson.title(),
                "skill", lesson.plan().skill(),
                "prompt", String.valueOf(block.get("prompt")),
                "rubric", formatPoints(points),
                "answer_outline", marking.answerOutline(),
                "earlier_attempts", formatEarlier(earlier),
                "hints_shown", formatHints(marking.hints(), progress.hintsRevealed())));
        String learnerMessage = "Attempt " + (earlier.size() + 1) + ". The learner's answer:\n" + answer;

        Map<String, Object> guardLog = new LinkedHashMap<>();
        String prompt = String.valueOf(block.get("prompt"));
        Graded graded = grade(vars, learnerMessage, null, points, tb, userId);
        boolean leaked = false;
        if (!graded.blocked() && graded.verdict() != Verdict.MET) {
            Optional<String> leak = leaks.leaks(graded.message(), secrets(points, graded.missing(), marking), answer, prompt, userId, tb.id());
            guardLog.put("firstCheck", leak.orElse("clean"));
            if (leak.isPresent()) {
                String correction = "\n\nYour previous coaching message gave the answer away (" + leak.get() + "). Write the "
                        + "message again so it guides with a question or a nudge, without stating any missing point or the answer.";
                graded = grade(vars, learnerMessage, correction, points, tb, userId);
                if (!graded.blocked() && graded.verdict() != Verdict.MET) {
                    Optional<String> again = leaks.leaks(graded.message(), secrets(points, graded.missing(), marking), answer,
                            prompt, userId, tb.id());
                    guardLog.put("secondCheck", again.orElse("clean"));
                    leaked = again.isPresent();
                }
            }
        }
        Integer hintNo = null;
        if (leaked || graded.blocked()) { // the grader's words can't be shown: a pre-written hint instead
            if (graded.blocked()) guardLog.put("safety", "blocked");
            Fallback f = fallBack(lesson, blockId, userId, marking, progress, graded);
            graded = f.graded();
            hintNo = f.hintNo();
            guardLog.put("fellBackTo", f.what());
        }

        boolean firstMet = graded.verdict() == Verdict.MET && practice.markMet(lesson.id(), blockId, userId);
        List<String> coveredText = graded.covered().stream().map(points::get).toList();
        Map<String, Object> feedback = new LinkedHashMap<>();
        feedback.put("verdict", graded.verdict().name());
        feedback.put("covered", coveredText);
        feedback.put("missing", graded.missing().size());
        feedback.put("coachingMove", graded.coachingMove().name());
        feedback.put("message", graded.message());
        if (hintNo != null) feedback.put("hintNo", hintNo);
        feedback.put("leakGuard", guardLog);
        feedback.put("gradeRunId", String.valueOf(graded.runId()));
        Attempt saved = practice.insertAttempt(lesson.id(), blockId, userId, answer, graded.verdict().name(), feedback);

        if (firstMet) {
            executor.execute(() -> writeEvidence(tb, lesson, blockId, userId, answer, points));
        }
        return new Note(saved.attemptNo(), graded.verdict(), coveredText, graded.missing().size(), graded.coachingMove(),
                graded.message(), hintNo, firstMet);
    }

    /** {@code blocked}: the safety layer held the message back (it's the placeholder now). */
    private record Graded(Verdict verdict, List<String> covered, List<String> missing, CoachingMove coachingMove,
                          String message, UUID runId, boolean blocked) {}

    private record Fallback(Graded graded, Integer hintNo, String what) {}

    /** The coaching message can't be shown: the next pre-written hint replaces it (or a general nudge, if none is left). */
    private Fallback fallBack(Lesson lesson, String blockId, UUID userId, Marking marking, Progress progress, Graded graded) {
        if (graded.verdict() == Verdict.MET) {
            return new Fallback(new Graded(graded.verdict(), graded.covered(), graded.missing(), CoachingMove.CONFIRM,
                    MET_FALLBACK, graded.runId(), false), null, "standard well done");
        }
        int revealed = practice.revealHint(lesson.id(), blockId, userId, marking.hints().size());
        boolean newHint = revealed > progress.hintsRevealed();
        String text = newHint ? marking.hints().get(revealed - 1) : NO_HINT_LEFT;
        return new Fallback(new Graded(graded.verdict(), graded.covered(), graded.missing(), CoachingMove.HINT, text, graded.runId(), false),
                newHint ? revealed : null, newHint ? "hint " + revealed : "generic nudge");
    }

    private Graded grade(Map<String, String> vars, String learnerMessage, String correction, Map<String, String> points,
                         Textbook tb, UUID userId) {
        PromptContext ctx = new PromptContext(vars, List.of(), learnerMessage + (correction == null ? "" : correction),
                userId, tb.id(), Origin.APP);
        SafetyLayer.Checked<GradeResult> result;
        try {
            result = safety.write(Calls.GRADE, ctx);
        } catch (LlmUnavailableException | StructuredOutputException e) {
            log.warn("Grading failed: {}", SecretRedactor.redact(e.getMessage()));
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Your tutor couldn’t read your answer just now. Nothing was lost: send it again.");
        }
        GradeResult g = result.value();
        // Covered wins over missing; unknown ids are dropped; anything unmentioned counts as missing.
        List<String> covered = new ArrayList<>();
        for (String id : g.coveredPoints()) if (points.containsKey(norm(id)) && !covered.contains(norm(id))) covered.add(norm(id));
        List<String> missing = new ArrayList<>();
        for (String id : points.keySet()) if (!covered.contains(id)) missing.add(id);
        Verdict verdict = missing.isEmpty() ? Verdict.MET : covered.isEmpty() ? Verdict.NOT_YET : Verdict.PARTIAL;
        CoachingMove move = verdict == Verdict.MET ? CoachingMove.CONFIRM
                : g.coachingMove() == CoachingMove.CONFIRM ? CoachingMove.ASK : g.coachingMove();
        return new Graded(verdict, covered, missing, move, g.message().strip(), result.runId(), result.blocked());
    }

    // ------------------------------------------------------------------ hints

    /**
     * The next hint on the ladder. Once every hint is shown, the answer outline is revealed only if the learner's
     * profile allows it after this many attempts (reveal_after_attempts; null = never).
     */
    public HintView hint(Lesson lesson, String blockId, UUID userId, GenerationProfile profile) {
        freeResponse(lesson, blockId);
        Marking marking = lesson.marking().get(blockId);
        int total = marking.hints().size();
        Progress progress = practice.progress(lesson.id(), blockId, userId);
        if (progress.hintsRevealed() < total) {
            int n = practice.revealHint(lesson.id(), blockId, userId, total);
            return new HintView(n, total, marking.hints().get(n - 1), null, n >= total);
        }
        int attempts = practice.attempts(lesson.id(), blockId, userId).size();
        if (mayRevealAnswer(profile, attempts)) {
            practice.revealAnswer(lesson.id(), blockId, userId);
            return new HintView(total, total, null, marking.answerOutline(), true);
        }
        return new HintView(total, total, null, null, true);
    }

    static boolean mayRevealAnswer(GenerationProfile profile, int attempts) {
        Integer after = profile == null ? null : profile.revealAfterAttempts();
        return after != null && attempts >= after;
    }

    /** What the lesson page needs to restore a practice block: revealed hints, the latest note, met. */
    /** {@code revealAfter}: the learner's reveal-after-attempts setting (null = never), so the page can offer the answer. */
    public Map<String, Object> state(Lesson lesson, String blockId, UUID userId, Integer revealAfter) {
        Marking marking = lesson.marking().get(blockId);
        Progress progress = practice.progress(lesson.id(), blockId, userId);
        List<Attempt> attempts = practice.attempts(lesson.id(), blockId, userId);
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("hintsShown", marking == null ? List.of() : marking.hints().subList(0, Math.min(progress.hintsRevealed(), marking.hints().size())));
        s.put("hintCount", marking == null ? 0 : marking.hints().size());
        s.put("answer", progress.answerRevealed() && marking != null ? marking.answerOutline() : null);
        s.put("met", progress.metAt() != null);
        s.put("evidence", progress.evidencePath());
        s.put("attempts", attempts.size());
        s.put("revealAfter", revealAfter);
        if (!attempts.isEmpty()) {
            Attempt last = attempts.getLast();
            Map<String, Object> note = new LinkedHashMap<>(last.feedback());
            note.remove("leakGuard");
            note.remove("gradeRunId");
            note.put("attemptNo", last.attemptNo());
            s.put("lastAnswer", last.answer());
            s.put("note", note);
        }
        return s;
    }

    // ------------------------------------------------------------------ evidence

    private void writeEvidence(Textbook tb, Lesson lesson, String blockId, UUID userId, String answer,
                               Map<String, String> points) {
        try {
            Map<String, Object> block = freeResponse(lesson, blockId);
            List<String> recordTitles = workspace.list(tb.id()).stream().filter(p -> p.startsWith("learning-records/")).toList();
            Map<String, String> vars = TeachingContext.with(teaching.load(tb.id(), tb.title()).variables(), Map.of(
                    "lesson_title", lesson.title(),
                    "skill", lesson.plan().skill(),
                    "prompt", String.valueOf(block.get("prompt")),
                    "rubric", formatPoints(points),
                    "existing_records", recordTitles.isEmpty() ? "None yet." : String.join("\n", recordTitles)));
            PromptContext ctx = new PromptContext(vars, List.of(), "The learner's answer, which met every point:\n" + answer,
                    userId, tb.id(), Origin.APP);
            SafetyLayer.Checked<RecordUpdate> checked = safety.write(Calls.RECORD_UPDATE, ctx);
            if (checked.blocked()) {
                log.info("Evidence for lesson {} block {} was held back by the safety check", lesson.seq(), blockId);
                return;
            }
            RecordUpdate update = checked.value();

            if (Boolean.TRUE.equals(update.writeRecord()) && !update.title().isBlank() && !update.body().isBlank()) {
                String path = LearningRecords.nextPath(workspace.list(tb.id()), update.slug().isBlank() ? update.title() : update.slug());
                String evidence = "Lesson " + lesson.seq() + " practice: " + (update.evidence().isBlank()
                        ? "met every point of the rubric." : update.evidence());
                workspace.write(tb.id(), path, LearningRecords.render(update.title(), update.body(), evidence));
                practice.setEvidence(lesson.id(), blockId, userId, path);
            }
            if (!update.glossary().isEmpty()) {
                String current = workspace.read(tb.id(), "GLOSSARY.md").map(WorkspaceFile::content).orElse(null);
                List<Glossary.Term> terms = update.glossary().stream()
                        .map(t -> new Glossary.Term(t.term(), t.definition(), t.avoid())).toList();
                workspace.write(tb.id(), "GLOSSARY.md", Glossary.upsert(current, tb.title(), terms));
            }
        } catch (RuntimeException e) {
            log.warn("Evidence for lesson {} block {} wasn't written: {}", lesson.seq(), blockId,
                    SecretRedactor.redact(String.valueOf(e.getMessage())));
        }
    }

    // ------------------------------------------------------------------ helpers

    private static Map<String, Object> freeResponse(Lesson lesson, String blockId) {
        Map<String, Object> block = lesson.blocks().stream().filter(b -> blockId.equals(b.get("id"))).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No such block"));
        if (!"free_response".equals(block.get("type")) || !lesson.marking().containsKey(blockId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only practice questions are sent to the tutor.");
        }
        return block;
    }

    /** Rubric points as p1, p2, … in order. */
    static Map<String, String> pointIds(Marking marking) {
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i < marking.points().size(); i++) out.put("p" + (i + 1), marking.points().get(i));
        return out;
    }

    private static List<String> secrets(Map<String, String> points, List<String> missing, Marking marking) {
        List<String> out = new ArrayList<>();
        for (String id : missing) out.add(points.get(id));
        out.add(marking.answerOutline());
        return out;
    }

    private static String formatPoints(Map<String, String> points) {
        StringBuilder out = new StringBuilder();
        points.forEach((id, text) -> out.append("- ").append(id).append(": ").append(text).append('\n'));
        return out.toString().strip();
    }

    private static String formatEarlier(List<Attempt> earlier) {
        if (earlier.isEmpty()) return "None: this is the first attempt.";
        StringBuilder out = new StringBuilder();
        for (Attempt a : earlier.subList(Math.max(0, earlier.size() - 3), earlier.size())) {
            out.append("- Attempt ").append(a.attemptNo()).append(" (").append(a.verdict()).append("): ")
                    .append(a.answer().replaceAll("\\s+", " ")).append("\n  Your note: ").append(a.feedback().get("message")).append('\n');
        }
        return out.toString().strip();
    }

    private static String formatHints(List<String> hints, int revealed) {
        if (revealed == 0) return "None yet.";
        return "- " + String.join("\n- ", hints.subList(0, Math.min(revealed, hints.size())));
    }

    private static String norm(String id) {
        return id == null ? "" : id.strip().toLowerCase(java.util.Locale.ROOT);
    }
}
