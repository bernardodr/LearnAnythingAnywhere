package com.laa.lesson;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.laa.IntegrationTest;
import com.laa.llm.Calls;
import com.laa.llm.Provider;
import com.laa.llm.provider.fake.FailureMode;

import tools.jackson.databind.JsonNode;

/** Lessons: plan, write, check, store, read; the contents page and the back of the book (BRIEF 6.4, 7, 10; milestone M3). */
class LessonApiTest extends IntegrationTest {

    @Test
    void lessonsAreWrittenAsJobsReadInOrderAndKeepTheirSecretsServerSide() {
        String learner = login("lessons@test.dev");
        String id = activeTextbook(learner, "Bouldering");

        // Lesson 1, as a job with progress events.
        SseResp job = writeNext(learner, id);
        assertThat(job.named("progress")).extracting(e -> e.data().get("stage").asString())
                .containsSubsequence("context", "plan", "write", "check", "draw", "save");
        JsonNode done = job.named("done").getFirst().data();
        assertThat(done.get("seq").asInt()).isEqualTo(1);
        assertThat(job.events().getLast().name()).isEqualTo("done");

        Resp lesson = get("/textbooks/" + id + "/lessons/1", learner);
        ok(lesson);
        JsonNode l = lesson.body();
        assertThat(l.get("kicker").asString()).isEqualTo("Lesson 1 · Bouldering");
        assertThat(l.get("missionLink").asString()).isNotBlank();
        assertThat(l.get("referenceSheet").get("title").asString()).isEqualTo("Small steps");
        List<String> types = l.get("blocks").valueStream().map(b -> b.get("type").asString()).toList();
        assertThat(types).containsExactly("heading", "text", "diagram", "key_idea", "callout", "quiz", "steps", "free_response", "ask_tutor");
        // The diagram arrives drawn: its page, title, text version and caption. Its brief stays on the server.
        JsonNode diagram = l.get("blocks").get(2);
        assertThat(diagram.get("title").asString()).endsWith("The small-step loop");
        assertThat(diagram.get("html").asString()).contains("<svg", "laa-button").doesNotContain("<html");
        assertThat(diagram.get("text").asString()).contains("**Pick**");
        assertThat(diagram.get("caption").asString()).endsWith("Press Next to walk round the loop one stage at a time.");
        assertThat(lesson.raw()).doesNotContain("A step-through of the small-step loop");
        JsonNode quiz = l.get("blocks").valueStream().filter(b -> b.get("type").asString().equals("quiz")).findFirst().orElseThrow();
        assertThat(quiz.get("options").get(quiz.get("answer").asInt()).asString()).isEqualTo("Practise it in small steps");
        assertThat(l.get("prev").isNull()).isTrue();
        assertThat(l.get("next").isNull()).isTrue();
        // Rubrics and hints never leave the server.
        assertThat(lesson.raw()).doesNotContain("answerOutline", "Names one concrete step", "Think about what you could do", "\"hints\"");
        assertThat(l.get("blocks").get(7).get("hintCount").asInt()).isEqualTo(3);

        Resp contents = get("/textbooks/" + id + "/contents", learner);
        ok(contents);
        assertThat(contents.body().get("lessons").get(0).get("read").asBoolean()).isFalse();
        assertThat(contents.body().get("next").get("seq").asInt()).isEqualTo(2);
        assertThat(contents.body().get("next").get("writtenAfter").asInt()).isEqualTo(1);
        assertThat(contents.body().get("next").get("canWrite").asBoolean()).isFalse();
        assertThat(contents.body().get("missionWhy").asString()).isNotBlank();
        assertThat(contents.body().get("back").get("referenceSheets").size()).isEqualTo(1);
        assertThat(contents.body().get("back").get("sources").size()).isZero();
        assertThat(contents.body().get("back").get("glossary").isNull()).isTrue();
        assertThat(contents.raw()).doesNotContain("answerOutline", "Names one concrete step");

        // The next lesson waits until this one is finished.
        Resp tooSoon = post("/textbooks/" + id + "/lessons/next", learner, null);
        assertThat(tooSoon.status()).isEqualTo(409);
        assertThat(tooSoon.body().get("detail").asString()).contains("Finish lesson 1");
        ok(post("/lessons/" + l.get("id").asString() + "/read", learner, null));
        assertThat(get("/textbooks/" + id + "/contents", learner).body().get("next").get("canWrite").asBoolean()).isTrue();

        // A source the learner adds becomes citable in the next lesson.
        Resp source = post("/textbooks/" + id + "/resources", learner,
                Map.of("title", "Lattice Training: Footwork", "url", "https://example.org/footwork", "note", "Use for: footwork drills."));
        assertThat(source.status()).isEqualTo(201);
        assertThat(source.body().get(0).get("id").asString()).isEqualTo("r1");
        assertThat(get("/textbooks/" + id + "/files/RESOURCES.md", learner).body().get("content").asString())
                .isEqualTo("# Bouldering Resources\n\n## Knowledge\n\n- [Lattice Training: Footwork](https://example.org/footwork)\n"
                        + "  Use for: footwork drills.\n");

        ok(writeNext(learner, id));
        JsonNode second = get("/textbooks/" + id + "/lessons/2", learner).body();
        JsonNode first = second.get("blocks").get(0);
        assertThat(first.get("type").asString()).isEqualTo("quiz");
        assertThat(first.get("variant").asString()).isEqualTo("warmup");
        assertThat(second.get("blocks").valueStream().filter(b -> b.get("type").asString().equals("text")).findFirst().orElseThrow()
                .get("citations").get(0).asString()).isEqualTo("r1");
        assertThat(second.get("blocks").valueStream().anyMatch(b -> b.get("type").asString().equals("primary_source")
                && b.get("resource").asString().equals("r1"))).isTrue();
        assertThat(second.get("sources").get(0).get("url").asString()).isEqualTo("https://example.org/footwork");
        assertThat(second.get("prev").asInt()).isEqualTo(1);
        assertThat(get("/textbooks/" + id + "/lessons/1", learner).body().get("next").asInt()).isEqualTo(2);

        // Metered against the textbook: one plan and one write per lesson.
        var runs = jdbc.sql("SELECT task, count(*) AS n FROM model_runs WHERE textbook_id = :t::uuid "
                        + "AND task IN ('LESSON_PLAN', 'LESSON_WRITE') GROUP BY task ORDER BY task")
                .param("t", id).query().listOfRows();
        assertThat(runs).containsExactly(Map.of("task", "LESSON_PLAN", "n", 2L), Map.of("task", "LESSON_WRITE", "n", 2L));
    }

