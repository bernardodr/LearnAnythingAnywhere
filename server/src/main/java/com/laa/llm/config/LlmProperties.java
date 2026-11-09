package com.laa.llm.config;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import com.laa.llm.Capabilities;
import com.laa.llm.ModelInfo;
import com.laa.llm.ModelTier;
import com.laa.llm.Provider;

/**
 * {@code laa.llm.*}. Model IDs, tiers, capabilities and prices live here (config, not code) and are
 * synced into the {@code providers} table on every start. {@code defaultProvider} only seeds an empty
 * database; after that the owner's choice in the DB wins.
 */
@ConfigurationProperties("laa.llm")
public record LlmProperties(
        @DefaultValue("FAKE") Provider defaultProvider,
        @DefaultValue("30s") Duration routeCacheTtl,
        @DefaultValue("120s") Duration callTimeout,
        @DefaultValue("30s") Duration firstTokenTimeout,
        Map<Provider, ProviderSettings> providers) {

    public LlmProperties {
        providers = providers == null ? Map.of() : new EnumMap<>(providers);
    }

    public record ProviderSettings(
            String apiKey,
            String baseUrl,
            @DefaultValue("1") int maxRetries,
            @DefaultValue("true") boolean enabled,
            List<ModelSettings> models) {

        public ProviderSettings {
            models = models == null ? List.of() : List.copyOf(models);
        }

        /** Same as the record's default toString, minus the key. */
        @Override
        public String toString() {
            return "ProviderSettings[apiKey=" + (hasText(apiKey) ? "[set]" : "[unset]") + ", baseUrl=" + baseUrl
                    + ", maxRetries=" + maxRetries + ", enabled=" + enabled + ", models=" + models + "]";
        }
    }

    public record ModelSettings(
            String id,
            String label,
            ModelTier tier,
            boolean structuredOutput,
            boolean toolCalling,
            boolean streaming,
            int maxContext,
            BigDecimal inputPerMtok,
            BigDecimal outputPerMtok) {

        public ModelInfo toModelInfo() {
            return new ModelInfo(id, label == null ? id : label, tier,
                    new Capabilities(structuredOutput, toolCalling, streaming, maxContext), inputPerMtok, outputPerMtok);
        }
    }

    public ProviderSettings settings(Provider provider) {
        return providers.getOrDefault(provider, new ProviderSettings(null, null, 1, true, List.of()));
    }

    /** A provider can be called only if its key (or, for Ollama, its URL) is present. Fakes always can. */
    public boolean isConfigured(Provider provider) {
        ProviderSettings s = settings(provider);
        return switch (provider) {
            case FAKE, FAKE_B -> true;
            case OLLAMA -> hasText(s.baseUrl());
            case OPENAI, GOOGLE, ANTHROPIC -> hasText(s.apiKey());
        };
    }

    public String notConfiguredReason(Provider provider) {
        return switch (provider) {
            case OLLAMA -> "OLLAMA is not configured (OLLAMA_BASE_URL is empty)";
            case OPENAI -> "OPENAI is not configured (OPENAI_API_KEY is empty)";
            case GOOGLE -> "GOOGLE is not configured (GEMINI_API_KEY is empty)";
            case ANTHROPIC -> "ANTHROPIC is not configured (ANTHROPIC_API_KEY is empty)";
            case FAKE, FAKE_B -> provider + " is always configured";
        };
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
