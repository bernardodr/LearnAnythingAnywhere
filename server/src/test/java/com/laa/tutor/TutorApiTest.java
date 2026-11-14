package com.laa.tutor;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.laa.IntegrationTest;
import com.laa.llm.Provider;
import com.laa.llm.Task;
import com.laa.llm.provider.fake.FailureMode;

import tools.jackson.databind.JsonNode;

/** The tutor: grading, the hint ladder, the leak guard, evidence, and tutor chat (BRIEF 6.5 and 10; milestone M4). */
class TutorApiTest extends IntegrationTest {

    /** The fake lesson's rubric: covered by any concrete step; the second point needs a "because". */
    private static final String POINT_1 = "Names one concrete step";
    private static final String POINT_2 = "Explains how it gives quick feedback";
    private static final List<String> HINTS = List.of("Think about what you could do in ten minutes.",
            "Which part of your mission does it move forward?", "Name the step, then say what feedback it gives you.");

    record Practice(String textbookId, String lessonId, String blockId) {}

    @Test
    void aPartialAnswerGetsANoteWithoutTheAnswerThenHintsThenAMetAnswerBecomesEvidence() {
        String learner = login("tutor@test.dev");
        Practice p = practice(learner);

        // Demo step 3: a partial answer gets a tutor's note that doesn't give the rest away.
        Resp partial = attempt(learner, p, "I'd practise one small step, like a single move, ten times.");
        ok(partial);
        JsonNode note = partial.body();
        assertThat(note.get("verdict").asString()).isEqualTo("PARTIAL");
        assertThat(note.get("attemptNo").asInt()).isEqualTo(1);
        assertThat(note.get("covered").get(0).asString()).isEqualTo(POINT_1);
        assertThat(note.get("missing").asInt()).isEqualTo(1);
        assertThat(note.get("coachingMove").asString()).isEqualTo("ASK");
        assertThat(partial.raw()).doesNotContain(POINT_2, "quick feedback", "answerOutline", "leakGuard");
        assertThat(note.get("evidencePending").asBoolean()).isFalse();

        // The hint ladder, gentle to strong; answers are never revealed under the default profile.
        for (int i = 0; i < 3; i++) {
            Resp hint = post("/lessons/" + p.lessonId() + "/blocks/" + p.blockId() + "/hint", learner, null);
            ok(hint);
            assertThat(hint.body().get("hintNo").asInt()).isEqualTo(i + 1);
            assertThat(hint.body().get("text").asString()).isEqualTo(HINTS.get(i));
        }
        Resp noMore = post("/lessons/" + p.lessonId() + "/blocks/" + p.blockId() + "/hint", learner, null);
        assertThat(noMore.body().get("exhausted").asBoolean()).isTrue();
        assertThat(noMore.body().get("answer").isNull()).isTrue();
        assertThat(noMore.body().get("text").isNull()).isTrue();

        // Demo step 4: the revised answer meets every point, and a learning record appears.
        Resp met = attempt(learner, p, "I'd practise one small step because trying it straight away gives me feedback.");
        ok(met);
        assertThat(met.body().get("verdict").asString()).isEqualTo("MET");
        assertThat(met.body().get("coachingMove").asString()).isEqualTo("CONFIRM");
        assertThat(met.body().get("covered").size()).isEqualTo(2);
        assertThat(met.body().get("evidencePending").asBoolean()).isTrue();

        JsonNode learned = awaitLearned(learner, p.textbookId(), 1);
        assertThat(learned.get(0).get("title").asString()).isEqualTo("[FAKE] Explains why small steps work");
        String record = get("/textbooks/" + p.textbookId() + "/files/learning-records/0001-explains-why-small-steps-work.md", learner)
                .body().get("content").asString();
        assertThat(record).startsWith("# [FAKE] Explains why small steps work\n\n").contains("\n## Evidence\nLesson 1 practice: ");
        String glossary = get("/textbooks/" + p.textbookId() + "/contents", learner).body().get("back").get("glossary").asString();
        assertThat(glossary).startsWith("# Bouldering Glossary").contains("**Small step**:\n").contains("_Avoid_: baby step");

        // Meeting it again writes no second record.
        Resp again = attempt(learner, p, "Because it gives quick feedback, I'd practise one small step.");
        assertThat(again.body().get("evidencePending").asBoolean()).isFalse();
        assertThat(get("/textbooks/" + p.textbookId() + "/contents", learner).body().get("back").get("learned").size()).isEqualTo(1);

        // The lesson page restores it all.
        JsonNode state = get("/textbooks/" + p.textbookId() + "/lessons/1", learner).body().get("practice").get(p.blockId());
        assertThat(state.get("met").asBoolean()).isTrue();
        assertThat(state.get("hintsShown").size()).isEqualTo(3);
        assertThat(state.get("attempts").asInt()).isEqualTo(3);
        assertThat(state.get("note").get("verdict").asString()).isEqualTo("MET");
        assertThat(state.get("evidence").asString()).isEqualTo("learning-records/0001-explains-why-small-steps-work.md");
        assertThat(state.toString()).doesNotContain("leakGuard", "gradeRunId");

        var tasks = jdbc.sql("SELECT task, count(*) AS n FROM model_runs WHERE textbook_id = :t::uuid AND task IN ('GRADE', 'SAFETY_CHECK', 'RECORD_UPDATE') GROUP BY task ORDER BY task")
                .param("t", p.textbookId()).query().listOfRows();
        // 3 grades; 1 leak check (only the partial answer has anything hidden left); 1 record update.
        assertThat(tasks).containsExactly(Map.of("task", "GRADE", "n", 3L), Map.of("task", "RECORD_UPDATE", "n", 1L),
                Map.of("task", "SAFETY_CHECK", "n", 1L));
    }

