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
 * synced into the {@code providers} table on every start.
 * <ul>
 *   <li>{@code defaultProvider} only seeds an empty database; after that the owner's choice in the DB wins. It has
 *       no default: an empty database with none set refuses to start.</li>
 *   <li>{@code practiceProviders}: the practice providers (FAKE, FAKE_B) can be called only when this is on (local
 *       development and tests).</li>
 *   <li>{@code limits}: daily spending caps, in estimated US dollars.</li>
 *   <li>{@code childSafeSearchDomains}: the only sites a web search may look at for a reader in child-safe mode
 *       (well-known educational sites; the owner can change the list). Lesson sources from anywhere else are dropped
 *       for them too.</li>
 * </ul>
 */
@ConfigurationProperties("laa.llm")
public record LlmProperties(
        Provider defaultProvider,
        @DefaultValue("false") boolean practiceProviders,
        @DefaultValue Limits limits,
        @DefaultValue("30s") Duration routeCacheTtl,
        @DefaultValue("120s") Duration callTimeout,
        @DefaultValue("30s") Duration firstTokenTimeout,
        Map<Provider, ProviderSettings> providers,
        List<String> childSafeSearchDomains) {

    public LlmProperties {
        providers = providers == null ? Map.of() : new EnumMap<>(providers);
        childSafeSearchDomains = childSafeSearchDomains == null ? List.of() : List.copyOf(childSafeSearchDomains);
    }

    /**
     * Daily caps on estimated spend, reset at midnight UTC: per learner (their own requests) and in total (every
     * request, the owner's included).
     */
    public record Limits(@DefaultValue("1.00") BigDecimal learnerDailyUsd, @DefaultValue("25.00") BigDecimal totalDailyUsd) {}

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
            boolean webSearch,
            int maxContext,
            BigDecimal inputPerMtok,
            BigDecimal outputPerMtok,
            BigDecimal webSearchPer1k) {

        public ModelInfo toModelInfo() {
            return new ModelInfo(id, label == null ? id : label, tier,
                    new Capabilities(structuredOutput, toolCalling, streaming, webSearch, maxContext), inputPerMtok, outputPerMtok,
                    webSearchPer1k);
        }
    }

    public ProviderSettings settings(Provider provider) {
        return providers.getOrDefault(provider, new ProviderSettings(null, null, 1, true, List.of()));
    }

    /** A provider can be called only if its key (or, for Ollama, its URL) is present. Fakes only in practice mode. */
    public boolean isConfigured(Provider provider) {
        ProviderSettings s = settings(provider);
        return switch (provider) {
            case FAKE, FAKE_B -> practiceProviders;
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
            case FAKE, FAKE_B -> practiceProviders ? provider + " is configured"
                    : provider + " is a practice provider, and practice mode is off on this server (LAA_PRACTICE_PROVIDERS)";
        };
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
