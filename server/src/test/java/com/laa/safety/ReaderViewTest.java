package com.laa.safety;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.laa.IntegrationTest;
import com.laa.llm.Calls;
import com.laa.llm.GenerationProfile.LinksPolicy;
import com.laa.mission.MissionInterview;

import tools.jackson.databind.JsonNode;

/**
 * The reader view (BRIEF section 8: links are enforced on the server): whatever was written while links were allowed,
 * a learner whose links policy is now Off sees no web address in any response. Every surface gets an address planted
 * while the policy is Open; then the policy changes and every learner read path is fetched.
 */
class ReaderViewTest extends IntegrationTest {

    /** Planted everywhere; must never reach a learner whose links are off. */
    private static final String PLANT = "plant.example.com";
    private static final String MARKER = MissionInterview.MARKER;

    @Test
    void withLinksOffNoResponseShowsAnAddressWhateverWasWrittenBefore() {
        String learner = login("reader-view@test.dev");
        String profileId = get("/profiles/me", learner).body().get("id").asString();
        ok(put("/profiles/" + profileId, learner, settings("OPEN")));

        // The topic gate's reframed title and note.
        fakes.setCanned(Calls.TOPIC_GATE, json(Map.of("verdict", "REFRAME", "title", "Bouldering, see https://" + PLANT + "/title",
                "message", "We'll come at it this way: https://" + PLANT + "/gate", "categories", List.of())));
        String id = post("/textbooks", learner, Map.of("topic", "Bouldering")).body().get("textbook").get("id").asString();

        // The preface: the interviewer's reply, the learner's answer, the draft, the confirmed mission.
        fakes.setCannedText(Calls.INTERVIEW_TURN, "Tell me more: https://" + PLANT + "/interview " + MARKER);
        fakes.setCanned(Calls.MISSION_DRAFT, json(Map.of("headline", "Climb a V3, see https://" + PLANT + "/headline",
                "why", "Because https://" + PLANT + "/why says so.", "successLooksLike", List.of("A clean V3 https://" + PLANT + "/success"),
                "constraints", List.of(), "outOfScope", List.of())));
        String mission = "/textbooks/" + id + "/mission";
        ok(sse(mission + "/messages", learner, null));
        ok(sse(mission + "/messages", learner, Map.of("content", "Like https://" + PLANT + "/my-answer")));
        ok(post(mission + "/confirm", learner, Map.of("draft", JSON.convertValue(get(mission, learner).body().get("draft"), Map.class))));

        // A source of their own, then a lesson with addresses in every part.
        ok(post("/textbooks/" + id + "/resources", learner, Map.of("title", "Guide", "url", "https://" + PLANT + "/guide")));
        fakes.setCanned(Calls.LESSON_WRITE, plantedLesson());
        String job = post("/textbooks/" + id + "/lessons/next", learner, null).body().get("jobId").asString();
        ok(sseGet("/jobs/" + job + "/events", learner));
        JsonNode lesson = get("/textbooks/" + id + "/lessons/1", learner).body();
        String lessonId = lesson.get("id").asString();
        String blockId = lesson.get("blocks").valueStream().filter(b -> b.get("type").asString().equals("free_response"))
                .findFirst().orElseThrow().get("id").asString();
        String practice = "/lessons/" + lessonId + "/blocks/" + blockId;

        // Tutor chat, both sides.
        fakes.setCannedText(Calls.TUTOR_REPLY, "Read https://" + PLANT + "/chat first.");
        ok(sse("/textbooks/" + id + "/tutor/messages", learner, Map.of("content", "What about https://" + PLANT + "/question?", "lessonSeq", 1)));

        // Practice: a tutor's note, every hint, the strong answer, then a met answer and its evidence.
        fakes.setCanned(Calls.GRADE, grade("PARTIAL", "Look again, maybe https://" + PLANT + "/note helps."));
        ok(post(practice + "/attempts", learner, Map.of("answer", "One small step, see https://" + PLANT + "/attempt")));
        for (int i = 0; i < 4; i++) ok(post(practice + "/hint", learner, null));
        fakes.setCanned(Calls.GRADE, grade("MET", "Well done, see https://" + PLANT + "/met"));
        fakes.setCanned(Calls.RECORD_UPDATE, json(Map.of("writeRecord", true, "slug", "small-steps", "title", "Small steps https://" + PLANT + "/record",
                "body", "Knows why small steps work: https://" + PLANT + "/body", "evidence", "Explained it.",
                "glossary", List.of(Map.of("term", "Small step", "definition", "One action, see https://" + PLANT + "/glossary",
                        "avoid", List.of())))));
        ok(post(practice + "/attempts", learner, Map.of("answer", "One small step because it gives quick feedback.")));
        String recordPath = awaitLearned(learner, id).get(0).get("path").asString();

        // The plant took: with links open, the lesson shows its addresses.
        assertThat(get("/textbooks/" + id + "/lessons/1", learner).raw()).contains(PLANT);

        // Links off: nothing written before shows an address now.
        ok(put("/profiles/" + profileId, learner, settings("OFF")));
        Map<String, Resp> reads = new LinkedHashMap<>();
        reads.put("library", get("/textbooks", learner));
        reads.put("textbook", get("/textbooks/" + id, learner));
        reads.put("mission", get(mission, learner));
        reads.put("contents", get("/textbooks/" + id + "/contents", learner));
        reads.put("lesson", get("/textbooks/" + id + "/lessons/1", learner));
        reads.put("tutor thread", get("/textbooks/" + id + "/tutor/messages?lessonSeq=1", learner));
        reads.put("hint (strong answer)", post(practice + "/hint", learner, null));
        for (String file : List.of("MISSION.md", "RESOURCES.md", "GLOSSARY.md", recordPath)) {
            reads.put("file " + file, get("/textbooks/" + id + "/files/" + file, learner));
        }
        reads.put("new textbook", post("/textbooks", learner, Map.of("topic", "Rock climbing")));
        reads.forEach((what, r) -> {
            assertThat(r.status()).as(what + ": " + r.raw()).isBetween(200, 299);
            assertThat(r.raw()).as(what).doesNotContain(PLANT);
        });
        // Only the addresses went: the words around them are still there.
        assertThat(reads.get("lesson").raw()).contains("Your next small step", "Well done, see");
        assertThat(reads.get("contents").body().get("back").get("sources").get(0).get("title").asString()).isEqualTo("Guide");
    }

