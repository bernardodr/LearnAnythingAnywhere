package com.laa.lesson;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import com.laa.IntegrationTest;
import com.laa.lesson.LessonChecks.Attempt;
import com.laa.lesson.LessonChecks.Pass;
import com.laa.lesson.LessonChecks.Problem;
import com.laa.lesson.LessonChecks.Refuse;
import com.laa.lesson.LessonChecks.Rewrite;
import com.laa.lesson.LessonChecks.Verdict;
import com.laa.lesson.LessonDraft.Block;
import com.laa.lesson.LessonDraft.ReferenceSheet;
import com.laa.lesson.LessonDraft.Rubric;
import com.laa.lesson.LessonDraft.Type;
import com.laa.llm.provider.fake.FakeBehaviour;
import com.laa.safety.Moderation;

/**
 * Lesson checks, branch by branch (BRIEF sections 7 and 8): the lesson rules, the links rule and the safety check on a
 * draft, and what happens next. Drafts are built by hand and judged directly; the practice provider's safety check
 * blocks anything containing {@link FakeBehaviour#BLOCK_MARKER}.
 */
class LessonChecksTest extends IntegrationTest {

    private static final String BLOCK = FakeBehaviour.BLOCK_MARKER;

    @Autowired
    LessonChecks checks;

    // ------------------------------------------------------------------ first attempt

    @Test
    void aCleanDraftPassesAndLesson1DropsItsWarmUp() {
        Verdict v = checks.check(draft(), Attempt.FIRST, learner().about());
        Pass pass = (Pass) v;
        assertThat(pass.lesson().result().blocks()).extracting(b -> b.get("type"))
                .containsExactly("heading", "text", "key_idea", "quiz", "free_response");
        assertThat(pass.lesson().result().blocks()).noneMatch(b -> "warmup".equals(b.get("variant")));
        assertThat(pass.lesson().changes()).anyMatch(c -> c.contains("warm-up dropped"));
        assertThat(pass.lesson().result().marking()).containsOnlyKeys("b5");
    }

    @Test
    void aRuleProblemAsksForARewrite() {
        Verdict v = checks.check(edit(draft(), "q1", b -> withItems(b, List.of("Small steps", "Big leaps"))), Attempt.FIRST, learner().about());
        List<Problem> problems = ((Rewrite) v).problems();
        assertThat(problems).singleElement().satisfies(p -> {
            assertThat(p.kind()).isEqualTo(Problem.Kind.LESSON_RULES);
            assertThat(p.message()).contains("3 or 4 options");
        });
    }

    @Test
    void aBlockThatDoesntFitAsksForARewriteAndItsRunIsLogged() {
        Learner a = learner();
        Verdict v = checks.check(edit(draft(), "k1", b -> withText(b, "Small steps beat leaps. " + BLOCK)), Attempt.FIRST, a.about());
        Problem p = ((Rewrite) v).problems().getFirst();
        assertThat(p.kind()).isEqualTo(Problem.Kind.READER_SETTINGS);
        assertThat(p.target()).isEqualTo("k1");
        assertThat(p.message()).contains("Block k1", "test block");
        assertThat(status(a.runId())).isEqualTo("BLOCKED");
    }

    @Test
    void safetyIsCheckedEvenWhenTheRulesAlreadyFailSoOneRewriteFixesBoth() {
        LessonDraft d = edit(edit(draft(), "q1", b -> withItems(b, List.of("Small steps", "Big leaps"))),
                "k1", b -> withText(b, "Small steps beat leaps. " + BLOCK));
        List<Problem> problems = ((Rewrite) checks.check(d, Attempt.FIRST, learner().about())).problems();
        assertThat(problems).extracting(Problem::kind)
                .containsExactlyInAnyOrder(Problem.Kind.LESSON_RULES, Problem.Kind.READER_SETTINGS);
    }

    // ------------------------------------------------------------------ final attempt

    @Test
    void onTheFinalAttemptAQuizThatStillBreaksTheRulesIsDropped() {
        List<Block> blocks = new ArrayList<>(draft().blocks());
        blocks.add(4, quiz("q2", "check", List.of("Yes", "No")));
        Pass pass = (Pass) checks.check(withBlocks(draft(), blocks), Attempt.FINAL, learner().about());
        assertThat(pass.lesson().changes()).anyMatch(c -> c.contains("q2") && c.contains("dropped"));
        assertThat(pass.lesson().result().blocks()).filteredOn(b -> "quiz".equals(b.get("type"))).hasSize(1);
    }

    @Test
    void onTheFinalAttemptABlockThatStillDoesntFitBecomesAPlaceholder() {
        Learner a = learner();
        Pass pass = (Pass) checks.check(edit(draft(), "k1", b -> withText(b, "Small steps beat leaps. " + BLOCK)), Attempt.FINAL, a.about());
        assertThat(pass.lesson().result().blocks()).anySatisfy(b -> {
            assertThat(b.get("type")).isEqualTo("callout");
            assertThat(b.get("text")).isEqualTo(Moderation.PLACEHOLDER);
        });
        assertThat(pass.lesson().changes()).anyMatch(c -> c.contains("k1") && c.contains("placeholder"));
        assertThat(status(a.runId())).isEqualTo("BLOCKED");
    }

