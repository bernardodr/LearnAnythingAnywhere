package com.laa.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.laa.IntegrationTest;
import com.laa.llm.Calls;

import tools.jackson.databind.JsonNode;

class OwnerApiTest extends IntegrationTest {

    @Test
    void authAndRolesGuardTheOwnerApi() {
        assertThat(get("/owner/providers", null).status()).isEqualTo(401);
        assertThat(get("/owner/providers", login("learner@test.dev")).status()).isEqualTo(403);
        assertThat(get("/owner/providers", ownerToken()).status()).isEqualTo(200);
        assertThat(get("/owner/providers", "not-a-jwt").status()).isEqualTo(401);
        assertThat(post("/dev/llm/ping", login("learner@test.dev"), Map.of("task", "TUTOR")).status()).isEqualTo(403);
    }

    @Test
    void devLoginAssignsRoles() {
        Resp learner = post("/auth/dev-login", null, Map.of("email", "Ana@Test.dev"));
        ok(learner);
        assertThat(learner.body().get("user").get("role").asString()).isEqualTo("LEARNER");
        assertThat(learner.body().get("user").get("email").asString()).isEqualTo("ana@test.dev");

        assertThat(post("/auth/dev-login", null, Map.of("email", "sneaky@test.dev", "role", "OWNER")).status()).isEqualTo(403);
        assertThat(post("/auth/dev-login", null, Map.of("email", "not-an-email")).status()).isEqualTo(400);

        Resp owner = post("/auth/dev-login", null, Map.of("email", OWNER_EMAIL));
        assertThat(owner.body().get("user").get("role").asString()).isEqualTo("OWNER");
        Resp me = get("/auth/me", owner.body().get("token").asString());
        assertThat(me.body().get("role").asString()).isEqualTo("OWNER");
    }

    @Test
    void providersShowConfigurationWithoutSecrets() {
        Resp r = get("/owner/providers", ownerToken());
        ok(r);
        JsonNode fake = provider(r.body(), "FAKE");
        assertThat(fake.get("isDefault").asBoolean()).isTrue();
        assertThat(fake.get("tasksServed").asInt()).isEqualTo(Task.values().length);
        assertThat(provider(r.body(), "OPENAI").get("configured").asBoolean()).isTrue();
        assertThat(provider(r.body(), "OLLAMA").get("configured").asBoolean()).isFalse();
        assertThat(provider(r.body(), "OLLAMA").get("unavailableReason").asString()).contains("OLLAMA_BASE_URL");
        assertThat(r.raw()).doesNotContainIgnoringCase("apiKey").doesNotContain("LEAKCANARY");
    }

    @Test
    void providerChangesThatWouldBreakRoutingAreRejected() {
        String owner = ownerToken();
        Resp disableDefault = patch("/owner/providers/FAKE", owner, Map.of("enabled", false));
        assertThat(disableDefault.status()).isEqualTo(400);
        assertThat(disableDefault.raw()).contains("default provider");

        Resp unconfiguredDefault = patch("/owner/providers/OLLAMA", owner, Map.of("isDefault", true));
        assertThat(unconfiguredDefault.status()).isEqualTo(400);
        assertThat(unconfiguredDefault.raw()).contains("OLLAMA_BASE_URL");

        // A fallback on FAKE_B makes disabling FAKE_B a breaking change.
        ok(patch("/owner/routes/GRADE", owner, Map.of("fallbacks", List.of("FAKE_B"))));
        Resp disableFallback = patch("/owner/providers/FAKE_B", owner, Map.of("enabled", false));
        assertThat(disableFallback.status()).isEqualTo(400);
        assertThat(disableFallback.raw()).contains("FAKE_B is disabled");

        // Unused: fine.
        ok(patch("/owner/routes/GRADE", owner, Map.of("fallbacks", List.of())));
        ok(patch("/owner/providers/FAKE_B", owner, Map.of("enabled", false)));
        Resp routeToDisabled = patch("/owner/routes/GRADE", owner, Map.of("providerChoice", "FAKE_B"));
        assertThat(routeToDisabled.status()).isEqualTo(400);
    }

