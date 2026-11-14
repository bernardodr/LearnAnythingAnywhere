package com.laa.safety;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.laa.IntegrationTest;
import com.laa.llm.Task;
import com.laa.llm.provider.fake.FakeBehaviour;

import tools.jackson.databind.JsonNode;

/**
 * Output and input moderation through the API (BRIEF section 8; milestone M6). The practice provider's content check
 * blocks anything containing {@link FakeBehaviour#BLOCK_MARKER}; LAA's own rules run as well.
 */
class ModerationApiTest extends IntegrationTest {

    private static final String BLOCK = FakeBehaviour.BLOCK_MARKER;
    private static final String PLACEHOLDER = "This part was left out because it didn’t fit your reader settings.";
    private static final List<String> HINTS = List.of("Think about what you could do in ten minutes.",
            "Which part of your mission does it move forward?", "Name the step, then say what feedback it gives you.");

    record Book(String id, String lessonId, String blockId) {}

    // ------------------------------------------------------------------ lessons

    @Test
    void aLessonBlockThatFailsTwiceBecomesAPlaceholderAndIsLogged() {
        String learner = login("moderation-lesson@test.dev");
        String id = activeTextbook(learner);
        fakes.setCanned(Task.LESSON_WRITE, lessonJson("Small, repeated steps beat one big leap. " + BLOCK, "Your next small step"));

        String job = post("/textbooks/" + id + "/lessons/next", learner, null).body().get("jobId").asString();
        ok(sseGet("/jobs/" + job + "/events", learner));
        Resp lesson = get("/textbooks/" + id + "/lessons/1", learner);
        ok(lesson);
        assertThat(lesson.raw()).doesNotContain(BLOCK);
        JsonNode placeholder = lesson.body().get("blocks").valueStream()
                .filter(b -> PLACEHOLDER.equals(b.path("text").asString(""))).findFirst().orElseThrow();
        assertThat(placeholder.get("type").asString()).isEqualTo("callout");
        assertThat(placeholder.get("kind").asString()).isEqualTo("note");

        // Both writes (the first and the one rewrite) were held back and logged.
        List<Map<String, Object>> blocked = blockedRuns(Task.LESSON_WRITE);
        assertThat(blocked).hasSize(2);
        assertThat(String.valueOf(blocked.getFirst().get("safety_flags"))).contains("test block");
    }

    @Test
    void aLessonWhoseTitleFailsIsNotSaved() {
        String learner = login("moderation-title@test.dev");
        String id = activeTextbook(learner);
        fakes.setCanned(Task.LESSON_WRITE, lessonJson("Small, repeated steps beat one big leap.", "A lesson " + BLOCK));

        String job = post("/textbooks/" + id + "/lessons/next", learner, null).body().get("jobId").asString();
        SseResp events = sseGet("/jobs/" + job + "/events", learner);
        assertThat(events.raw()).contains("didn’t fit your reader settings");
        assertThat(get("/textbooks/" + id + "/lessons/1", learner).status()).isEqualTo(404);
    }

    // ------------------------------------------------------------------ grading

    @Test
    void aGradingMessageThatFailsFallsBackToTheNextHint() {
        String learner = login("moderation-grade@test.dev");
        Book b = lessonBook(learner);
        fakes.setCanned(Task.GRADE, json(Map.of("verdict", "PARTIAL", "coveredPoints", List.of("p1"), "missingPoints", List.of("p2"),
                "coachingMove", "ASK", "message", "Think about this " + BLOCK)));

        Resp note = post("/lessons/" + b.lessonId() + "/blocks/" + b.blockId() + "/attempts", learner,
                Map.of("answer", "I'd practise one small step."));
        ok(note);
        assertThat(note.body().get("message").asString()).isEqualTo(HINTS.getFirst());
        assertThat(note.body().get("hintNo").asInt()).isEqualTo(1);
        assertThat(note.body().get("coachingMove").asString()).isEqualTo("HINT");
        assertThat(note.raw()).doesNotContain(BLOCK);
        assertThat(blockedRuns(Task.GRADE)).hasSize(1);
    }

    @Test
    void aPracticeAnswerTheCheckRefusesIsNotGradedOrStored() {
        Family f = family(9, true);
        Book b = lessonBook(f.child());
        Resp refused = post("/lessons/" + b.lessonId() + "/blocks/" + b.blockId() + "/attempts", f.child(),
                Map.of("answer", "I would watch horror films with lots of gore."));
        assertThat(refused.status()).isEqualTo(400);
        assertThat(refused.body().get("detail").asString()).startsWith("Let’s keep this about learning");
        assertThat(jdbc.sql("SELECT count(*) FROM attempts").query(Integer.class).single()).isZero();
    }