    @Test
    void aTitleThatDoesntFitIsRefusedForTheReaderSettings() {
        LessonDraft d = draft();
        d = new LessonDraft("Small steps " + BLOCK, d.subtitle(), d.win(), d.blocks(), d.referenceSheet(), d.rubrics());
        Refuse refuse = (Refuse) checks.check(d, Attempt.FINAL, learner().about());
        assertThat(refuse.error().status()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(refuse.error().getMessage()).contains("reader settings");
    }

    @Test
    void aLessonThatStillBreaksTheRulesIsRefusedForTheRules() {
        List<Block> noPractice = draft().blocks().stream().filter(b -> b.type() != Type.FREE_RESPONSE).toList();
        Refuse refuse = (Refuse) checks.check(withBlocks(draft(), noPractice), Attempt.FINAL, learner().about());
        assertThat(refuse.error().status()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(refuse.problems()).anyMatch(p -> p.message().contains("FREE_RESPONSE"));
    }

    @Test
    void aPlaceholderThatBreaksTheLessonIsRefusedForTheReaderSettings() {
        LessonDraft d = edit(draft(), "f1", b -> withText(b, "Describe your step. " + BLOCK));
        Refuse refuse = (Refuse) checks.check(d, Attempt.FINAL, learner().about());
        assertThat(refuse.error().status()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    // ------------------------------------------------------------------ links

    @Test
    void linksFollowTheLearnersSettings() {
        Learner a = learner();
        String profile = get("/profiles/me", a.token()).body().get("id").asString();
        Map<String, Object> off = new HashMap<>();
        off.put("childSafe", false);
        off.put("linksPolicy", "OFF");
        ok(put("/profiles/" + profile, a.token(), off));

        Pass pass = (Pass) checks.check(edit(draft(), "t1", b -> withText(b, "See https://elsewhere.example.com for more.")),
                Attempt.FIRST, a.about());
        assertThat(pass.lesson().result().blocks().toString()).doesNotContain("elsewhere.example.com").contains("See for more.");
    }

    // ------------------------------------------------------------------ helpers

    /** A learner, their textbook and a lesson-writing run to log against. */
    private record Learner(LessonChecks.About about, String token, UUID runId) {}

    private Learner learner() {
        String email = "checks-" + UUID.randomUUID().toString().substring(0, 8) + "@test.dev";
        String token = login(email);
        String id = post("/textbooks", token, Map.of("topic", "Bouldering")).body().get("textbook").get("id").asString();
        UUID userId = UUID.fromString(get("/profiles/me", token).body().get("userId").asString());
        UUID runId = jdbc.sql("INSERT INTO model_runs (task, provider, model, latency_ms, status, call) "
                        + "VALUES ('LESSON_WRITE', 'FAKE', 'fake-large', 1, 'OK', 'LESSON_WRITE') RETURNING id")
                .query(UUID.class).single();
        return new Learner(new LessonChecks.About(1, Set.of(), userId, UUID.fromString(id), runId), token, runId);
    }

    private String status(UUID runId) {
        return jdbc.sql("SELECT status FROM model_runs WHERE id = :id").param("id", runId).query(String.class).single();
    }

    private static final List<String> HINTS = List.of("Think about ten minutes.", "Which part of your mission?", "Name the step.");

    /** A lesson that passes every rule (with a warm-up, which lesson 1 drops). */
    private static LessonDraft draft() {
        List<Block> blocks = List.of(
                quiz("w1", "warmup", List.of("The first idea we met", "The second idea we met", "The third idea we met")),
                new Block("h1", Type.HEADING, "", "Where to begin", List.of(), -1, "", List.of()),
                new Block("t1", Type.TEXT, "", "Start with one small step you can repeat.", List.of(), -1, "", List.of()),
                new Block("k1", Type.KEY_IDEA, "", "Small, repeated steps beat one big leap.", List.of(), -1, "", List.of()),
                quiz("q1", "check", List.of("Practise it in small steps", "Read about it once more", "Skip it and hope for luck")),
                new Block("f1", Type.FREE_RESPONSE, "", "Describe the small step you’ll take and why it helps.", HINTS, -1, "", List.of()));
        return new LessonDraft("Your next small step", "Why the smallest step is fastest.", "Take one small step.", blocks,
                new ReferenceSheet("Small steps", "- Pick one step\n- Repeat it"),
                List.of(new Rubric("f1", List.of("Names one concrete step", "Explains the quick feedback"), "A small action, and why.")));
    }

    private static Block quiz(String id, String style, List<String> options) {
        return new Block(id, Type.QUIZ, style, "What is the best way to start?", options, 0, "Small steps give quick feedback.", List.of());
    }

    private static LessonDraft edit(LessonDraft d, String id, UnaryOperator<Block> change) {
        return withBlocks(d, d.blocks().stream().map(b -> b.id().equals(id) ? change.apply(b) : b).toList());
    }

    private static LessonDraft withBlocks(LessonDraft d, List<Block> blocks) {
        return new LessonDraft(d.title(), d.subtitle(), d.win(), blocks, d.referenceSheet(), d.rubrics());
    }

    private static Block withText(Block b, String text) {
        return new Block(b.id(), b.type(), b.style(), text, b.items(), b.answer(), b.explanation(), b.refs());
    }

    private static Block withItems(Block b, List<String> items) {
        return new Block(b.id(), b.type(), b.style(), b.text(), items, b.answer(), b.explanation(), b.refs());
    }
}
