package com.laa.llm.routing;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.laa.llm.ModelTier;
import com.laa.llm.Provider;
import com.laa.llm.Task;

/**
 * A {@code model_routes} row. {@code providerChoice} null means DEFAULT (follow the default provider).
 * {@code modelId} null means "the tier's default model".
 */
public record RouteRecord(
        UUID id,
        Task task,
        Provider providerChoice,
        ModelTier tier,
        String modelId,
        Map<String, Object> params,
        String promptVersion,
        List<Provider> fallbacks,
        Map<String, Object> trafficSplit) {

    public RouteRecord {
        params = params == null ? Map.of() : Map.copyOf(params);
        fallbacks = fallbacks == null ? List.of() : List.copyOf(fallbacks);
    }

    public boolean followsDefault() {
        return providerChoice == null;
    }
}