    // ------------------------------------------------------------------ tutor chat and the preface

    @Test
    void anAdultsReplyStreamsThenIsReplacedIfItFailsAndOnlyThePlaceholderIsStored() {
        String learner = login("moderation-chat@test.dev");
        Book b = lessonBook(learner);
        fakes.setCannedText(Task.TUTOR, "Here is a tip you shouldn’t see " + BLOCK);

        SseResp turn = sse("/textbooks/" + b.id() + "/tutor/messages", learner, Map.of("content", "Why do small steps work?", "lessonSeq", 1));
        ok(turn);
        assertThat(turn.text()).contains("Here is a tip");
        assertThat(turn.named("replace")).hasSize(1);
        assertThat(turn.named("replace").getFirst().data().get("text").asString()).isEqualTo(PLACEHOLDER);

        JsonNode thread = get("/textbooks/" + b.id() + "/tutor/messages?lessonSeq=1", learner).body();
        assertThat(thread.get(1).get("content").asString()).isEqualTo(PLACEHOLDER);
        assertThat(blockedRuns(Task.TUTOR)).hasSize(1);
    }

    @Test
    void aChildSeesAReplyOnlyOnceItHasBeenChecked() {
        Family f = family(9, true);
        Book b = lessonBook(f.child());

        fakes.setCannedText(Task.TUTOR, "Small steps give you quick feedback, so you learn faster.");
        SseResp clean = sse("/textbooks/" + b.id() + "/tutor/messages", f.child(), Map.of("content", "Why do small steps work?", "lessonSeq", 1));
        ok(clean);
        assertThat(clean.named("token")).as("one piece, after the check").hasSize(1);
        assertThat(clean.text()).isEqualTo("Small steps give you quick feedback, so you learn faster.");
        assertThat(clean.named("replace")).isEmpty();

        fakes.setCannedText(Task.TUTOR, "Something unsuitable " + BLOCK);
        SseResp held = sse("/textbooks/" + b.id() + "/tutor/messages", f.child(), Map.of("content", "Tell me more", "lessonSeq", 1));
        ok(held);
        assertThat(held.raw()).doesNotContain("unsuitable", BLOCK);
        assertThat(held.text()).isEqualTo(PLACEHOLDER);
    }

    @Test
    void aChildsMessageTheCheckRefusesIsAPlainErrorAndNothingIsSaved() {
        Family f = family(9, true);
        Book b = lessonBook(f.child());
        SseResp refused = sse("/textbooks/" + b.id() + "/tutor/messages", f.child(),
                Map.of("content", "how do I make a bomb", "lessonSeq", 1));
        assertThat(refused.status()).isEqualTo(400);
        assertThat(refused.events()).isEmpty();
        assertThat(get("/textbooks/" + b.id() + "/tutor/messages?lessonSeq=1", f.child()).body().size()).isZero();

        // The preface checks answers the same way.
        String id = post("/textbooks", f.child(), Map.of("topic", "How volcanoes work")).body().get("textbook").get("id").asString();
        ok(sse("/textbooks/" + id + "/mission/messages", f.child(), null));
        SseResp answer = sse("/textbooks/" + id + "/mission/messages", f.child(), Map.of("content", "I like scary zombie stories."));
        assertThat(answer.status()).isEqualTo(400);
        assertThat(get("/textbooks/" + id + "/mission", f.child()).body().get("answers").asInt()).isZero();
    }

