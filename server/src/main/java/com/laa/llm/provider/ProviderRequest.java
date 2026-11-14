package com.laa.llm.provider;

import java.util.List;
import java.util.Map;

import com.laa.llm.Call;
import com.laa.llm.ChatMessage;
import com.laa.llm.Task;

/**
 * One call to one provider. {@code call} says which of the app's calls it is ({@link com.laa.llm.Calls}); real
 * adapters ignore it, the practice provider replies by it. {@code outputSchema} (JSON Schema) is set for structured
 * calls, null for text.
 */
public record ProviderRequest(
        Call call,
        String model,
        String system,
        List<ChatMessage> messages,
        Map<String, Object> params,
        String outputSchema) {

    public ProviderRequest {
        messages = List.copyOf(messages);
        params = params == null ? Map.of() : Map.copyOf(params);
    }

    public Task task() {
        return call.task();
    }
}
