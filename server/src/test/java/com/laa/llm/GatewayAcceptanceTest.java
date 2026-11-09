package com.laa.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.laa.IntegrationTest;

/** The acceptance tests from BRIEF section 5.5, run over HTTP against the real gateway. */
@ExtendWith(OutputCaptureExtension.class)
class GatewayAcceptanceTest extends IntegrationTest {

    private static final List<String> CANARY_KEYS = List.of(
            "LEAKCANARY-openai-7f3a9c1e", "LEAKCANARY-anthropic-2b8d4f60", "LEAKCANARY-google-91c0ab37");
    private static final String JWT_SECRET = "test-only-jwt-secret-0123456789abcdef-0123456789";

    @Test
    void changingTheDefaultProviderChangesTheProviderOfTheNextRun() {
        String owner = ownerToken();

        Resp first = ping(owner, "LESSON_PLAN");
        ok(first);
        assertThat(latestRuns(1).getFirst()).containsEntry("provider", "FAKE").containsEntry("status", "OK");

        ok(patch("/owner/providers/FAKE_B", owner, Map.of("isDefault", true)));

        Resp next = ping(owner, "LESSON_PLAN");
        ok(next);
        assertThat(next.body().get("provider").asString()).isEqualTo("FAKE_B");
        assertThat(latestRuns(1).getFirst()).containsEntry("provider", "FAKE_B").containsEntry("status", "OK")
                .containsEntry("model", "fake-b-large");
    }

    @Test
    void aPerTaskOverrideWinsOverTheDefault() {
        String owner = ownerToken();
        ok(patch("/owner/routes/LESSON_WRITE", owner, Map.of("providerChoice", "FAKE_B")));

        ok(ping(owner, "LESSON_WRITE"));
        assertThat(latestRuns(1).getFirst()).containsEntry("provider", "FAKE_B");
        ok(ping(owner, "LESSON_PLAN"));
        assertThat(latestRuns(1).getFirst()).containsEntry("provider", "FAKE");

        // Still wins after the default moves.
        ok(patch("/owner/routes/LESSON_WRITE", owner, Map.of("providerChoice", "FAKE")));
        ok(patch("/owner/providers/FAKE_B", owner, Map.of("isDefault", true)));
        ok(ping(owner, "LESSON_WRITE"));
        assertThat(latestRuns(1).getFirst()).containsEntry("provider", "FAKE");
        ok(ping(owner, "LESSON_PLAN"));
        assertThat(latestRuns(1).getFirst()).containsEntry("provider", "FAKE_B");
    }

    @ParameterizedTest
    @CsvSource({"SERVER_ERROR, SERVER_ERROR", "RATE_LIMIT, RATE_LIMIT", "PROVIDER_ERROR, PROVIDER_ERROR", "TIMEOUT, TIMEOUT"})
    void aFailingPrimaryFallsBackAndTheRunIsLoggedAsFallback(String mode, String expectedKind) {
        String owner = ownerToken();
        ok(patch("/owner/routes/LESSON_PLAN", owner,
                Map.of("fallbacks", List.of("FAKE_B"), "params", Map.of("maxTokens", 16000, "timeoutMs", 300))));
        ok(put("/dev/fake/FAKE/failure", owner, Map.of("mode", mode, "hangMs", 3000)));

        Resp r = ping(owner, "LESSON_PLAN");
        ok(r);
        assertThat(r.body().get("provider").asString()).isEqualTo("FAKE_B");
        assertThat(r.body().get("status").asString()).isEqualTo("FALLBACK");

        var runs = latestRuns(2);
        assertThat(runs.get(0)).containsEntry("provider", "FAKE_B").containsEntry("status", "FALLBACK").containsEntry("attempt", 2);
        assertThat(runs.get(1)).containsEntry("provider", "FAKE").containsEntry("status", "ERROR")
                .containsEntry("error_kind", expectedKind).containsEntry("attempt", 1);
    }

    @Test
    void assigningAModelWithoutToolCallingToResearchIsRejected() {
        String owner = ownerToken();

        Resp byTier = patch("/owner/routes/RESEARCH", owner, Map.of("modelTier", "SMALL"));
        assertThat(byTier.status()).isEqualTo(400);
        assertThat(byTier.raw()).contains("tool calling").contains("fake-small");

        Resp byModel = patch("/owner/routes/RESEARCH", owner, Map.of("providerChoice", "FAKE_B", "modelId", "fake-b-small"));
        assertThat(byModel.status()).isEqualTo(400);
        assertThat(byModel.raw()).contains("tool calling");

        // Nothing was saved.
        Resp routes = get("/owner/routes", owner);
        var research = routes.body().valueStream().filter(n -> n.get("task").asString().equals("RESEARCH")).findFirst().orElseThrow();
        assertThat(research.get("modelTier").asString()).isEqualTo("LARGE");
        assertThat(research.get("providerChoice").asString()).isEqualTo("DEFAULT");
    }

    @Test
    void noApiKeyAppearsInLogsOrApiResponses(CapturedOutput output) {
        String owner = ownerToken();
        List<String> bodies = new ArrayList<>();

        // Real adapters (OpenAI, Anthropic, Gemini) with canary keys, failing against a dead port, falling back to FAKE.
        bodies.add(patch("/owner/routes/LESSON_PLAN", owner, Map.of("providerChoice", "OPENAI", "fallbacks", List.of("FAKE"))).raw());
        bodies.add(patch("/owner/routes/GRADE", owner, Map.of("providerChoice", "ANTHROPIC", "fallbacks", List.of("FAKE"))).raw());
        bodies.add(patch("/owner/routes/TUTOR", owner, Map.of("providerChoice", "GOOGLE", "fallbacks", List.of("FAKE"))).raw());
        Resp viaOpenAi = ping(owner, "LESSON_PLAN");
        ok(viaOpenAi);
        bodies.add(viaOpenAi.raw());
        bodies.add(ping(owner, "GRADE").raw());
        bodies.add(pingStream(owner, "TUTOR").raw());
        bodies.add(post("/owner/compare", owner, Map.of("task", "GRADE", "input", Map.of("message", "hello"),
                "a", Map.of("provider", "OPENAI"), "b", Map.of("provider", "ANTHROPIC"))).raw());
        bodies.add(get("/owner/providers", owner).raw());
        bodies.add(get("/owner/routes", owner).raw());
        bodies.add(get("/owner/runs?limit=100", owner).raw());

        // The real adapters really were called and failed.
        var errors = jdbc.sql("SELECT provider FROM model_runs WHERE status = 'ERROR'").query(String.class).list();
        assertThat(errors).contains("OPENAI", "ANTHROPIC", "GOOGLE");

        // Belt and braces: even a secret logged on purpose is masked by the log converters.
        LoggerFactory.getLogger("leak-check").warn("simulated leak {} {}", CANARY_KEYS.getFirst(), JWT_SECRET);

        String everything = String.join("\n", bodies) + "\n" + output.getAll();
        for (String secret : CANARY_KEYS) assertThat(everything).doesNotContain(secret);
        assertThat(everything).doesNotContain(JWT_SECRET);
        assertThat(output.getAll()).contains("simulated leak [REDACTED] [REDACTED]");
    }
}
