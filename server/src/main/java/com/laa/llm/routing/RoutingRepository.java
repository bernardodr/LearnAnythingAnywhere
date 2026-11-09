package com.laa.llm.routing;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.laa.llm.ModelInfo;
import com.laa.llm.ModelTier;
import com.laa.llm.Provider;
import com.laa.llm.Task;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class RoutingRepository {

    private static final TypeReference<List<ModelInfo>> MODELS = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};
    private static final TypeReference<List<Provider>> PROVIDERS = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public RoutingRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public Map<Provider, ProviderRecord> providers() {
        Map<Provider, ProviderRecord> out = new EnumMap<>(Provider.class);
        jdbc.sql("SELECT id, enabled, is_default, models::text AS models FROM providers")
                .query((rs, i) -> new ProviderRecord(
                        Provider.valueOf(rs.getString("id")),
                        rs.getBoolean("enabled"),
                        rs.getBoolean("is_default"),
                        json.readValue(rs.getString("models"), MODELS)))
                .list()
                .forEach(p -> out.put(p.id(), p));
        return out;
    }

    public Map<Task, RouteRecord> activeRoutes() {
        Map<Task, RouteRecord> out = new EnumMap<>(Task.class);
        jdbc.sql("""
                SELECT id, task, provider_choice, model_tier, model_id, params::text AS params, prompt_version,
                       fallbacks::text AS fallbacks, traffic_split::text AS traffic_split
                FROM model_routes WHERE active""")
                .query(this::route)
                .list()
                .forEach(r -> out.put(r.task(), r));
        return out;
    }

    /** Model list and capability summary always come from config; {@code enabled} only seeds new rows. */
    public void upsertProviderCatalog(Provider id, boolean enabledIfNew, List<ModelInfo> models, Map<String, Object> capabilities) {
        jdbc.sql("""
                INSERT INTO providers (id, enabled, is_default, capabilities, models)
                VALUES (:id, :enabled, false, :caps::jsonb, :models::jsonb)
                ON CONFLICT (id) DO UPDATE SET capabilities = EXCLUDED.capabilities, models = EXCLUDED.models""")
                .param("id", id.name())
                .param("enabled", enabledIfNew)
                .param("caps", json.writeValueAsString(capabilities))
                .param("models", json.writeValueAsString(models))
                .update();
    }

    /** Two statements so the single-default unique index never sees two defaults. Call inside a transaction. */
    public void setDefault(Provider id) {
        jdbc.sql("UPDATE providers SET is_default = false WHERE is_default AND id <> :id").param("id", id.name()).update();
        jdbc.sql("UPDATE providers SET is_default = true WHERE id = :id").param("id", id.name()).update();
    }

    public void setEnabled(Provider id, boolean enabled) {
        jdbc.sql("UPDATE providers SET enabled = :enabled WHERE id = :id")
                .param("enabled", enabled).param("id", id.name()).update();
    }

    public void insertRoute(RouteRecord r) {
        jdbc.sql("""
                INSERT INTO model_routes (task, provider_choice, model_tier, model_id, params, prompt_version, fallbacks, traffic_split, active)
                VALUES (:task, :choice, :tier, :modelId, :params::jsonb, :promptVersion, :fallbacks::jsonb, :traffic::jsonb, true)""")
                .param("task", r.task().name())
                .param("choice", r.followsDefault() ? "DEFAULT" : r.providerChoice().name())
                .param("tier", r.tier().name())
                .param("modelId", r.modelId())
                .param("params", json.writeValueAsString(r.params()))
                .param("promptVersion", r.promptVersion())
                .param("fallbacks", json.writeValueAsString(r.fallbacks()))
                .param("traffic", r.trafficSplit() == null ? null : json.writeValueAsString(r.trafficSplit()))
                .update();
    }

    public void updateRoute(RouteRecord r) {
        jdbc.sql("""
                UPDATE model_routes SET provider_choice = :choice, model_tier = :tier, model_id = :modelId,
                       params = :params::jsonb, prompt_version = :promptVersion, fallbacks = :fallbacks::jsonb
                WHERE id = :id""")
                .param("id", r.id())
                .param("choice", r.followsDefault() ? "DEFAULT" : r.providerChoice().name())
                .param("tier", r.tier().name())
                .param("modelId", r.modelId())
                .param("params", json.writeValueAsString(r.params()))
                .param("promptVersion", r.promptVersion())
                .param("fallbacks", json.writeValueAsString(r.fallbacks()))
                .update();
    }

    private RouteRecord route(ResultSet rs, int row) throws SQLException {
        String choice = rs.getString("provider_choice");
        String traffic = rs.getString("traffic_split");
        return new RouteRecord(
                rs.getObject("id", java.util.UUID.class),
                Task.valueOf(rs.getString("task")),
                "DEFAULT".equals(choice) ? null : Provider.valueOf(choice),
                ModelTier.valueOf(rs.getString("model_tier")),
                rs.getString("model_id"),
                json.readValue(rs.getString("params"), MAP),
                rs.getString("prompt_version"),
                json.readValue(rs.getString("fallbacks"), PROVIDERS),
                traffic == null ? null : json.readValue(traffic, MAP));
    }
}
