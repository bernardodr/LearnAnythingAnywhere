package com.laa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import com.laa.auth.Throttle;
import com.laa.llm.provider.fake.FakeBehaviour;
import com.laa.llm.routing.RoutingSeeder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base for HTTP-level tests: one shared context + PostgreSQL container. Each test starts from freshly
 * seeded routing (FAKE default, every task on DEFAULT, no fallbacks) and well-behaved fakes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
public abstract class IntegrationTest {

    protected static final String OWNER_EMAIL = "owner@test.dev";
    /** Passes the password rules; for accounts a test signs into with a password. */
    protected static final String PASSWORD = "copper kettle north meadow";
    protected static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    protected int port;
    @Autowired
    protected JdbcClient jdbc;
    @Autowired
    protected RoutingSeeder seeder;
    @Autowired
    protected FakeBehaviour fakes;
    @Autowired
    protected Throttle throttle;

    protected RestClient http;

    public record Resp(int status, JsonNode body, String raw) {}

    @BeforeEach
    void resetGatewayState() {
        jdbc.sql("DELETE FROM textbooks").update(); // cascades to workspace files and chat
        // Reader settings start fresh too (adult defaults on first use), so one test's settings never reach another's.
        jdbc.sql("UPDATE users SET profile_id = NULL, pin_hash = NULL").update();
        jdbc.sql("DELETE FROM generation_profiles").update();
        jdbc.sql("DELETE FROM model_runs").update();
        jdbc.sql("DELETE FROM model_routes").update();
        jdbc.sql("DELETE FROM providers").update();
        seeder.seed();
        fakes.reset();
        throttle.reset(); // every test comes from the same address
        http = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    protected Resp call(HttpMethod method, String path, String token, Object body) {
        return call(method, path, token, body, Map.of());
    }

    protected Resp call(HttpMethod method, String path, String token, Object body, Map<String, String> headers) {
        var spec = http.method(method).uri(path);
        if (token != null) spec = spec.header("Authorization", "Bearer " + token);
        for (var h : headers.entrySet()) spec = spec.header(h.getKey(), h.getValue());
        if (body != null) spec = spec.contentType(MediaType.APPLICATION_JSON).body(body);
        ResponseEntity<String> res = spec.retrieve().onStatus(s -> true, (req, r) -> { }).toEntity(String.class);
        String raw = res.getBody() == null ? "" : res.getBody();
        JsonNode node = raw.isBlank() ? JSON.nullNode() : JSON.readTree(raw);
        return new Resp(res.getStatusCode().value(), node, raw);
    }

    protected Resp get(String path, String token) {
        return call(HttpMethod.GET, path, token, null);
    }

    protected Resp post(String path, String token, Object body) {
        return call(HttpMethod.POST, path, token, body);
    }

    protected Resp patch(String path, String token, Object body) {
        return call(HttpMethod.PATCH, path, token, body);
    }

    protected Resp put(String path, String token, Object body) {
        return call(HttpMethod.PUT, path, token, body);
    }

    protected String login(String email) {
        Resp r = post("/auth/dev-login", null, Map.of("email", email));
        assertThat(r.status()).as(r.raw()).isEqualTo(200);
        return r.body().get("token").asString();
    }

    /** Signs in as a parent (a new email each time) and adds a child; returns both tokens and the child's profile id. */
    protected Family family(int childAge, boolean childSafe) {
        String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        Resp p = post("/auth/dev-login", null, Map.of("email", "parent-" + suffix + "@test.dev", "role", "PARENT"));
        assertThat(p.status()).as(p.raw()).isEqualTo(200);
        String parent = p.body().get("token").asString();
        String childEmail = "child-" + suffix + "@test.dev";
        Resp c = post("/profiles/children", parent, Map.of("email", childEmail, "displayName", "Sam", "age", childAge,
                "childSafe", childSafe, "password", PASSWORD));
        assertThat(c.status()).as(c.raw()).isEqualTo(201);
        return new Family(parent, login(childEmail), c.body().get("profile").get("id").asString());
    }

    protected record Family(String parent, String child, String childProfileId) {}

    /**
     * Routes SAFETY_CHECK to FAKE_B, so a test can make FAKE fail (an interviewer, grader or tutor outage) while the
     * safety check that runs before it still answers.
     */
    protected void safetyCheckOnFakeB() {
        ok(patch("/owner/routes/SAFETY_CHECK", ownerToken(), Map.of("providerChoice", "FAKE_B")));
    }

    protected String ownerToken() {
        return login(OWNER_EMAIL);
    }

    /** Structured gateway call through the dev endpoint. */
    protected Resp ping(String token, String task) {
        return post("/dev/llm/ping", token, Map.of("task", task));
    }

    protected Resp pingStream(String token, String task) {
        return post("/dev/llm/ping", token, Map.of("task", task, "stream", true));
    }

    public record SseEvent(String name, JsonNode data) {}

    /** A server-sent-events response read to the end; {@code events} is empty when the server answered with JSON. */
    public record SseResp(int status, List<SseEvent> events, String raw) {
        public List<SseEvent> named(String name) {
            return events.stream().filter(e -> e.name().equals(name)).toList();
        }

        /** The streamed text: every {@code token} event's text, in order. */
        public String text() {
            return named("token").stream().map(e -> e.data().get("text").asString()).collect(Collectors.joining());
        }
    }

    /** {@code GET} a server-sent-events endpoint (job progress) and read it to the end. */
    protected SseResp sseGet(String path, String token) {
        return sse(HttpMethod.GET, path, token, null);
    }

    protected SseResp sse(String path, String token, Object body) {
        return sse(HttpMethod.POST, path, token, body);
    }

    private SseResp sse(HttpMethod method, String path, String token, Object body) {
        var spec = http.method(method).uri(path).header("Accept", "text/event-stream, application/json");
        if (token != null) spec = spec.header("Authorization", "Bearer " + token);
        if (body != null) spec = spec.contentType(MediaType.APPLICATION_JSON).body(body);
        // Event streams are always UTF-8 (SSE spec), whatever charset the header names, so decode the bytes as a real client does.
        ResponseEntity<byte[]> res = spec.retrieve().onStatus(s -> true, (req, r) -> { }).toEntity(byte[].class);
        String raw = res.getBody() == null ? "" : new String(res.getBody(), java.nio.charset.StandardCharsets.UTF_8);
        List<SseEvent> events = new ArrayList<>();
        boolean isStream = res.getHeaders().getContentType() != null
                && res.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_EVENT_STREAM);
        if (isStream) {
            for (String block : raw.split("\\r?\\n\\r?\\n")) {
                String name = "message";
                StringBuilder data = new StringBuilder();
                for (String line : block.split("\\r?\\n")) {
                    if (line.startsWith("event:")) name = line.substring(6).strip();
                    else if (line.startsWith("data:")) data.append(line.substring(5).strip());
                }
                if (!data.isEmpty()) events.add(new SseEvent(name, JSON.readTree(data.toString())));
            }
        }
        return new SseResp(res.getStatusCode().value(), events, raw);
    }

    /** A 2xx stream that finished its turn with no error event. */
    protected void ok(SseResp r) {
        assertThat(r.status()).as(r.raw()).isBetween(200, 299);
        assertThat(r.named("error")).as(r.raw()).isEmpty();
        assertThat(r.named("done")).as(r.raw()).hasSize(1);
    }

    protected void ok(Resp r) {
        assertThat(r.status()).as(r.raw()).isBetween(200, 299);
    }

    /** Newest model_runs rows first. */
    protected java.util.List<Map<String, Object>> latestRuns(int n) {
        return jdbc.sql("SELECT provider, model, status, error_kind, attempt, origin, tokens_in, tokens_out, error_message "
                        + "FROM model_runs ORDER BY created_at DESC LIMIT :n")
                .param("n", n).query().listOfRows();
    }
}
