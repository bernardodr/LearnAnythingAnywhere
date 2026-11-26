package com.laa.safety;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.laa.IntegrationTest;
import com.laa.llm.Calls;
import com.laa.mission.MissionInterview;

/**
 * The hard filter on em dashes (issue #80), end to end: whatever the AI writes, and whatever was stored before the
 * filter, no learner-facing response carries an em dash. Run with links Open, the policy the reader view used to skip.
 */
class NoEmDashesTest extends IntegrationTest {

    private static final String EM = "—";

    @Test
    void noLearnerEverReadsAnEmDashNewOrOld() {
        String learner = login("dashes@test.dev");
        String profileId = get("/profiles/me", learner).body().get("id").asString();
        Map<String, Object> open = new HashMap<>();
        open.put("childSafe", false);
        open.put("linksPolicy", "OPEN");
        open.put("revealAfterAttempts", 1);
        ok(put("/profiles/" + profileId, learner, open));
        String id = post("/textbooks", learner, Map.of("topic", "Bouldering")).body().get("textbook").get("id").asString();
        String mission = "/textbooks/" + id + "/mission";

        // The interviewer's streamed reply, and the mission draft, both written with em dashes.
        fakes.setCannedText(Calls.INTERVIEW_TURN, "Climbing — what draws you to it? Tell me—honestly." );
        SseResp first = sse(mission + "/messages", learner, null);
        ok(first);
        assertThat(first.named("token").getFirst().data().get("text").asString())
                .isEqualTo("Climbing, what draws you to it? Tell me, honestly.");
        fakes.setCannedText(Calls.INTERVIEW_TURN, "Thanks — that helps. " + MissionInterview.MARKER);
        fakes.setCanned(Calls.MISSION_DRAFT, JSON.writeValueAsString(Map.of("headline", "Climb a V3 — with friends",
                "why", "Because climbing — like chess — is a puzzle.", "successLooksLike", List.of("A clean V3 — no falls"),
                "constraints", List.of("Two evenings a week — at most"), "outOfScope", List.of(), "motif", "MOUNTAIN")));
        SseResp answer = sse(mission + "/messages", learner, Map.of("content", "I love it — truly."));
        ok(answer);
        assertThat(answer.raw()).doesNotContain(EM);
        assertThat(get(mission, learner).raw()).as("the stored preface, as shown").doesNotContain(EM)
                .contains("Climb a V3, with friends", "Because climbing, like chess, is a puzzle.", "I love it, truly.");

        // Lesson 1, then text with em dashes planted straight into the database: as if written before the filter.
        ok(post(mission + "/confirm", learner, Map.of("draft", JSON.convertValue(get(mission, learner).body().get("draft"), Map.class))));
        String job = post("/textbooks/" + id + "/lessons/next", learner, null).body().get("jobId").asString();
        assertThat(sseGet("/jobs/" + job + "/events", learner).raw()).doesNotContain(EM);
        jdbc.sql("UPDATE lessons SET title = 'Hold on — gently', subtitle = 'Feet first—always' WHERE textbook_id = :id::uuid")
                .param("id", id).update();
        String lesson = get("/textbooks/" + id + "/lessons/1", learner).raw();
        assertThat(lesson).doesNotContain(EM).contains("Hold on, gently", "Feet first, always");
        assertThat(get("/textbooks/" + id + "/contents", learner).raw()).doesNotContain(EM).contains("Hold on, gently");
    }
}