    @Test
    void routePatchValidatesItsInput() {
        String owner = ownerToken();
        assertThat(patch("/owner/routes/GRADE", owner, Map.of("colour", "red")).status()).isEqualTo(400);
        assertThat(patch("/owner/routes/GRADE", owner, Map.of("params", Map.of("seed", 1))).status()).isEqualTo(400);
        assertThat(patch("/owner/routes/GRADE", owner, Map.of("promptVersion", "v9")).status()).isEqualTo(400);
        assertThat(patch("/owner/routes/GRADE", owner, Map.of("modelId", "no-such-model")).status()).isEqualTo(400);
        assertThat(patch("/owner/routes/NOT_A_TASK", owner, Map.of("modelTier", "SMALL")).status()).isEqualTo(404);

        Resp okChange = patch("/owner/routes/GRADE", owner, Map.of("modelTier", "LARGE", "params", Map.of("temperature", 0.2)));
        ok(okChange);
        assertThat(okChange.body().get("resolvedModel").asString()).isEqualTo("fake-large");
        assertThat(okChange.body().get("problems").size()).isZero();
    }

    @Test
    void compareRunsBothRoutesSideBySideWithSchemaChecks() {
        String owner = ownerToken();
        Resp r = post("/owner/compare", owner, Map.of(
                "call", "GRADE",
                "input", Map.of("message", "The answer is 42."),
                "a", Map.of("provider", "FAKE"),
                "b", Map.of("provider", "FAKE_B", "modelTier", "LARGE")));
        ok(r);
        assertThat(r.body().get("mode").asString()).isEqualTo("structured:GradeResult");
        assertThat(r.body().get("a").get("model").asString()).isEqualTo("fake-small");
        assertThat(r.body().get("b").get("model").asString()).isEqualTo("fake-b-large");
        for (String side : List.of("a", "b")) {
            assertThat(r.body().get(side).get("ok").asBoolean()).isTrue();
            assertThat(r.body().get(side).get("checks").get("schemaValid").asBoolean()).isTrue();
            assertThat(r.body().get(side).get("parsed").get("verdict").asString()).isEqualTo("MET");
        }
        assertThat(latestRuns(2)).extracting(m -> m.get("origin")).containsOnly("COMPARE");

        Resp incapable = post("/owner/compare", owner, Map.of(
                "call", "RESEARCH", "input", Map.of("message", "x"),
                "a", Map.of("provider", "FAKE", "modelTier", "SMALL"), "b", Map.of("provider", "FAKE_B")));
        assertThat(incapable.status()).isEqualTo(400);
        assertThat(incapable.raw()).contains("web search");
    }

    @Test
    void compareCanRunEveryRegisteredCallIncludingTheSafetyChecks() {
        String owner = ownerToken();
        JsonNode calls = get("/owner/calls", owner).body();
        assertThat(calls.valueStream().map(c -> c.get("id").asString()).toList())
                .contains("SAFETY_CHECK.topic", "SAFETY_CHECK.content", "SAFETY_CHECK.leak", "MISSION_INTERVIEW", "TUTOR");
        JsonNode leak = calls.valueStream().filter(c -> c.get("id").asString().equals("SAFETY_CHECK.leak")).findFirst().orElseThrow();
        assertThat(leak.get("task").asString()).isEqualTo("SAFETY_CHECK");
        assertThat(leak.get("label").asString()).isEqualTo("Leak check");
        assertThat(leak.get("structured").asBoolean()).isTrue();

        Resp content = post("/owner/compare", owner, Map.of("call", "SAFETY_CHECK.content",
                "input", Map.of("message", "<item id=\"a\">\nHow volcanoes erupt.\n</item>"),
                "a", Map.of("provider", "FAKE"), "b", Map.of("provider", "FAKE_B")));
        ok(content);
        assertThat(content.body().get("mode").asString()).isEqualTo("structured:ModerationVerdict");
        assertThat(content.body().get("call").asString()).isEqualTo("SAFETY_CHECK.content");
        for (String side : List.of("a", "b")) {
            assertThat(content.body().get(side).get("checks").get("schemaValid").asBoolean()).isTrue();
            assertThat(content.body().get(side).get("parsed").get("items").get(0).get("verdict").asString()).isEqualTo("PASS");
        }

        assertThat(post("/owner/compare", owner, Map.of("call", "SAFETY_CHECK.nope", "input", Map.of("message", "x"),
                "a", Map.of("provider", "FAKE"), "b", Map.of("provider", "FAKE_B"))).status()).isEqualTo(400);
    }

