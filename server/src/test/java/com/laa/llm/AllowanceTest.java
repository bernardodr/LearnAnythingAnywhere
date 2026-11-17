package com.laa.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.laa.IntegrationTest;

/**
 * Who may spend on AI: a verified email for a learner's own requests, the learner's daily cap, and the total daily
 * cap (defaults: $1 and $25). Spend comes from the run log, so tests write past spend straight into it.
 */
class AllowanceTest extends IntegrationTest {

    @Test
    void anUnverifiedAccountCanLookAroundButNotStartATextbook() {
        String email = "allow-" + UUID.randomUUID().toString().substring(0, 8) + "@test.dev";
        String token = post("/auth/sign-up", null, Map.of("email", email, "password", PASSWORD)).body().get("token").asString();
        assertThat(get("/textbooks", token).status()).isEqualTo(200);

        Resp blocked = post("/textbooks", token, Map.of("topic", "Bouldering"));
        assertThat(blocked.status()).isEqualTo(403);
        assertThat(blocked.body().get("code").asString()).isEqualTo("email-unverified");
        assertThat(blocked.body().get("detail").asString()).contains("Confirm your email");
        assertThat(latestRuns(5)).as("nothing reached a model").isEmpty();

        ok(post("/auth/verify-email", null, Map.of("token", emailedToken(email, "/verify-email"))));
        assertThat(post("/textbooks", token, Map.of("topic", "Bouldering")).status()).isEqualTo(201);
    }

    @Test
    void aLearnerWhoHasSpentTheirDailyCapIsToldSo() {
        String token = login("allow-cap@test.dev");
        UUID me = UUID.fromString(get("/auth/me", token).body().get("id").asString());
        spent(me, "APP", "1.00");

        Resp capped = post("/textbooks", token, Map.of("topic", "Bouldering"));
        assertThat(capped.status()).isEqualTo(429);
        assertThat(capped.body().get("code").asString()).isEqualTo("learner-daily-limit");
        assertThat(capped.body().get("detail").asString()).contains("today’s AI allowance");
        assertThat(post("/textbooks", login("allow-other@test.dev"), Map.of("topic", "Bouldering")).status())
                .as("other learners carry on").isEqualTo(201);
    }

    @Test
    void yesterdaysSpendAndTheOwnersComparisonsDontCountTowardALearnersCap() {
        String token = login("allow-yesterday@test.dev");
        UUID me = UUID.fromString(get("/auth/me", token).body().get("id").asString());
        spent(me, "APP", "5.00");
        jdbc.sql("UPDATE model_runs SET created_at = now() - interval '1 day'").update();
        spent(me, "COMPARE", "2.00");
        assertThat(post("/textbooks", token, Map.of("topic", "Bouldering")).status()).isEqualTo(201);
    }

    @Test
    void atTheTotalCapAIPausesForEveryoneTheOwnerIncluded() {
        spent(null, "APP", "25.00");
        Resp learner = post("/textbooks", login("allow-total@test.dev"), Map.of("topic", "Bouldering"));
        assertThat(learner.status()).isEqualTo(503);
        assertThat(learner.body().get("code").asString()).isEqualTo("total-daily-limit");
        assertThat(learner.body().get("detail").asString()).contains("today’s limit");
        assertThat(ping(ownerToken(), "LESSON_PLAN").status()).isEqualTo(503);
    }

    private void spent(UUID userId, String origin, String usd) {
        jdbc.sql("INSERT INTO model_runs (task, provider, model, latency_ms, status, origin, cost_estimate, user_id, call) "
                        + "VALUES ('LESSON_WRITE', 'FAKE', 'fake-large', 1, 'OK', :o, :c, :u, 'LESSON_WRITE')")
                .param("o", origin).param("c", new BigDecimal(usd)).param("u", userId).update();
    }
}
