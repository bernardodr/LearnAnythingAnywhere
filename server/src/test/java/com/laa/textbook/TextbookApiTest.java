package com.laa.textbook;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.laa.IntegrationTest;
import com.laa.llm.Calls;
import com.laa.llm.Provider;
import com.laa.llm.provider.fake.FailureMode;

/** The library and the topic gate (BRIEF sections 8 and 10, milestone M2). */
class TextbookApiTest extends IntegrationTest {

    @Test
    void aNewLearnerSeesAnEmptyShelfThenGetsABookThroughTheGate() {
        String learner = login("shelf@test.dev");
        Resp empty = get("/textbooks", learner);
        ok(empty);
        assertThat(empty.body().size()).isZero();

        Resp created = post("/textbooks", learner, Map.of("topic", "  bouldering  "));
        assertThat(created.status()).as(created.raw()).isEqualTo(201);
        assertThat(created.body().get("verdict").asString()).isEqualTo("ALLOW");
        var book = created.body().get("textbook");
        assertThat(book.get("title").asString()).isEqualTo("Bouldering");
        assertThat(book.get("topic").asString()).isEqualTo("bouldering");
        assertThat(book.get("status").asString()).isEqualTo("INTERVIEW");
        assertThat(book.get("volume").asInt()).isEqualTo(1);
        assertThat(book.get("coverColor").asString()).isEqualTo(Textbook.COVER_COLORS.get(0));
        assertThat(book.get("gateNote").isNull()).isTrue();

        Resp second = post("/textbooks", learner, Map.of("topic", "Sourdough"));
        assertThat(second.body().get("textbook").get("volume").asInt()).isEqualTo(2);
        assertThat(second.body().get("textbook").get("coverColor").asString()).isEqualTo(Textbook.COVER_COLORS.get(1));

        Resp shelf = get("/textbooks", learner);
        assertThat(shelf.body().size()).isEqualTo(2);
        assertThat(shelf.body().get(0).get("title").asString()).isEqualTo("Bouldering");

        // The gate's verdict was a metered SAFETY_CHECK run for this learner.
        var runs = jdbc.sql("SELECT task, status FROM model_runs WHERE task = 'SAFETY_CHECK'").query().listOfRows();
        assertThat(runs).hasSize(2).allSatisfy(r -> assertThat(r).containsEntry("status", "OK"));
    }

    @Test
    void anotherLearnersBookIsInvisible() {
        String ana = login("ana@test.dev");
        String ben = login("ben@test.dev");
        String id = post("/textbooks", ana, Map.of("topic", "Chess")).body().get("textbook").get("id").asString();

        assertThat(get("/textbooks/" + id, ana).status()).isEqualTo(200);
        assertThat(get("/textbooks/" + id, ben).status()).isEqualTo(404);
        assertThat(get("/textbooks/" + id + "/mission", ben).status()).isEqualTo(404);
        assertThat(sse("/textbooks/" + id + "/mission/messages", ben, null).status()).isEqualTo(404);
        assertThat(get("/textbooks", ben).body().size()).isZero();
        assertThat(get("/textbooks", null).status()).isEqualTo(401);
    }

    @Test
    void aDeclinedTopicCreatesNothingAndSaysWhyKindly() {
        String learner = login("decline@test.dev");
        fakes.setCanned(Calls.TOPIC_GATE, """
                {"verdict": "DECLINE", "title": "", "message": "That’s not something we can write about. How about the history of locks?",
                 "categories": ["illegal_activity"]}""");

        Resp r = post("/textbooks", learner, Map.of("topic", "something harmful"));
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body().get("verdict").asString()).isEqualTo("DECLINE");
        assertThat(r.body().get("message").asString()).contains("history of locks");
        assertThat(r.body().get("textbook").isNull()).isTrue();
        assertThat(get("/textbooks", learner).body().size()).isZero();
    }

    @Test
    void aReframedTopicBecomesTheTitleAndKeepsTheOriginalTopic() {
        String learner = login("reframe@test.dev");
        fakes.setCanned(Calls.TOPIC_GATE, """
                {"verdict": "REFRAME", "title": "How castles were defended",
                 "message": "We’ll look at how castles were built to keep people safe.", "categories": ["violence_weapons"]}""");

        Resp r = post("/textbooks", learner, Map.of("topic", "medieval siege weapons"));
        assertThat(r.status()).isEqualTo(201);
        var book = r.body().get("textbook");
        assertThat(book.get("title").asString()).isEqualTo("How castles were defended");
        assertThat(book.get("topic").asString()).isEqualTo("medieval siege weapons");
        assertThat(book.get("gateNote").asString()).contains("keep people safe");
    }

    @Test
    void aReframeWithoutAFramingIsTreatedAsADecline() {
        String learner = login("reframe-empty@test.dev");
        fakes.setCanned(Calls.TOPIC_GATE, """
                {"verdict": "REFRAME", "title": "", "message": "", "categories": []}""");
        Resp r = post("/textbooks", learner, Map.of("topic", "x"));
        assertThat(r.body().get("verdict").asString()).isEqualTo("DECLINE");
        assertThat(r.body().get("message").asString()).isNotBlank();
        assertThat(get("/textbooks", learner).body().size()).isZero();
    }

    @Test
    void theGateFailsClosedWhenNoProviderCanAnswer() {
        String learner = login("closed@test.dev");
        fakes.set(Provider.FAKE, FailureMode.SERVER_ERROR);
        Resp r = post("/textbooks", learner, Map.of("topic", "Bouldering"));
        assertThat(r.status()).isEqualTo(503);
        assertThat(r.body().get("detail").asString()).contains("nothing was created");
        assertThat(get("/textbooks", learner).body().size()).isZero();
    }

    @Test
    void theGateFailsClosedOnOutputThatNeverValidates() {
        String learner = login("garbled@test.dev");
        fakes.set(Provider.FAKE, FailureMode.INVALID_OUTPUT);
        assertThat(post("/textbooks", learner, Map.of("topic", "Bouldering")).status()).isEqualTo(503);
        assertThat(get("/textbooks", learner).body().size()).isZero();
    }

    @Test
    void topicsAreValidated() {
        String learner = login("valid@test.dev");
        assertThat(post("/textbooks", learner, Map.of("topic", "   ")).status()).isEqualTo(400);
        assertThat(post("/textbooks", learner, Map.of("topic", "x".repeat(121))).status()).isEqualTo(400);
        assertThat(post("/textbooks", learner, Map.of()).status()).isEqualTo(400);
        assertThat(jdbc.sql("SELECT count(*) FROM model_runs").query(Integer.class).single()).isZero();
    }
}
