package com.laa.mission;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.laa.IntegrationTest;
import com.laa.llm.Provider;
import com.laa.llm.Task;
import com.laa.llm.provider.fake.FailureMode;

/** The preface: streamed mission interview, mission card, MISSION.md (BRIEF section 6.2, milestone M2). */
class MissionInterviewTest extends IntegrationTest {

    private static final Map<String, Object> EDITED = Map.of(
            "headline", "Climb a V3 by spring",
            "why", "You want to climb V3 problems with your friends by spring.",
            "successLooksLike", List.of("Send a V3 at the gym", "Read a problem before climbing it"),
            "constraints", List.of("Two sessions a week"),
            "outOfScope", List.of());

    private static final String EXPECTED_MISSION_MD = """
            # Mission: Bouldering

            ## Why
            You want to climb V3 problems with your friends by spring.

            ## Success looks like
            - Send a V3 at the gym
            - Read a problem before climbing it

            ## Constraints
            - Two sessions a week

            ## Out of scope
            - None stated yet
            """;

    @Test
    void aFullPrefaceEndsInAMissionCardAndAnExactMissionMd() {
        String learner = login("preface@test.dev");
        String id = newTextbook(learner, "Bouldering");
        String base = "/textbooks/" + id + "/mission";

        // Opening question: no answer yet.
        SseResp open = sse(base + "/messages", learner, null);
        assertThat(open.status()).as(open.raw()).isEqualTo(200);
        assertThat(open.text()).startsWith("[FAKE] Hello!");
        assertThat(open.named("done").getFirst().data().get("interviewComplete").asBoolean()).isFalse();
        assertThat(open.named("draft")).isEmpty();

        SseResp first = sse(base + "/messages", learner, Map.of("content", "I want to climb V3 with my friends by spring."));
        assertThat(first.text()).contains("What would success look like");
        assertThat(first.named("token")).hasSizeGreaterThan(1); // really streamed, not one blob

        SseResp last = sse(base + "/messages", learner, Map.of("content", "Sending a V3 at my gym, twice a week."));
        assertThat(last.text()).isEqualTo("[FAKE] Thank you, I have what I need to draft your mission.");
        assertThat(last.raw()).doesNotContain(MissionInterview.MARKER);
        var draft = last.named("draft").getFirst().data();
        assertThat(draft.get("why").asString()).contains("climb V3 with my friends"); // built from the learner's answers
        assertThat(last.named("done").getFirst().data().get("interviewComplete").asBoolean()).isTrue();
        // Events arrive in order: text, then the card, then done.
        assertThat(last.events().getLast().name()).isEqualTo("done");

        Resp state = get(base, learner);
        ok(state);
        assertThat(state.body().get("answers").asInt()).isEqualTo(2);
        assertThat(state.body().get("messages").size()).isEqualTo(5);
        assertThat(state.body().get("messages").get(4).get("content").asString()).doesNotContain(MissionInterview.MARKER);
        assertThat(state.body().get("draft").get("headline").asString()).isNotBlank();

        // Once the card is up, edits happen on the card, not in the chat.
        assertThat(sse(base + "/messages", learner, Map.of("content", "one more thing")).status()).isEqualTo(409);

        // "Write my textbook" with the learner's edits.
        Resp confirmed = post(base + "/confirm", learner, Map.of("draft", EDITED));
        ok(confirmed);
        assertThat(confirmed.body().get("content").asString()).isEqualTo(EXPECTED_MISSION_MD);
        assertThat(confirmed.body().get("version").asInt()).isEqualTo(1);

        Resp file = get("/textbooks/" + id + "/files/MISSION.md", learner);
        ok(file);
        assertThat(file.body().get("content").asString()).isEqualTo(EXPECTED_MISSION_MD);
        Resp book = get("/textbooks/" + id, learner);
        assertThat(book.body().get("status").asString()).isEqualTo("ACTIVE");
        assertThat(book.body().get("missionHeadline").asString()).isEqualTo("Climb a V3 by spring");
        assertThat(jdbc.sql("SELECT count(*) FROM workspace_file_versions WHERE path = 'MISSION.md'")
                .query(Integer.class).single()).isEqualTo(1);

        assertThat(post(base + "/confirm", learner, Map.of("draft", EDITED)).status()).isEqualTo(409);
        assertThat(sse(base + "/messages", learner, Map.of("content", "hi")).status()).isEqualTo(409);

        // Three streamed turns and one structured draft, all metered against this textbook.
        var runs = jdbc.sql("SELECT status, textbook_id::text AS tb FROM model_runs WHERE task = 'MISSION_INTERVIEW'")
                .query().listOfRows();
        assertThat(runs).hasSize(4).allSatisfy(r -> assertThat(r).containsEntry("status", "OK").containsEntry("tb", id));
    }