    @Test
    void theRunLogSaysWhichCallEachRunWas() {
        String learner = login("run-log-call@test.dev");
        ok(post("/textbooks", learner, Map.of("topic", "Bouldering")));
        JsonNode runs = get("/owner/runs?task=SAFETY_CHECK", ownerToken()).body();
        assertThat(runs.get(0).get("call").asString()).isEqualTo("SAFETY_CHECK.topic");
    }

    @Test
    void compareChecksLessonQuizRulesAndReadingAge() {
        String owner = ownerToken();
        Resp r = post("/owner/compare", owner, Map.of(
                "call", "LESSON_WRITE",
                "input", Map.of("message", "Write lesson 2 from this plan: {\"title\": \"Small steps\"}"),
                "a", Map.of("provider", "FAKE"),
                "b", Map.of("provider", "FAKE_B")));
        ok(r);
        for (String side : List.of("a", "b")) {
            JsonNode checks = r.body().get(side).get("checks");
            assertThat(checks.get("schemaValid").asBoolean()).isTrue();
            assertThat(checks.get("quizRules").size()).as("the fake lesson passes the rules").isZero();
            assertThat(checks.get("readingAge").asDouble()).isBetween(5.0, 25.0);
            assertThat(checks.get("moderation").get("passed").asBoolean()).as("the fake lesson passes moderation").isTrue();
            assertThat(checks.get("moderation").get("flags").size()).isZero();
        }

        // A lesson that breaks the rules is flagged, not hidden.
        fakes.setCanned(Calls.LESSON_WRITE, """
                {"title": "T", "subtitle": "S", "win": "W", "referenceSheet": {"title": "R", "md": "- r"}, "rubrics": [],
                 "blocks": [
                   {"id": "h", "type": "HEADING", "style": "", "text": "Start", "items": [], "answer": -1, "explanation": "", "refs": []},
                   {"id": "t", "type": "TEXT", "style": "", "text": "Text.", "items": [], "answer": -1, "explanation": "", "refs": []},
                   {"id": "k", "type": "KEY_IDEA", "style": "", "text": "Idea.", "items": [], "answer": -1, "explanation": "", "refs": []},
                   {"id": "q", "type": "QUIZ", "style": "check", "text": "Which?", "items": ["Yes", "No", "All of the above"], "answer": 0, "explanation": "E", "refs": []},
                   {"id": "c", "type": "CALLOUT", "style": "note", "text": "Note.", "items": [], "answer": -1, "explanation": "", "refs": []}
                 ]}""");
        Resp broken = post("/owner/compare", owner, Map.of("call", "LESSON_WRITE", "input", Map.of("message", "x"),
                "a", Map.of("provider", "FAKE"), "b", Map.of("provider", "FAKE_B")));
        assertThat(broken.body().get("a").get("checks").get("quizRules").toString())
                .contains("aren't allowed").contains("FREE_RESPONSE").contains("warm-up");

        // Tasks without lessons don't get a quiz-rules verdict.
        Resp grade = post("/owner/compare", owner, Map.of("call", "GRADE", "input", Map.of("message", "x"),
                "a", Map.of("provider", "FAKE"), "b", Map.of("provider", "FAKE_B")));
        assertThat(grade.body().get("a").get("checks").get("quizRules").isNull()).isTrue();
    }

    private static JsonNode provider(JsonNode list, String id) {
        return list.valueStream().filter(n -> n.get("id").asString().equals(id)).findFirst().orElseThrow();
    }
}
