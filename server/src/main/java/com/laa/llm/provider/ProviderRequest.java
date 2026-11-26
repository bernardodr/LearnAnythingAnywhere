package com.laa.llm.provider;

import java.util.List;
import java.util.Map;

import com.laa.llm.Call;
import com.laa.llm.ChatMessage;
import com.laa.llm.Task;

/**
 * One call to one provider. {@code call} says which of the app's calls it is ({@link com.laa.llm.Calls}); real
 * adapters ignore it, the practice provider replies by it. {@code outputSchema} (JSON Schema) is set for structured
 * calls that use the provider's native JSON mode, null otherwise. For a task that searches the web
 * ({@code Task.Needs.webSearch}), {@code searchDomains} limits the search to those sites (a child-safe reader's), or is
 * null to search anywhere.
 */
public record ProviderRequest(
        Call call,
        String model,
        String system,
        List<ChatMessage> messages,
        Map<String, Object> params,
        String outputSchema,
        List<String> searchDomains) {

    public ProviderRequest {
        messages = List.copyOf(messages);
        params = params == null ? Map.of() : Map.copyOf(params);
        searchDomains = searchDomains == null || searchDomains.isEmpty() ? null : List.copyOf(searchDomains);
    }

    /** A call that doesn't search the web. */
    public ProviderRequest(Call call, String model, String system, List<ChatMessage> messages, Map<String, Object> params,
                           String outputSchema) {
        this(call, model, system, messages, params, outputSchema, null);
    }

    public Task task() {
        return call.task();
    }
}