    @Test
    void aMessageThatRepeatsTheHiddenAnswerIsRegeneratedThenReplacedByTheNextHint() {
        String learner = login("leak-similar@test.dev");
        Practice p = practice(learner);
        fakes.setCanned(Task.GRADE, """
                {"verdict": "PARTIAL", "coveredPoints": ["p1"], "missingPoints": ["p2"], "coachingMove": "ASK",
                 "message": "Good. Now you just need to say it explains how it gives quick feedback."}""");

        Resp r = attempt(learner, p, "One small step.");
        ok(r);
        assertThat(r.body().get("coachingMove").asString()).isEqualTo("HINT");
        assertThat(r.body().get("hintNo").asInt()).isEqualTo(1);
        assertThat(r.body().get("message").asString()).isEqualTo(HINTS.getFirst());
        assertThat(r.raw()).doesNotContain("quick feedback");

        String guard = jdbc.sql("SELECT feedback->'leakGuard' FROM attempts").query(String.class).single();
        assertThat(guard).contains("repeats").contains("hint 1");
        assertThat(jdbc.sql("SELECT count(*) FROM model_runs WHERE task = 'GRADE'").query(Long.class).single()).isEqualTo(2);
        // The hint it fell back to counts as revealed.
        assertThat(get("/textbooks/" + p.textbookId() + "/lessons/1", learner).body().get("practice").get(p.blockId())
                .get("hintsShown").size()).isEqualTo(1);
    }

    @Test
    void aParaphraseTheClassifierCatchesIsAlsoReplacedByAHint() {
        String learner = login("leak-classifier@test.dev");
        Practice p = practice(learner);
        fakes.setCanned(Task.SAFETY_CHECK, """
                {"leaks": true, "reason": "spells out the feedback point"}""");

        Resp r = attempt(learner, p, "One small step.");
        assertThat(r.body().get("coachingMove").asString()).isEqualTo("HINT");
        assertThat(r.body().get("message").asString()).isEqualTo(HINTS.getFirst());
        assertThat(jdbc.sql("SELECT feedback->'leakGuard'->>'firstCheck' FROM attempts").query(String.class).single())
                .isEqualTo("spells out the feedback point");
    }

    @Test
    void theLeakGuardFailsClosedWhenTheClassifierIsDown() {
        String learner = login("leak-down@test.dev");
        Practice p = practice(learner);
        String owner = ownerToken();
        ok(patch("/owner/routes/SAFETY_CHECK", owner, Map.of("providerChoice", "FAKE_B")));
        fakes.set(Provider.FAKE_B, FailureMode.SERVER_ERROR);

        Resp r = attempt(learner, p, "One small step.");
        ok(r);
        assertThat(r.body().get("coachingMove").asString()).isEqualTo("HINT");
        assertThat(jdbc.sql("SELECT feedback->'leakGuard'->>'firstCheck' FROM attempts").query(String.class).single())
                .contains("couldn’t be checked");
    }

    @Test
    void aGraderOutageSavesNoAttempt() {
        String learner = login("grader-down@test.dev");
        Practice p = practice(learner);
        fakes.set(Provider.FAKE, FailureMode.SERVER_ERROR);
        Resp r = attempt(learner, p, "One small step.");
        assertThat(r.status()).isEqualTo(503);
        assertThat(r.body().get("detail").asString()).contains("send it again");
        assertThat(jdbc.sql("SELECT count(*) FROM attempts").query(Long.class).single()).isZero();
    }

