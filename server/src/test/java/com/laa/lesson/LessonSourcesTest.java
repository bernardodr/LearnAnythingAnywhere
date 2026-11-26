package com.laa.lesson;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.laa.IntegrationTest;
import com.laa.llm.Calls;

import tools.jackson.databind.JsonNode;

/**
 * Sources for every lesson (issue #82), end to end in practice mode: the practice provider "searches" and returns two
 * sources on example.org and example.com, which the source check passes offline only in practice mode.
 */
class LessonSourcesTest extends IntegrationTest {

    @Test
    void eachLessonEndsWithSourcesFoundForItWhichJoinTheBook() {
        String learner = login("sources@test.dev");
        String id = activeTextbook(learner, "The Night Sky");

        SseResp job = writeNext(learner, id);
        assertThat(job.named("progress")).extracting(e -> e.data().get("stage").asString()).containsSubsequence("plan", "research", "write");
        JsonNode sources = get("/textbooks/" + id + "/lessons/1", learner).body().get("sources");
        assertThat(sources).hasSize(2);
        assertThat(sources.get(0).get("id").asString()).isEqualTo("r1");
        assertThat(sources.get(0).get("url").asString()).isEqualTo("https://example.org/guides/the-night-sky");
        assertThat(sources.get(0).get("note").asString()).contains("A clear overview to read first", "(");
        assertThat(sources.get(1).get("url").asString()).isEqualTo("https://www.example.com/learn/the-night-sky/deeper");
        // They join the book's sources (the back of the book), and so the links allowlist.
        assertThat(get("/textbooks/" + id + "/contents", learner).body().get("back").get("sources")).hasSize(2);
        // The research was a metered run of its own, with its search counted.
        assertThat(jdbc.sql("SELECT count(*) FROM model_runs WHERE task = 'RESEARCH' AND status = 'OK'").query(Integer.class).single()).isEqualTo(1);

        // Lesson 2: the search finds the same two pages, which are linked to it, not added to the book twice.
        ok(post("/lessons/" + get("/textbooks/" + id + "/lessons/1", learner).body().get("id").asString() + "/read", learner, null));
        ok(writeNext(learner, id));
        assertThat(get("/textbooks/" + id + "/lessons/2", learner).body().get("sources")).extracting(s -> s.get("id").asString())
                .containsExactly("r1", "r2");
        assertThat(get("/textbooks/" + id + "/contents", learner).body().get("back").get("sources")).hasSize(2);
    }

    @Test
    void aChildSafeReaderGetsOnlySourcesFromTheEducationalSites() {
        ChildReader child = childReader(9, true);
        String id = activeTextbook(child.token(), "The Night Sky");
        ok(writeNext(child.token(), id));
        // The practice sources are on example.org and example.com, which aren't on the child-safe list: left out.
        assertThat(get("/textbooks/" + id + "/lessons/1", child.token()).body().get("sources")).isEmpty();
        assertThat(get("/textbooks/" + id + "/contents", child.token()).body().get("back").get("sources")).isEmpty();
    }

    @Test
    void aFailedSearchNeverStopsTheLesson() {
        String learner = login("no-sources@test.dev");
        String id = activeTextbook(learner, "Bouldering");
        fakes.setCanned(Calls.RESEARCH, "{\"not\": \"the right shape\"}");
        SseResp job = writeNext(learner, id);
        assertThat(job.named("done")).hasSize(1);
        assertThat(get("/textbooks/" + id + "/lessons/1", learner).body().get("sources")).isEmpty();
    }

    private SseResp writeNext(String token, String textbookId) {
        String job = post("/textbooks/" + textbookId + "/lessons/next", token, null).body().get("jobId").asString();
        return sseGet("/jobs/" + job + "/events", token);
    }

    private String activeTextbook(String token, String topic) {
        String id = post("/textbooks", token, Map.of("topic", topic)).body().get("textbook").get("id").asString();
        String base = "/textbooks/" + id + "/mission";
        ok(sse(base + "/messages", token, null));
        ok(sse(base + "/messages", token, Map.of("content", "I want to find the planets myself by spring.")));
        ok(sse(base + "/messages", token, Map.of("content", "Two clear evenings a month.")));
        JsonNode draft = get(base, token).body().get("draft");
        ok(post(base + "/confirm", token, Map.of("draft", JSON.convertValue(draft, Map.class))));
        return id;
    }
}
