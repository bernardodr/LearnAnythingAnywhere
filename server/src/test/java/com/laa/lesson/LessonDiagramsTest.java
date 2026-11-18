package com.laa.lesson;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.laa.IntegrationTest;
import com.laa.llm.Calls;
import com.laa.llm.Provider;
import com.laa.llm.provider.fake.FailureMode;
import com.laa.llm.provider.fake.FakeBehaviour;
import com.laa.safety.Moderation;

import tools.jackson.databind.JsonNode;

/**
 * Interactive diagrams in lessons (issue #60), end to end through the lesson job. A diagram can make a lesson better,
 * but never stops one: whatever goes wrong with a diagram, it's left out and the rest of the lesson is saved.
 */
class LessonDiagramsTest extends IntegrationTest {

    @Test
    void aPassedDiagramIsStoredWithItsPageAndAtMostTwoAreKept() {
        String learner = login("diagrams@test.dev");
        String id = activeTextbook(learner);
        fakes.setCanned(Calls.LESSON_WRITE, lessonWithDiagrams(3));

        ok(writeNext(learner, id));

        JsonNode blocks = get("/textbooks/" + id + "/lessons/1", learner).body().get("blocks");
        List<JsonNode> diagrams = blocks.valueStream().filter(b -> b.get("type").asString().equals("diagram")).toList();
        assertThat(diagrams).hasSize(2);
        assertThat(diagrams.getFirst().get("html").asString()).contains("<svg");
        assertThat(diagrams.getFirst().get("caption").asString()).isEqualTo("Tap each stage. 1");
        assertThat(diagramRuns()).isEqualTo(2); // the third was dropped before anyone drew it
        // Metered like any other learner request: theirs, for this textbook, so it counts toward their allowance.
        assertThat(jdbc.sql("SELECT count(*) FROM model_runs WHERE task = 'DIAGRAM' AND origin = 'APP' AND user_id IS NOT NULL "
                + "AND textbook_id = :t AND call = 'DIAGRAM'").param("t", java.util.UUID.fromString(id)).query(Long.class).single()).isEqualTo(2);
    }

    @Test
    void aPageThatKeepsReachingOutIsRedrawnOnceThenLeftOut() {
        String learner = login("reaching@test.dev");
        String id = activeTextbook(learner);
        fakes.setCanned(Calls.LESSON_WRITE, lessonWithDiagrams(1));
        fakes.setCanned(Calls.DIAGRAM, diagram("<p>Loading</p><script>fetch('/textbooks').then(r => r.text())</script>"));

        SseResp job = writeNext(learner, id);

        assertThat(job.named("done")).hasSize(1);
        assertThat(types(learner, id)).doesNotContain("diagram").contains("free_response");
        assertThat(diagramRuns()).isEqualTo(2);
        // The diagram's block id is simply missing; practice on the blocks after it still finds its marking.
        JsonNode lesson = get("/textbooks/" + id + "/lessons/1", learner).body();
        String practice = lesson.get("blocks").valueStream().filter(b -> b.get("type").asString().equals("free_response"))
                .findFirst().orElseThrow().get("id").asString();
        ok(post("/lessons/" + lesson.get("id").asString() + "/blocks/" + practice + "/hint", learner, null));
    }

    @Test
    void aDiagramWhoseWordsDontFitTheReaderSettingsIsLeftOutAndItsRunMarkedBlocked() {
        String learner = login("blocked-diagram@test.dev");
        String id = activeTextbook(learner);
        fakes.setCanned(Calls.LESSON_WRITE, lessonWithDiagrams(1));
        // The words are in a script string, shown only when the learner taps: the check still reads them.
        fakes.setCanned(Calls.DIAGRAM, diagram("<button class=\"laa-button\" id=\"b\">Tap</button><script>const said = \""
                + FakeBehaviour.BLOCK_MARKER + " shown later\";</script>"));

        ok(writeNext(learner, id));

        assertThat(types(learner, id)).doesNotContain("diagram").contains("free_response");
        assertThat(jdbc.sql("SELECT status FROM model_runs WHERE task = 'DIAGRAM'").query(String.class).single()).isEqualTo("BLOCKED");
    }

    @Test
    void aDiagramInTheWrongShapeIsLeftOut() {
        String learner = login("shapeless@test.dev");
        String id = activeTextbook(learner);
        fakes.setCanned(Calls.LESSON_WRITE, lessonWithDiagrams(1));
        fakes.setCanned(Calls.DIAGRAM, "{\"title\": \"Only a title\"}");

        ok(writeNext(learner, id));

        assertThat(types(learner, id)).doesNotContain("diagram").contains("free_response");
    }

    @Test
    void whenTheDiagramWriterIsDownTheLessonIsSavedWithoutIt() {
        String learner = login("artist-down@test.dev");
        String id = activeTextbook(learner);
        fakes.setCanned(Calls.LESSON_WRITE, lessonWithDiagrams(1));
        // Diagrams get their own route, so the owner can send them to another service, and that one can fail alone.
        ok(patch("/owner/routes/DIAGRAM", ownerToken(), Map.of("providerChoice", "FAKE_B")));
        fakes.set(Provider.FAKE_B, FailureMode.SERVER_ERROR);

        SseResp job = writeNext(learner, id);

        assertThat(job.named("done")).hasSize(1);
        assertThat(types(learner, id)).doesNotContain("diagram").contains("free_response");
    }