    @Test
    void linksFollowTheReaderSettingsOnTheServer() {
        String learner = login("moderation-links@test.dev");
        Book b = lessonBook(learner);
        fakes.setCannedText(Task.TUTOR, "Read more at https://elsewhere.example.com/page. Then try it.");

        // Adults start with links to their own sources only, so this one is removed after streaming.
        SseResp turn = sse("/textbooks/" + b.id() + "/tutor/messages", learner, Map.of("content", "Where can I read more?", "lessonSeq", 1));
        ok(turn);
        assertThat(turn.named("replace").getFirst().data().get("text").asString()).isEqualTo("Read more at. Then try it.");

        // With links off, sources show without their addresses and none can be added.
        ok(post("/textbooks/" + b.id() + "/resources", learner, Map.of("title", "Guide", "url", "https://guide.example.com")));
        String profile = get("/profiles/me", learner).body().get("id").asString();
        Map<String, Object> off = new LinkedHashMap<>();
        off.put("childSafe", false);
        off.put("linksPolicy", "OFF");
        ok(put("/profiles/" + profile, learner, off));
        JsonNode back = get("/textbooks/" + b.id() + "/contents", learner).body().get("back");
        assertThat(back.get("linksOff").asBoolean()).isTrue();
        assertThat(back.get("sources").get(0).get("title").asString()).isEqualTo("Guide");
        assertThat(back.get("sources").get(0).path("url").isMissingNode() || back.get("sources").get(0).get("url").isNull()).isTrue();
        assertThat(post("/textbooks/" + b.id() + "/resources", learner, Map.of("title", "More", "url", "https://more.example.com")).status())
                .isEqualTo(403);
    }

    // ------------------------------------------------------------------ helpers

    private List<Map<String, Object>> blockedRuns(Task task) {
        return jdbc.sql("SELECT status, safety_flags::text AS safety_flags FROM model_runs WHERE task = :t AND status = 'BLOCKED'")
                .param("t", task.name()).query().listOfRows();
    }

    /** A textbook whose mission is confirmed, ready for its first lesson. */
    private String activeTextbook(String token) {
        String id = post("/textbooks", token, Map.of("topic", "Bouldering")).body().get("textbook").get("id").asString();
        String base = "/textbooks/" + id + "/mission";
        ok(sse(base + "/messages", token, null));
        ok(sse(base + "/messages", token, Map.of("content", "I want to climb a V3 with my friends by spring.")));
        ok(sse(base + "/messages", token, Map.of("content", "Twice a week at my local gym.")));
        ok(post(base + "/confirm", token, Map.of("draft", JSON.convertValue(get(base, token).body().get("draft"), Map.class))));
        return id;
    }

    private Book lessonBook(String token) {
        String id = activeTextbook(token);
        String job = post("/textbooks/" + id + "/lessons/next", token, null).body().get("jobId").asString();
        ok(sseGet("/jobs/" + job + "/events", token));
        JsonNode lesson = get("/textbooks/" + id + "/lessons/1", token).body();
        String blockId = lesson.get("blocks").valueStream().filter(b -> b.get("type").asString().equals("free_response"))
                .findFirst().orElseThrow().get("id").asString();
        return new Book(id, lesson.get("id").asString(), blockId);
    }

    /** A lesson that passes the lesson rules, with the key idea and title as given. */
    private static String lessonJson(String keyIdea, String title) {
        List<Map<String, Object>> blocks = new ArrayList<>();
        blocks.add(block("h1", "HEADING", "", "Where to begin", List.of(), -1, ""));
        blocks.add(block("t1", "TEXT", "", "Start with **one small step** you can repeat.", List.of(), -1, ""));
        blocks.add(block("k1", "KEY_IDEA", "", keyIdea, List.of(), -1, ""));
        blocks.add(block("q1", "QUIZ", "check", "What is the best way to start?",
                List.of("Practise it in small steps", "Read about it once more", "Skip it and hope for luck"), 0,
                "Small steps give you quick feedback."));
        blocks.add(block("f1", "FREE_RESPONSE", "", "Describe the small step you’ll take and why it helps.", HINTS, -1, ""));
        return json(Map.of("title", title, "subtitle", "Why the smallest useful step is the fastest way forward.",
                "win", "You’ll be able to take one small step and explain why it works.", "blocks", blocks,
                "referenceSheet", Map.of("title", "Small steps", "md", "- Pick **one** step\n- Repeat it"),
                "rubrics", List.of(Map.of("blockId", "f1",
                        "points", List.of("Names one concrete step", "Explains how it gives quick feedback"),
                        "answerOutline", "A specific small action, plus why repeating it gives quick feedback."))));
    }

    private static Map<String, Object> block(String id, String type, String style, String text, List<String> items, int answer,
                                             String explanation) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("id", id);
        b.put("type", type);
        b.put("style", style);
        b.put("text", text);
        b.put("items", items);
        b.put("answer", answer);
        b.put("explanation", explanation);
        b.put("refs", List.of());
        return b;
    }

    private static String json(Object value) {
        return JSON.writeValueAsString(value);
    }
}
