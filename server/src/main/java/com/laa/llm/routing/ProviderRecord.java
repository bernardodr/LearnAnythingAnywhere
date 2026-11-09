package com.laa.llm.routing;

import java.util.List;
import java.util.Optional;

import com.laa.llm.ModelInfo;
import com.laa.llm.ModelTier;
import com.laa.llm.Provider;

/** A {@code providers} row. */
public record ProviderRecord(Provider id, boolean enabled, boolean isDefault, List<ModelInfo> models) {

    public ProviderRecord {
        models = List.copyOf(models);
    }

    /** The tier's default model: the first one listed for that tier. */
    public Optional<ModelInfo> modelForTier(ModelTier tier) {
        return models.stream().filter(m -> m.tier() == tier).findFirst();
    }

    public Optional<ModelInfo> model(String modelId) {
        return models.stream().filter(m -> m.id().equals(modelId)).findFirst();
    }

    public ProviderRecord withEnabled(boolean value) {
        return new ProviderRecord(id, value, isDefault, models);
    }

    public ProviderRecord withDefault(boolean value) {
        return new ProviderRecord(id, enabled, value, models);
    }
}