    @Test
    void aLearnerWhoReachesTheirDailyCapMidLessonStillGetsTheLessonWithoutTheDiagram() {
        String learner = login("cap-mid-lesson@test.dev");
        String id = activeTextbook(learner);
        fakes.setCanned(Calls.LESSON_WRITE, lessonWithDiagrams(1));
        // The lesson's own safety check is the last call before the diagram. As soon as it's logged, this learner's
        // spend jumps past their daily cap, so the diagram call is refused by the allowance, not by an outage.
        jdbc.sql("""
                CREATE OR REPLACE FUNCTION test_overspend() RETURNS trigger AS $$ BEGIN
                  IF NEW.call = 'SAFETY_CHECK.content' AND NEW.user_id IS NOT NULL THEN
                    INSERT INTO model_runs (task, provider, model, latency_ms, status, origin, cost_estimate, user_id, call)
                    VALUES ('LESSON_WRITE', 'FAKE', 'fake', 1, 'OK', 'APP', 99, NEW.user_id, 'LESSON_WRITE');
                  END IF;
                  RETURN NEW;
                END $$ LANGUAGE plpgsql""").update();
        jdbc.sql("CREATE TRIGGER test_overspend AFTER INSERT ON model_runs FOR EACH ROW EXECUTE FUNCTION test_overspend()").update();
        try {
            SseResp job = writeNext(learner, id);

            assertThat(job.named("error")).as("the lesson was written and checked: it must not be thrown away").isEmpty();
            assertThat(types(learner, id)).doesNotContain("diagram").contains("free_response");
            assertThat(diagramRuns()).isZero();
        } finally {
            jdbc.sql("DROP TRIGGER test_overspend ON model_runs").update();
            jdbc.sql("DROP FUNCTION test_overspend()").update();
        }
    }

    @Test
    void whenTheSafetyCheckIsDownWhileCheckingDiagramsNothingIsSaved() {
        String learner = login("check-down@test.dev");
        String id = activeTextbook(learner);
        fakes.setCanned(Calls.LESSON_WRITE, lessonWithDiagrams(1));
        // The lesson's own check passes; the check of the diagram's words can't run. An unchecked page is never shown,
        // and LAA never carries on past a safety check that's down, so the lesson isn't saved either.
        fakes.setCanned(Calls.DIAGRAM, diagram("<p>" + FakeBehaviour.CHECK_DOWN_MARKER + " A part to tap</p>"));

        SseResp job = writeNext(learner, id);

        assertThat(job.named("error")).as(job.raw()).isNotEmpty();
        assertThat(job.named("error").getFirst().data().get("message").asString()).contains("couldn’t run the safety check");
        assertThat(get("/textbooks/" + id + "/lessons/1", learner).status()).isEqualTo(404);
    }

    @Test
    void aBriefThatDoesntFitTheReaderSettingsBecomesAPlaceholderAndIsNeverDrawn() {
        String learner = login("blocked-brief@test.dev");
        String id = activeTextbook(learner);
        // The brief goes to another AI, so it's checked with the lesson like any other block. Both attempts fail.
        fakes.setCanned(Calls.LESSON_WRITE, lessonWithDiagrams(1).replace("A loop of four stages", FakeBehaviour.BLOCK_MARKER + " A loop"));

        ok(writeNext(learner, id));

        JsonNode blocks = get("/textbooks/" + id + "/lessons/1", learner).body().get("blocks");
        assertThat(blocks.valueStream().map(b -> b.get("type").asString())).doesNotContain("diagram");
        assertThat(blocks.valueStream().filter(b -> Moderation.PLACEHOLDER.equals(b.path("text").asString(null)))).hasSize(1);
        assertThat(diagramRuns()).isZero();
    }

    @Test
    void theDiagramsWordsFollowTheLearnersLinksRule() {
        // A learner set up for a child of 10: links are off.
        ChildReader child = childReader(10, true);
        String id = activeTextbook(child.token());
        fakes.setCanned(Calls.LESSON_WRITE, lessonWithDiagrams(1));
        fakes.setCanned(Calls.DIAGRAM, JSON.writeValueAsString(Map.of("title", "Feet on the wall",
                "html", "<p>Tap a part</p>", "textVersion", "- Toe: read more at https://example.com/feet")));

        ok(writeNext(child.token(), id));

        JsonNode diagram = get("/textbooks/" + id + "/lessons/1", child.token()).body().get("blocks").valueStream()
                .filter(b -> b.get("type").asString().equals("diagram")).findFirst().orElseThrow();
        assertThat(diagram.get("text").asString()).startsWith("- Toe: read more at").doesNotContain("https://", "example.com");
    }

