package com.laa.llm;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.laa.llm.runs.Origin;

/**
 * Everything a call needs besides the route: template variables for the task prompt,
 * prior turns, the new user message, and who/what the call is for (for metering).
 */
public record PromptContext(
        Map<String, String> variables,
        List<ChatMessage> history,
        String userMessage,
        UUID userId,
        UUID textbookId,
        Origin origin) {

    public PromptContext {
        variables = variables == null ? Map.of() : Map.copyOf(variables);
        history = history == null ? List.of() : List.copyOf(history);
        origin = origin == null ? Origin.APP : origin;
    }

    public static PromptContext of(String userMessage) {
        return new PromptContext(Map.of(), List.of(), userMessage, null, null, Origin.APP);
    }

    public PromptContext forUser(UUID userId) {
        return new PromptContext(variables, history, userMessage, userId, textbookId, origin);
    }

    public PromptContext withOrigin(Origin origin) {
        return new PromptContext(variables, history, userMessage, userId, textbookId, origin);
    }
}
