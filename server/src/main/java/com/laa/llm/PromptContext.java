package com.laa.llm;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.laa.llm.runs.Origin;

/**
 * Everything a call needs besides the route: template variables for the task prompt,
 * prior turns, the new user message, and who/what the call is for (for metering).
 * {@code promptVariant} picks one of the task's {@link Task#promptVariants() prompt variants}
 * ({@code prompts/<task>.<variant>.<version>.md}); null means the task's main prompt.
 */
public record PromptContext(
        Map<String, String> variables,
        List<ChatMessage> history,
        String userMessage,
        UUID userId,
        UUID textbookId,
        Origin origin,
        String promptVariant) {

    public PromptContext {
        variables = variables == null ? Map.of() : Map.copyOf(variables);
        history = history == null ? List.of() : List.copyOf(history);
        origin = origin == null ? Origin.APP : origin;
    }

    public PromptContext(Map<String, String> variables, List<ChatMessage> history, String userMessage,
                         UUID userId, UUID textbookId, Origin origin) {
        this(variables, history, userMessage, userId, textbookId, origin, null);
    }

    public static PromptContext of(String userMessage) {
        return new PromptContext(Map.of(), List.of(), userMessage, null, null, Origin.APP, null);
    }

    public PromptContext forUser(UUID userId) {
        return new PromptContext(variables, history, userMessage, userId, textbookId, origin, promptVariant);
    }

    public PromptContext withOrigin(Origin origin) {
        return new PromptContext(variables, history, userMessage, userId, textbookId, origin, promptVariant);
    }

    public PromptContext withVariant(String variant) {
        return new PromptContext(variables, history, userMessage, userId, textbookId, origin, variant);
    }
}