    @Test
    void theStoredPageHasNoDocumentWrapperOrNamespaces() {
        String learner = login("wrapped@test.dev");
        String id = activeTextbook(learner);
        fakes.setCanned(Calls.LESSON_WRITE, lessonWithDiagrams(1));
        fakes.setCanned(Calls.DIAGRAM, diagram("<!DOCTYPE html><html><head><title>t</title></head><body>"
                + "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 10 10\"><circle r=\"4\"/></svg></body></html>"));

        ok(writeNext(learner, id));

        String html = get("/textbooks/" + id + "/lessons/1", learner).body().get("blocks").valueStream()
                .filter(b -> b.get("type").asString().equals("diagram")).findFirst().orElseThrow().get("html").asString();
        assertThat(html).isEqualTo("<svg viewBox=\"0 0 10 10\"><circle r=\"4\"/></svg>");
    }

    @Test
    void theOwnerCanRouteDiagramsOnTheirOwnAndCompareThem() {
        String owner = ownerToken();
        JsonNode routes = get("/owner/routes", owner).body();
        assertThat(routes.toString()).contains("\"DIAGRAM\"");
        assertThat(get("/owner/calls", owner).body().valueStream().map(c -> c.get("id").asString()).toList()).contains("DIAGRAM");

        Resp compared = post("/owner/compare", owner, Map.of("call", "DIAGRAM",
                "input", Map.of("message", "Draw one interactive diagram: a loop of four stages."),
                "a", Map.of("provider", "FAKE"), "b", Map.of("provider", "FAKE_B")));
        ok(compared);
        assertThat(compared.body().get("mode").asString()).isEqualTo("structured:DiagramDraft");
        assertThat(compared.body().get("a").get("checks").get("schemaValid").asBoolean()).isTrue();
        assertThat(compared.body().get("a").get("parsed").get("html").asString()).contains("<svg");
    }

    // ------------------------------------------------------------------ helpers

    private long diagramRuns() {
        return jdbc.sql("SELECT count(*) FROM model_runs WHERE task = 'DIAGRAM'").query(Long.class).single();
    }

    private List<String> types(String token, String textbookId) {
        return get("/textbooks/" + textbookId + "/lessons/1", token).body().get("blocks").valueStream()
                .map(b -> b.get("type").asString()).toList();
    }

    private static String diagram(String html) {
        return JSON.writeValueAsString(Map.of("title", "A test diagram", "html", html, "textVersion", "- One stage"));
    }

    /** A LESSON_WRITE reply for lesson 1 with {@code count} DIAGRAM blocks after the text. */
    private static String lessonWithDiagrams(int count) {
        StringBuilder diagrams = new StringBuilder();
        for (int i = 1; i <= count; i++) {
            diagrams.append("""
                    {"id": "d%d", "type": "DIAGRAM", "style": "", "text": "A loop of four stages the learner steps through.", "items": [],
                     "answer": -1, "explanation": "Tap each stage. %d", "refs": []},""".formatted(i, i));
        }
        return """
                {"title": "Canned lesson", "subtitle": "A lesson pinned by the test.", "win": "You’ll be able to start.",
                 "blocks": [
                   {"id": "h1", "type": "HEADING", "style": "", "text": "Start here", "items": [], "answer": -1, "explanation": "", "refs": []},
                   {"id": "t1", "type": "TEXT", "style": "", "text": "Some *text*.", "items": [], "answer": -1, "explanation": "", "refs": []},
                   %s
                   {"id": "k1", "type": "KEY_IDEA", "style": "", "text": "Small steps.", "items": [], "answer": -1, "explanation": "", "refs": []},
                   {"id": "q1", "type": "QUIZ", "style": "check", "text": "What helps most?", "items": ["Small steps you repeat", "One big leap at once", "Waiting for a good day"],
                    "answer": 0, "explanation": "Repetition builds skill.", "refs": []},
                   {"id": "f1", "type": "FREE_RESPONSE", "style": "", "text": "Describe your first step.", "items": ["Gentle", "Medium", "Strong"],
                    "answer": -1, "explanation": "", "refs": []}
                 ],
                 "referenceSheet": {"title": "Sheet", "md": "- one"},
                 "rubrics": [{"blockId": "f1", "points": ["Names a step"], "answerOutline": "A concrete step."}]}""".formatted(diagrams);
    }

    private SseResp writeNext(String token, String textbookId) {
        Resp started = post("/textbooks/" + textbookId + "/lessons/next", token, null);
        assertThat(started.status()).as(started.raw()).isEqualTo(202);
        return sseGet("/jobs/" + started.body().get("jobId").asString() + "/events", token);
    }

    /** A textbook through the (fake) preface with its mission confirmed. */
    private String activeTextbook(String token) {
        String id = post("/textbooks", token, Map.of("topic", "Bouldering")).body().get("textbook").get("id").asString();
        String base = "/textbooks/" + id + "/mission";
        ok(sse(base + "/messages", token, null));
        ok(sse(base + "/messages", token, Map.of("content", "I want to climb a V3 with my friends by spring.")));
        ok(sse(base + "/messages", token, Map.of("content", "Twice a week at my local gym.")));
        JsonNode draft = get(base, token).body().get("draft");
        ok(post(base + "/confirm", token, Map.of("draft", JSON.convertValue(draft, Map.class))));
        return id;
    }
}