    @Test
    void aLateSubscriberGetsTheWholeJobReplayed() {
        String learner = login("replay@test.dev");
        String id = activeTextbook(learner, "Chess");
        String jobId = post("/textbooks/" + id + "/lessons/next", learner, null).body().get("jobId").asString();
        SseResp live = sseGet("/jobs/" + jobId + "/events", learner);
        SseResp replay = sseGet("/jobs/" + jobId + "/events", learner);
        assertThat(replay.events()).extracting(SseEvent::name).isEqualTo(live.events().stream().map(SseEvent::name).toList());
        assertThat(replay.named("done")).hasSize(1);

        assertThat(sseGet("/jobs/" + jobId + "/events", login("someone-else@test.dev")).status()).isEqualTo(404);
    }

    @Test
    void aQuizThatKeepsBreakingTheRulesIsDroppedAfterOneRewrite() {
        String learner = login("rewrite@test.dev");
        String id = activeTextbook(learner, "Bouldering");
        fakes.setCanned(Calls.LESSON_WRITE, cannedLesson(true, true));

        SseResp job = writeNext(learner, id);
        assertThat(job.named("progress")).extracting(e -> e.data().get("stage").asString()).contains("rewrite");
        assertThat(job.named("done")).hasSize(1);
        JsonNode blocks = get("/textbooks/" + id + "/lessons/1", learner).body().get("blocks");
        assertThat(blocks.valueStream().filter(b -> b.get("type").asString().equals("quiz")).count()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM model_runs WHERE task = 'LESSON_WRITE'").query(Long.class).single()).isEqualTo(2);
    }

    @Test
    void aLessonThatFailsItsChecksTwiceIsNotSaved() {
        String learner = login("broken@test.dev");
        String id = activeTextbook(learner, "Bouldering");
        fakes.setCanned(Calls.LESSON_WRITE, cannedLesson(false, false)); // no practice block at all

        SseResp job = writeNext(learner, id);
        assertThat(job.named("done")).isEmpty();
        assertThat(job.named("error").getFirst().data().get("message").asString()).contains("didn’t pass its checks");
        assertThat(get("/textbooks/" + id + "/lessons/1", learner).status()).isEqualTo(404);
        assertThat(get("/textbooks/" + id + "/contents", learner).body().get("next").get("canWrite").asBoolean()).isTrue();
    }

    @Test
    void aWriterOutageSavesNothingAndSaysSo() {
        String learner = login("outage@test.dev");
        String id = activeTextbook(learner, "Bouldering");
        fakes.set(Provider.FAKE, FailureMode.SERVER_ERROR);
        SseResp job = writeNext(learner, id);
        assertThat(job.named("error").getFirst().data().get("message").asString()).contains("couldn’t be reached");
        fakes.reset();
        assertThat(get("/textbooks/" + id + "/contents", learner).body().get("lessons").size()).isZero();
    }

    @Test
    void lessonsBelongToTheirLearnerAndNeedAConfirmedMission() {
        String ana = login("ana-lessons@test.dev");
        String ben = login("ben-lessons@test.dev");
        String interview = post("/textbooks", ana, Map.of("topic", "Chess")).body().get("textbook").get("id").asString();
        assertThat(post("/textbooks/" + interview + "/lessons/next", ana, null).status()).isEqualTo(409);

        String id = activeTextbook(ana, "Bouldering");
        ok(writeNext(ana, id));
        String lessonId = get("/textbooks/" + id + "/lessons/1", ana).body().get("id").asString();
        assertThat(get("/textbooks/" + id + "/lessons/1", ben).status()).isEqualTo(404);
        assertThat(get("/textbooks/" + id + "/contents", ben).status()).isEqualTo(404);
        assertThat(post("/lessons/" + lessonId + "/read", ben, null).status()).isEqualTo(404);
        assertThat(post("/textbooks/" + id + "/lessons/next", ben, null).status()).isEqualTo(404);
        assertThat(post("/textbooks/" + id + "/resources", ben, Map.of("title", "x", "url", "https://x.org")).status()).isEqualTo(404);
        assertThat(get("/textbooks/" + id + "/lessons/9", ana).status()).isEqualTo(404);
        assertThat(post("/textbooks/" + id + "/resources", ana, Map.of("title", "x", "url", "javascript:alert(1)")).status()).isEqualTo(400);
    }

    // ------------------------------------------------------------------ helpers

    private SseResp writeNext(String token, String textbookId) {
        Resp started = post("/textbooks/" + textbookId + "/lessons/next", token, null);
        assertThat(started.status()).as(started.raw()).isEqualTo(202);
        return sseGet("/jobs/" + started.body().get("jobId").asString() + "/events", token);
    }

    /** A textbook through the (fake) preface with its mission confirmed. */
    private String activeTextbook(String token, String topic) {
        String id = post("/textbooks", token, Map.of("topic", topic)).body().get("textbook").get("id").asString();
        String base = "/textbooks/" + id + "/mission";
        ok(sse(base + "/messages", token, null));
        ok(sse(base + "/messages", token, Map.of("content", "I want to climb a V3 with my friends by spring.")));
        ok(sse(base + "/messages", token, Map.of("content", "Twice a week at my local gym.")));
        JsonNode draft = get(base, token).body().get("draft");
        ok(post(base + "/confirm", token, Map.of("draft", JSON.convertValue(draft, Map.class))));
        return id;
    }

    /** A LESSON_WRITE reply: optionally with a practice block, and optionally an extra quiz that breaks the length rule. */
    private static String cannedLesson(boolean withPractice, boolean withBrokenQuiz) {
        String good = """
                {"id": "q1", "type": "QUIZ", "style": "check", "text": "What helps most?", "items": ["Small steps you repeat", "One big leap at once", "Waiting for a good day"],
                 "answer": 0, "explanation": "Repetition builds skill.", "refs": []}""";
        String broken = """
                ,{"id": "q2", "type": "QUIZ", "style": "check", "text": "Which?", "items": ["Yes", "No", "It depends on several very different factors"],
                 "answer": 0, "explanation": "Because.", "refs": []}""";
        String practice = """
                ,{"id": "f1", "type": "FREE_RESPONSE", "style": "", "text": "Describe your first step.", "items": ["Gentle", "Medium", "Strong"],
                 "answer": -1, "explanation": "", "refs": []}""";
        return """
                {"title": "Canned lesson", "subtitle": "A lesson pinned by the test.", "win": "You’ll be able to start.",
                 "blocks": [
                   {"id": "h1", "type": "HEADING", "style": "", "text": "Start here", "items": [], "answer": -1, "explanation": "", "refs": []},
                   {"id": "t1", "type": "TEXT", "style": "", "text": "Some *text*.", "items": [], "answer": -1, "explanation": "", "refs": []},
                   {"id": "k1", "type": "KEY_IDEA", "style": "", "text": "Small steps.", "items": [], "answer": -1, "explanation": "", "refs": []},
                   {"id": "c1", "type": "CALLOUT", "style": "watch_out", "text": "Doing too much.", "items": [], "answer": -1, "explanation": "", "refs": []},
                   %s%s%s
                 ],
                 "referenceSheet": {"title": "Sheet", "md": "- one"},
                 "rubrics": [%s]}""".formatted(good, withBrokenQuiz ? broken : "", withPractice ? practice : "",
                withPractice ? "{\"blockId\": \"f1\", \"points\": [\"Names a step\"], \"answerOutline\": \"A concrete step.\"}" : "");
    }
}
