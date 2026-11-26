package com.laa.textbook;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.laa.IntegrationTest;
import com.laa.llm.Calls;

import tools.jackson.databind.JsonNode;

/**
 * The familiarity slider (issue #83), end to end: it's saved with a new book, and reaches the AI for the preface, the
 * mission draft, and the lesson's plan and writing; a book without one is "Not stated".
 */
class FamiliarityTest extends IntegrationTest {

    @Test
    void howFamiliarTheLearnerIsReachesThePrefaceAndEveryLessonsPlanAndWriting() {
        String learner = login("familiar@test.dev");
        Resp created = post("/textbooks", learner, Map.of("topic", "The Night Sky", "familiarity", 5));
        assertThat(created.status()).isEqualTo(201);
        JsonNode book = created.body().get("textbook");
        assertThat(book.get("familiarity").asInt()).isEqualTo(5);
        String id = book.get("id").asString();

        String base = "/textbooks/" + id + "/mission";
        ok(sse(base + "/messages", learner, null));
        assertThat(fakes.lastSystemPrompt(Calls.INTERVIEW_TURN)).contains("Very confident. They're very confident");
        ok(sse(base + "/messages", learner, Map.of("content", "I want to photograph deep-sky objects.")));
        ok(sse(base + "/messages", learner, Map.of("content", "Most clear nights.")));
        assertThat(fakes.lastSystemPrompt(Calls.MISSION_DRAFT)).contains("Very confident");
        ok(post(base + "/confirm", learner, Map.of("draft", JSON.convertValue(get(base, learner).body().get("draft"), Map.class))));

        String job = post("/textbooks/" + id + "/lessons/next", learner, null).body().get("jobId").asString();
        ok(sseGet("/jobs/" + job + "/events", learner));
        assertThat(fakes.lastSystemPrompt(Calls.LESSON_PLAN)).contains("### Where they started\nVery confident. They're very confident",
                "Never explain the basics");
        assertThat(fakes.lastSystemPrompt(Calls.LESSON_WRITE)).contains("### Where they started\nVery confident");
    }

    @Test
    void itsOptionalAndOnlyOneToFive() {
        String learner = login("familiar-optional@test.dev");
        Resp none = post("/textbooks", learner, Map.of("topic", "Sourdough"));
        assertThat(none.status()).isEqualTo(201);
        assertThat(none.body().get("textbook").get("familiarity").isNull()).isTrue();
        ok(sse("/textbooks/" + none.body().get("textbook").get("id").asString() + "/mission/messages", learner, null));
        assertThat(fakes.lastSystemPrompt(Calls.INTERVIEW_TURN)).contains("Not stated");

        assertThat(post("/textbooks", learner, Map.of("topic", "Chess", "familiarity", 6)).status()).isEqualTo(400);
        assertThat(post("/textbooks", learner, Map.of("topic", "Chess", "familiarity", 0)).status()).isEqualTo(400);
    }
}
