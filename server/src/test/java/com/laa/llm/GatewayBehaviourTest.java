package com.laa.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.laa.IntegrationTest;

/** Structured-output retry, streaming fallback and metering, beyond the 5.5 acceptance tests. */
class GatewayBehaviourTest extends IntegrationTest {

    @Test
    void invalidStructuredOutputIsRetriedOnceWithTheErrorsAndThenSucceeds() {
        String owner = ownerToken();
        ok(put("/dev/fake/FAKE/failure", owner, Map.of("mode", "INVALID_OUTPUT_ONCE")));

        Resp r = ping(owner, "GRADE");
        ok(r);
        assertThat(r.body().get("output").get("message").asString()).isEqualTo("[FAKE] message");
        var runs = latestRuns(5);
        assertThat(runs).hasSize(1); // one row per provider attempt; the retry is inside it
        assertThat(runs.getFirst()).containsEntry("status", "OK");
    }

    @Test
    void outputThatStaysInvalidFailsWithAClearErrorAndDoesNotFallBack() {
        String owner = ownerToken();
        ok(patch("/owner/routes/GRADE", owner, Map.of("fallbacks", List.of("FAKE_B"))));
        ok(put("/dev/fake/FAKE/failure", owner, Map.of("mode", "INVALID_OUTPUT")));

        Resp r = ping(owner, "GRADE");
        assertThat(r.status()).isEqualTo(502);
        assertThat(r.body().get("detail").asString()).contains("failed validation twice");
        assertThat(r.body().get("errors").toString()).contains("message");
        var runs = latestRuns(5);
        assertThat(runs).hasSize(1);
        assertThat(runs.getFirst()).containsEntry("provider", "FAKE").containsEntry("status", "ERROR")
                .containsEntry("error_kind", "INVALID_OUTPUT");
    }

    @Test
    void streamingRecordsTokensAndLatency() {
        String owner = ownerToken();
        Resp r = pingStream(owner, "TUTOR");
        ok(r);
        assertThat(r.body().get("output").asString()).startsWith("[FAKE] This is a deterministic TUTOR reply");
        var run = latestRuns(1).getFirst();
        assertThat(run).containsEntry("provider", "FAKE").containsEntry("status", "OK").containsEntry("model", "fake-small");
        assertThat((Integer) run.get("tokens_out")).isPositive();
    }

    @Test
    void streamFallsBackWhenThePrimaryFailsBeforeTheFirstToken() {
        String owner = ownerToken();
        ok(patch("/owner/routes/TUTOR", owner, Map.of("fallbacks", List.of("FAKE_B"))));
        ok(put("/dev/fake/FAKE/failure", owner, Map.of("mode", "RATE_LIMIT")));

        Resp r = pingStream(owner, "TUTOR");
        ok(r);
        assertThat(r.body().get("output").asString()).startsWith("[FAKE_B]");
        var runs = latestRuns(2);
        assertThat(runs.get(0)).containsEntry("provider", "FAKE_B").containsEntry("status", "FALLBACK");
        assertThat(runs.get(1)).containsEntry("provider", "FAKE").containsEntry("error_kind", "RATE_LIMIT");
    }

    @Test
    void streamFallsBackWhenThePrimaryNeverStarts() {
        String owner = ownerToken();
        ok(patch("/owner/routes/TUTOR", owner, Map.of("fallbacks", List.of("FAKE_B"))));
        ok(put("/dev/fake/FAKE/failure", owner, Map.of("mode", "TIMEOUT", "hangMs", 5000)));

        Resp r = pingStream(owner, "TUTOR");
        ok(r);
        assertThat(r.body().get("output").asString()).startsWith("[FAKE_B]");
        assertThat(latestRuns(2).get(1)).containsEntry("provider", "FAKE").containsEntry("error_kind", "TIMEOUT");
    }

    @Test
    void whenEveryProviderFailsTheCallerGets503() {
        String owner = ownerToken();
        ok(patch("/owner/routes/LESSON_PLAN", owner, Map.of("fallbacks", List.of("FAKE_B"))));
        ok(put("/dev/fake/FAKE/failure", owner, Map.of("mode", "SERVER_ERROR")));
        ok(put("/dev/fake/FAKE_B/failure", owner, Map.of("mode", "SERVER_ERROR")));

        Resp r = ping(owner, "LESSON_PLAN");
        assertThat(r.status()).isEqualTo(503);
        assertThat(r.body().get("detail").asString()).contains("no provider answered");
        assertThat(latestRuns(2)).extracting(m -> m.get("status")).containsOnly("ERROR");
    }

    @Test
    void runsAreMeteredWithOriginUserAndCostFields() {
        String owner = ownerToken();
        ok(ping(owner, "SAFETY_CHECK"));
        Resp runs = get("/owner/runs?task=SAFETY_CHECK&provider=FAKE&limit=5", owner);
        ok(runs);
        assertThat(runs.body().size()).isEqualTo(1);
        var run = runs.body().get(0);
        assertThat(run.get("origin").asString()).isEqualTo("DEV");
        assertThat(run.get("promptVersion").asString()).isEqualTo("v1");
        assertThat(run.get("userId").isNull()).isFalse();
        assertThat(run.get("latencyMs").asInt()).isGreaterThanOrEqualTo(0);
        assertThat(run.get("tokensIn").asInt()).isPositive();
    }
}