    @Test
    void theInterviewEndsAfterFiveAnswersEvenIfTheModelKeepsAsking() {
        String learner = login("cap@test.dev");
        String id = newTextbook(learner, "Sourdough");
        String base = "/textbooks/" + id + "/mission";
        fakes.setCannedText(Task.MISSION_INTERVIEW, "And what else would you like to know about it?");

        ok(sse(base + "/messages", learner, null));
        for (int answer = 1; answer <= 4; answer++) {
            SseResp turn = sse(base + "/messages", learner, Map.of("content", "Answer " + answer));
            assertThat(turn.named("draft")).as("answer %d", answer).isEmpty();
        }
        SseResp fifth = sse(base + "/messages", learner, Map.of("content", "Answer 5"));
        assertThat(fifth.named("draft")).hasSize(1);
        assertThat(fifth.named("done").getFirst().data().get("interviewComplete").asBoolean()).isTrue();
    }

    @Test
    void aMarkerBeforeTheFirstAnswerIsIgnored() {
        String learner = login("early@test.dev");
        String id = newTextbook(learner, "Chess");
        fakes.setCannedText(Task.MISSION_INTERVIEW, "Hi! Why chess? " + MissionInterview.MARKER);

        SseResp open = sse("/textbooks/" + id + "/mission/messages", learner, null);
        assertThat(open.text()).isEqualTo("Hi! Why chess?");
        assertThat(open.named("draft")).isEmpty();
        assertThat(open.named("done").getFirst().data().get("interviewComplete").asBoolean()).isFalse();
    }

    @Test
    void aFailedReplySavesNothingSoTheSameAnswerCanBeResent() {
        String learner = login("retry@test.dev");
        String id = newTextbook(learner, "Bouldering");
        String base = "/textbooks/" + id + "/mission";
        ok(sse(base + "/messages", learner, null));

        safetyCheckOnFakeB();
        fakes.set(Provider.FAKE, FailureMode.SERVER_ERROR);
        SseResp failed = sse(base + "/messages", learner, Map.of("content", "To climb with friends."));
        assertThat(failed.status()).isEqualTo(200);
        assertThat(failed.named("error").getFirst().data().get("message").asString()).contains("send it again");
        assertThat(failed.named("done")).isEmpty();
        assertThat(get(base, learner).body().get("messages").size()).isEqualTo(1);

        fakes.reset();
        SseResp again = sse(base + "/messages", learner, Map.of("content", "To climb with friends."));
        assertThat(again.named("done")).hasSize(1);
        assertThat(get(base, learner).body().get("messages").size()).isEqualTo(3);
    }

    @Test
    void aDraftThatFailsCanBeRetriedWithoutRedoingTheInterview() {
        String learner = login("draft-retry@test.dev");
        String id = newTextbook(learner, "Bouldering");
        String base = "/textbooks/" + id + "/mission";
        fakes.setCannedText(Task.MISSION_INTERVIEW, "Got it, thanks! " + MissionInterview.MARKER);
        ok(sse(base + "/messages", learner, null));

        safetyCheckOnFakeB();
        fakes.set(Provider.FAKE, FailureMode.INVALID_OUTPUT); // streams still work; structured output doesn't validate
        SseResp turn = sse(base + "/messages", learner, Map.of("content", "I want to climb a V3."));
        assertThat(turn.named("draft_failed")).hasSize(1);
        assertThat(turn.named("done").getFirst().data().get("interviewComplete").asBoolean()).isTrue();
        assertThat(get(base, learner).body().get("draft").isNull()).isTrue();

        fakes.set(Provider.FAKE, FailureMode.NONE);
        Resp draft = post(base + "/draft", learner, null);
        ok(draft);
        assertThat(draft.body().get("why").asString()).contains("climb a V3");
        assertThat(get(base, learner).body().get("draft").get("why").asString()).contains("climb a V3");
    }

    @Test
    void turnsAndConfirmationAreValidated() {
        String learner = login("checks@test.dev");
        String id = newTextbook(learner, "Bouldering");
        String base = "/textbooks/" + id + "/mission";

        assertThat(sse(base + "/messages", learner, Map.of("content", "too early")).status()).isEqualTo(400);
        assertThat(post(base + "/confirm", learner, Map.of("draft", EDITED)).status()).isEqualTo(409); // no draft yet
        assertThat(post(base + "/draft", learner, null).status()).isEqualTo(409);                       // no answers yet

        ok(sse(base + "/messages", learner, null));
        assertThat(sse(base + "/messages", learner, null).status()).isEqualTo(400);                     // answer required
        assertThat(sse(base + "/messages", learner, Map.of("content", "x".repeat(2001))).status()).isEqualTo(400);

        Map<String, Object> noWhy = new java.util.HashMap<>(EDITED);
        noWhy.put("why", " ");
        assertThat(post(base + "/confirm", learner, Map.of("draft", noWhy)).status()).isEqualTo(400);
        Map<String, Object> noSuccess = new java.util.HashMap<>(EDITED);
        noSuccess.put("successLooksLike", List.of());
        assertThat(post(base + "/confirm", learner, Map.of("draft", noSuccess)).status()).isEqualTo(400);
    }

    private String newTextbook(String token, String topic) {
        Resp r = post("/textbooks", token, Map.of("topic", topic));
        assertThat(r.status()).as(r.raw()).isEqualTo(201);
        return r.body().get("textbook").get("id").asString();
    }
}