    @Test
    void aReaderChangesOnlyWebAddressesAnywhereInAResponse() {
        JsonNode tree = JSON.readTree("""
                {"id": "b1", "type": "text", "colour": "#3E4C5E", "count": 3, "ok": true,
                 "md": "See https://x.example.com for more", "nested": [{"note": "Or www.example.org today", "ids": ["r1", "p2"]}]}""");
        new ReaderView.Reader(LinksPolicy.OFF, Set.of()).apply(tree);
        assertThat(tree.toString()).isEqualTo("""
                {"id":"b1","type":"text","colour":"#3E4C5E","count":3,"ok":true,"md":"See for more",\
                "nested":[{"note":"Or today","ids":["r1","p2"]}]}""");

        JsonNode open = JSON.readTree("{\"md\": \"See https://x.example.com\"}");
        assertThat(new ReaderView.Reader(LinksPolicy.OPEN, Set.of()).apply(open).toString()).contains("x.example.com");
    }

    @Test
    void theOwnerConsoleStillSeesWhatTheModelWrote() {
        fakes.setCanned(Calls.LESSON_WRITE, plantedLesson());
        Resp r = post("/owner/compare", ownerToken(), Map.of("call", "LESSON_WRITE",
                "input", Map.of("message", "Write lesson 2."), "a", Map.of("provider", "FAKE"), "b", Map.of("provider", "FAKE_B")));
        ok(r);
        assertThat(r.body().get("a").get("output").asString()).contains(PLANT);
    }

    // ------------------------------------------------------------------ helpers

    private static Map<String, Object> settings(String links) {
        Map<String, Object> m = new HashMap<>();
        m.put("childSafe", false);
        m.put("linksPolicy", links);
        m.put("revealAfterAttempts", 1);
        return m;
    }

    private static String grade(String verdict, String message) {
        boolean met = verdict.equals("MET");
        return json(Map.of("verdict", verdict, "coveredPoints", met ? List.of("p1", "p2") : List.of("p1"),
                "missingPoints", met ? List.of() : List.of("p2"), "coachingMove", met ? "CONFIRM" : "ASK", "message", message));
    }

    /** A lesson that passes the lesson rules, with an address in its title, subtitle, win, text, hints, answer and sheet. */
    private static String plantedLesson() {
        List<Map<String, Object>> blocks = new ArrayList<>();
        blocks.add(block("h1", "HEADING", "", "Where to begin", List.of(), -1, ""));
        blocks.add(block("t1", "TEXT", "", "Start with [one small step](https://" + PLANT + "/md-link) you can repeat.", List.of(), -1, ""));
        blocks.add(block("k1", "KEY_IDEA", "", "Small, repeated steps beat one big leap.", List.of(), -1, ""));
        blocks.add(block("q1", "QUIZ", "check", "What is the best way to start?",
                List.of("Practise it in small steps", "Read about it once more", "Skip it and hope for luck"), 0,
                "Small steps give quick feedback, see https://" + PLANT + "/explanation."));
        blocks.add(block("f1", "FREE_RESPONSE", "", "Describe the small step you’ll take and why it helps.",
                List.of("Think about ten minutes: https://" + PLANT + "/hint", "Which part of your mission does it move forward?",
                        "Name the step, then the feedback it gives."), -1, ""));
        return json(Map.of("title", "Your next small step https://" + PLANT + "/lesson-title",
                "subtitle", "Why it works, see https://" + PLANT + "/subtitle",
                "win", "Take one small step: https://" + PLANT + "/win", "blocks", blocks,
                "referenceSheet", Map.of("title", "Small steps", "md", "- Pick one step https://" + PLANT + "/sheet"),
                "rubrics", List.of(Map.of("blockId", "f1", "points", List.of("Names one concrete step", "Explains how it gives quick feedback"),
                        "answerOutline", "A specific small action; this guide explains why: https://" + PLANT + "/outline"))));
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

    private JsonNode awaitLearned(String token, String textbookId) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(deadline)) {
            JsonNode learned = get("/textbooks/" + textbookId + "/contents", token).body().get("back").get("learned");
            if (learned.size() > 0) return learned;
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        throw new AssertionError("No learning record appeared within 10s");
    }

    private static String json(Object value) {
        return JSON.writeValueAsString(value);
    }
}