    @Test
    void practiceIsValidatedAndPrivate() {
        String ana = login("ana-tutor@test.dev");
        String ben = login("ben-tutor@test.dev");
        Practice p = practice(ana);
        assertThat(attempt(ana, p, "  ").status()).isEqualTo(400);
        assertThat(attempt(ana, p, "x".repeat(4001)).status()).isEqualTo(400);
        assertThat(post("/lessons/" + p.lessonId() + "/blocks/b1/attempts", ana, Map.of("answer", "hi")).status()).isEqualTo(400);
        assertThat(post("/lessons/" + p.lessonId() + "/blocks/b99/attempts", ana, Map.of("answer", "hi")).status()).isEqualTo(404);
        assertThat(attempt(ben, p, "One small step.").status()).isEqualTo(404);
        assertThat(post("/lessons/" + p.lessonId() + "/blocks/" + p.blockId() + "/hint", ben, null).status()).isEqualTo(404);
    }

    @Test
    void tutorChatStreamsAndKeepsAThreadPerLesson() {
        String learner = login("chat@test.dev");
        Practice p = practice(learner);
        String path = "/textbooks/" + p.textbookId() + "/tutor/messages";

        SseResp turn = sse(path, learner, Map.of("content", "Why do small steps work?", "lessonSeq", 1));
        ok(turn);
        assertThat(turn.text()).startsWith("[FAKE] This is a deterministic TUTOR reply");
        Resp thread = get(path + "?lessonSeq=1", learner);
        assertThat(thread.body().size()).isEqualTo(2);
        assertThat(thread.body().get(0).get("role").asString()).isEqualTo("USER");
        assertThat(thread.body().get(1).get("content").asString()).startsWith("[FAKE]");
        // The mission interview's thread is separate.
        assertThat(get("/textbooks/" + p.textbookId() + "/mission", learner).body().get("messages").size()).isEqualTo(5);

        assertThat(sse(path, learner, Map.of("content", "hi")).status()).isEqualTo(400);
        assertThat(sse(path, learner, Map.of("content", " ", "lessonSeq", 1)).status()).isEqualTo(400);
        assertThat(sse(path, learner, Map.of("content", "hi", "lessonSeq", 9)).status()).isEqualTo(404);
        assertThat(sse(path, login("someone@test.dev"), Map.of("content", "hi", "lessonSeq", 1)).status()).isEqualTo(404);

        fakes.set(Provider.FAKE, FailureMode.SERVER_ERROR);
        SseResp failed = sse(path, learner, Map.of("content", "And then?", "lessonSeq", 1));
        assertThat(failed.named("error").getFirst().data().get("message").asString()).contains("wasn’t lost");
        assertThat(get(path + "?lessonSeq=1", learner).body().size()).isEqualTo(2);
    }

    // ------------------------------------------------------------------ helpers

    private Resp attempt(String token, Practice p, String answer) {
        return post("/lessons/" + p.lessonId() + "/blocks/" + p.blockId() + "/attempts", token, Map.of("answer", answer));
    }

    /** A confirmed textbook with lesson 1 written; returns its practice block. */
    private Practice practice(String token) {
        String id = post("/textbooks", token, Map.of("topic", "Bouldering")).body().get("textbook").get("id").asString();
        String base = "/textbooks/" + id + "/mission";
        ok(sse(base + "/messages", token, null));
        ok(sse(base + "/messages", token, Map.of("content", "I want to climb a V3 with my friends by spring.")));
        ok(sse(base + "/messages", token, Map.of("content", "Twice a week at my local gym.")));
        ok(post(base + "/confirm", token, Map.of("draft", JSON.convertValue(get(base, token).body().get("draft"), Map.class))));
        String job = post("/textbooks/" + id + "/lessons/next", token, null).body().get("jobId").asString();
        ok(sseGet("/jobs/" + job + "/events", token));
        JsonNode lesson = get("/textbooks/" + id + "/lessons/1", token).body();
        String blockId = lesson.get("blocks").valueStream().filter(b -> b.get("type").asString().equals("free_response"))
                .findFirst().orElseThrow().get("id").asString();
        return new Practice(id, lesson.get("id").asString(), blockId);
    }

    /** Evidence is written in the background; wait for it. */
    private JsonNode awaitLearned(String token, String textbookId, int count) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(deadline)) {
            JsonNode learned = get("/textbooks/" + textbookId + "/contents", token).body().get("back").get("learned");
            if (learned.size() >= count) return learned;
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        throw new AssertionError("No learning record appeared within 10s");
    }
}
