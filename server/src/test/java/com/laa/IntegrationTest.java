package com.laa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

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
    protected static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    protected int port;
    @Autowired
    protected JdbcClient jdbc;
    @Autowired
    protected RoutingSeeder seeder;
    @Autowired
    protected FakeBehaviour fakes;

    protected RestClient http;

    public record Resp(int status, JsonNode body, String raw) {}

    @BeforeEach
    void resetGatewayState() {
        jdbc.sql("DELETE FROM model_runs").update();
        jdbc.sql("DELETE FROM model_routes").update();
        jdbc.sql("DELETE FROM providers").update();
        seeder.seed();
        fakes.reset();
        http = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    protected Resp call(HttpMethod method, String path, String token, Object body) {
        var spec = http.method(method).uri(path);
        if (token != null) spec = spec.header("Authorization", "Bearer " + token);
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
