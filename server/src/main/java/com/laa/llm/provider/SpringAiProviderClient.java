package com.laa.llm.provider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.AnthropicWebSearchTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.StructuredOutputChatOptions;

import com.anthropic.models.messages.ServerToolUsage;

import com.laa.llm.ChatMessage;
import com.laa.llm.Provider;

import reactor.core.publisher.Flux;

/**
 * Adapter over one Spring AI {@link ChatClient}. Messages are passed as Message objects (never
 * through ChatClient's template rendering, so learner text with braces is safe). Structured calls
 * use the provider's native JSON-schema mode via {@link StructuredOutputChatOptions}.
 */
final class SpringAiProviderClient implements ProviderClient {

    /** The most web searches one research call may run (Anthropic bills each one; failed searches count too). */
    static final long MAX_SEARCHES = 3;

    /** Route params the adapters understand; anything else is gateway-level (e.g. timeoutMs). */
    static final String TEMPERATURE = "temperature";
    static final String MAX_TOKENS = "maxTokens";
    static final String TOP_P = "topP";

    private final Provider provider;
    private final ChatClient chatClient;
    private final Supplier<ChatOptions.Builder<?>> optionsFactory;
    private final BiConsumer<ChatOptions.Builder<?>, Integer> maxTokensSetter;

    SpringAiProviderClient(
            Provider provider,
            ChatClient chatClient,
            Supplier<ChatOptions.Builder<?>> optionsFactory,
            BiConsumer<ChatOptions.Builder<?>, Integer> maxTokensSetter) {
        this.provider = provider;
        this.chatClient = chatClient;
        this.optionsFactory = optionsFactory;
        this.maxTokensSetter = maxTokensSetter;
    }

    @Override
    public Provider provider() {
        return provider;
    }

    @Override
    public ProviderReply call(ProviderRequest request) {
        ChatResponse response = chatClient.prompt()
                .messages(toMessages(request))
                .options(options(request))
                .call()
                .chatResponse();
        return toReply(response);
    }

    @Override
    public Flux<ProviderReply> stream(ProviderRequest request) {
        return chatClient.prompt()
                .messages(toMessages(request))
                .options(options(request))
                .stream()
                .chatResponse()
                .map(SpringAiProviderClient::toReply);
    }

    private ChatOptions.Builder<?> options(ProviderRequest request) {
        ChatOptions.Builder<?> builder = optionsFactory.get();
        builder.model(request.model());
        Map<String, Object> params = request.params();
        if (params.get(TEMPERATURE) instanceof Number n) builder.temperature(n.doubleValue());
        if (params.get(TOP_P) instanceof Number n) builder.topP(n.doubleValue());
        if (params.get(MAX_TOKENS) instanceof Number n) maxTokensSetter.accept(builder, n.intValue());
        if (request.outputSchema() != null) {
            if (builder instanceof StructuredOutputChatOptions.Builder<?> structured) {
                structured.outputSchema(request.outputSchema());
            } else {
                throw new IllegalStateException(provider + " options do not support a native output schema");
            }
        }
        if (request.task().needs().webSearch()) {
            // A call that must search the web is refused by a provider that can't: answering from memory is exactly
            // how sources get invented (BRIEF section 6.3), so it fails and the gateway tries the next provider.
            if (!(builder instanceof AnthropicChatOptions.Builder anthropic)) {
                throw new ProviderCallException(ErrorKind.NOT_CONFIGURED, provider + " can't search the web, which "
                        + request.task() + " needs: route it to a provider that can (Anthropic)");
            }
            AnthropicWebSearchTool.Builder search = AnthropicWebSearchTool.builder().maxUses(MAX_SEARCHES);
            if (request.searchDomains() != null) search.allowedDomains(request.searchDomains());
            anthropic.webSearchTool(search.build());
        }
        return builder;
    }

    private static List<Message> toMessages(ProviderRequest request) {
        List<Message> messages = new ArrayList<>(request.messages().size() + 1);
        if (request.system() != null && !request.system().isBlank()) {
            messages.add(new SystemMessage(request.system()));
        }
        for (ChatMessage m : request.messages()) {
            messages.add(switch (m.role()) {
                case USER -> new UserMessage(m.content());
                case ASSISTANT -> new AssistantMessage(m.content());
            });
        }
        return messages;
    }

    private static ProviderReply toReply(ChatResponse response) {
        String text = null;
        if (response != null && response.getResult() != null && response.getResult().getOutput() != null) {
            text = response.getResult().getOutput().getText();
        }
        Integer in = null;
        Integer out = null;
        int searches = 0;
        if (response != null && response.getMetadata() != null) {
            Usage usage = response.getMetadata().getUsage();
            if (usage != null) {
                in = positiveOrNull(usage.getPromptTokens());
                out = positiveOrNull(usage.getCompletionTokens());
                // Anthropic counts its web searches in the SDK's own usage (usage.server_tool_use.web_search_requests).
                if (usage.getNativeUsage() instanceof com.anthropic.models.messages.Usage anthropic) {
                    searches = (int) anthropic.serverToolUse().map(ServerToolUsage::webSearchRequests).orElse(0L).longValue();
                }
            }
        }
        return new ProviderReply(text == null ? "" : text, in, out, searches);
    }

    private static Integer positiveOrNull(Integer n) {
        return n == null || n <= 0 ? null : n;
    }
}
