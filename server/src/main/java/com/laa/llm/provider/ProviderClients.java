package com.laa.llm.provider;

import java.time.Duration;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Component;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.laa.llm.Provider;
import com.laa.llm.config.LlmProperties;
import com.laa.llm.config.LlmProperties.ModelSettings;
import com.laa.llm.config.LlmProperties.ProviderSettings;
import com.laa.llm.provider.fake.FakeBehaviour;
import com.laa.llm.provider.fake.FakeProviderClient;
import com.laa.security.SecretRedactor;

/**
 * Builds one client per configured provider at startup: one Spring AI {@link ChatClient} for each real
 * provider whose key (or URL) is present, plus the always-available fakes. Keys go into the SDK clients
 * and nowhere else.
 */
@Component
public class ProviderClients {

    private static final Logger log = LoggerFactory.getLogger(ProviderClients.class);

    private final Map<Provider, ProviderClient> clients = new EnumMap<>(Provider.class);
    private final Map<Provider, String> unavailable = new EnumMap<>(Provider.class);

    public ProviderClients(LlmProperties props, FakeBehaviour fakeBehaviour) {
        for (Provider p : Provider.values()) {
            SecretRedactor.register(props.settings(p).apiKey());
        }
        for (Provider p : Provider.values()) {
            if (!props.isConfigured(p)) {
                unavailable.put(p, props.notConfiguredReason(p));
                continue;
            }
            try {
                clients.put(p, p.isFake() ? new FakeProviderClient(p, fakeBehaviour) : build(p, props));
                log.info("LLM provider {} configured", p);
            } catch (RuntimeException e) {
                String reason = p + " failed to initialise: " + SecretRedactor.redact(e.getMessage());
                unavailable.put(p, reason);
                log.warn(reason);
            }
        }
    }

    public Optional<ProviderClient> client(Provider provider) {
        return Optional.ofNullable(clients.get(provider));
    }

    public boolean isAvailable(Provider provider) {
        return clients.containsKey(provider);
    }

    /** Why a provider has no client (not configured, or failed to build). */
    public String unavailableReason(Provider provider) {
        return unavailable.getOrDefault(provider, provider + " is available");
    }

    public Map<Provider, String> unavailableReasons() {
        return Collections.unmodifiableMap(unavailable);
    }

    private static ProviderClient build(Provider p, LlmProperties props) {
        ProviderSettings s = props.settings(p);
        Duration timeout = props.callTimeout();
        String defaultModel = s.models().stream().map(ModelSettings::id).findFirst().orElse(null);
        return switch (p) {
            case OPENAI -> {
                OpenAiChatOptions.Builder defaults = OpenAiChatOptions.builder()
                        .apiKey(s.apiKey())
                        .timeout(timeout)
                        .maxRetries(s.maxRetries())
                        .model(defaultModel)
                        .streamUsage(true);
                if (hasText(s.baseUrl())) defaults.baseUrl(s.baseUrl());
                var model = OpenAiChatModel.builder().options(defaults.build()).build();
                yield new SpringAiProviderClient(p, ChatClient.create(model),
                        () -> OpenAiChatOptions.builder().streamUsage(true),
                        // Current OpenAI models take max_completion_tokens, not max_tokens.
                        (b, n) -> ((OpenAiChatOptions.Builder) b).maxCompletionTokens(n));
            }
            case ANTHROPIC -> {
                AnthropicChatOptions.Builder defaults = AnthropicChatOptions.builder()
                        .apiKey(s.apiKey())
                        .timeout(timeout)
                        .maxRetries(s.maxRetries())
                        .model(defaultModel);
                if (hasText(s.baseUrl())) defaults.baseUrl(s.baseUrl());
                var model = AnthropicChatModel.builder().options(defaults.build()).build();
                yield new SpringAiProviderClient(p, ChatClient.create(model),
                        AnthropicChatOptions::builder, ChatOptions.Builder::maxTokens);
            }
            case GOOGLE -> {
                // The SDK retries on its own by default; cap it like the other providers so fallbacks stay fast.
                HttpOptions.Builder http = HttpOptions.builder()
                        .timeout((int) timeout.toMillis())
                        .retryOptions(HttpRetryOptions.builder().attempts(Math.max(1, s.maxRetries() + 1)).build());
                if (hasText(s.baseUrl())) http.baseUrl(s.baseUrl());
                Client genAi = Client.builder().apiKey(s.apiKey()).httpOptions(http.build()).build();
                var model = GoogleGenAiChatModel.builder()
                        .genAiClient(genAi)
                        .options(GoogleGenAiChatOptions.builder().model(defaultModel).build())
                        .retryTemplate(retries(s.maxRetries()))
                        .build();
                yield new SpringAiProviderClient(p, ChatClient.create(model),
                        GoogleGenAiChatOptions::builder, ChatOptions.Builder::maxTokens);
            }
            case OLLAMA -> {
                var model = OllamaChatModel.builder()
                        .ollamaApi(OllamaApi.builder().baseUrl(s.baseUrl()).build())
                        .options(OllamaChatOptions.builder().model(defaultModel).build())
                        .retryTemplate(retries(s.maxRetries()))
                        .modelManagementOptions(ModelManagementOptions.defaults())
                        .build();
                yield new SpringAiProviderClient(p, ChatClient.create(model),
                        OllamaChatOptions::builder, ChatOptions.Builder::maxTokens);
            }
            case FAKE, FAKE_B -> throw new IllegalArgumentException("fakes are built separately");
        };
    }

    private static RetryTemplate retries(int maxRetries) {
        return new RetryTemplate(RetryPolicy.withMaxRetries(Math.max(0, maxRetries)));
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    /** For diagnostics: which providers have clients. */
    public List<Provider> available() {
        return List.copyOf(clients.keySet());
    }
}
