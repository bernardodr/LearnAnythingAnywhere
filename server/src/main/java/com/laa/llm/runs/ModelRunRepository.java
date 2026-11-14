package com.laa.llm.runs;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.laa.llm.Provider;
import com.laa.llm.Task;
import com.laa.security.SecretRedactor;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class ModelRunRepository {

    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public ModelRunRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public UUID insert(RunRecord r) {
        String message = r.errorMessage() == null ? null : truncate(SecretRedactor.redact(r.errorMessage()), 1000);
        return jdbc.sql("""
                INSERT INTO model_runs (task, provider, model, prompt_version, route_id, origin, attempt,
                                        tokens_in, tokens_out, cost_estimate, latency_ms, status, error_kind,
                                        error_message, user_id, textbook_id, call)
                VALUES (:task, :provider, :model, :promptVersion, :routeId, :origin, :attempt,
                        :tokensIn, :tokensOut, :cost, :latency, :status, :errorKind, :errorMessage, :userId, :textbookId, :call)
                RETURNING id""")
                .param("task", r.task().name())
                .param("provider", r.provider().name())
                .param("model", r.model())
                .param("promptVersion", r.promptVersion())
                .param("routeId", r.routeId())
                .param("origin", r.origin().name())
                .param("attempt", r.attempt())
                .param("tokensIn", r.tokensIn())
                .param("tokensOut", r.tokensOut())
                .param("cost", r.costEstimate())
                .param("latency", (int) Math.min(Integer.MAX_VALUE, r.latencyMs()))
                .param("status", r.status().name())
                .param("errorKind", r.errorKind() == null ? null : r.errorKind().name())
                .param("errorMessage", message)
                .param("userId", r.userId())
                .param("textbookId", r.textbookId())
                .param("call", r.call())
                .query(UUID.class)
                .single();
    }

    /** A run whose output the safety layer held back: status BLOCKED, with what was flagged. */
    public void markBlocked(UUID runId, List<String> flags) {
        List<String> clean = flags.stream().map(f -> truncate(SecretRedactor.redact(f), 300)).toList();
        jdbc.sql("UPDATE model_runs SET status = 'BLOCKED', safety_flags = :f::jsonb WHERE id = :id")
                .param("f", json.writeValueAsString(clean)).param("id", runId).update();
    }

    /** Newest first. Null filters are ignored. */
    public List<ModelRun> recent(Task task, Provider provider, int limit) {
        return jdbc.sql("""
                SELECT id, task, provider, model, prompt_version, route_id, origin, attempt, tokens_in, tokens_out,
                       cost_estimate, latency_ms, status, error_kind, error_message, safety_flags::text AS safety_flags,
                       user_id, textbook_id, created_at, call
                FROM model_runs
                WHERE (CAST(:task AS text) IS NULL OR task = :task)
                  AND (CAST(:provider AS text) IS NULL OR provider = :provider)
                ORDER BY created_at DESC
                LIMIT :limit""")
                .param("task", task == null ? null : task.name())
                .param("provider", provider == null ? null : provider.name())
                .param("limit", limit)
                .query((rs, i) -> new ModelRun(
                        rs.getObject("id", UUID.class),
                        rs.getString("task"),
                        rs.getString("provider"),
                        rs.getString("model"),
                        rs.getString("prompt_version"),
                        rs.getObject("route_id", UUID.class),
                        rs.getString("origin"),
                        rs.getInt("attempt"),
                        (Integer) rs.getObject("tokens_in"),
                        (Integer) rs.getObject("tokens_out"),
                        rs.getBigDecimal("cost_estimate"),
                        rs.getInt("latency_ms"),
                        rs.getString("status"),
                        rs.getString("error_kind"),
                        rs.getString("error_message"),
                        json.readValue(rs.getString("safety_flags"), STRINGS),
                        rs.getObject("user_id", UUID.class),
                        rs.getObject("textbook_id", UUID.class),
                        rs.getObject("created_at", java.time.OffsetDateTime.class),
                        rs.getString("call")))
                .list();
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
