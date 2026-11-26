package com.laa.llm;

import java.util.ArrayList;
import java.util.List;

/**
 * What a model can do. Declared in application.yml per model. {@code webSearch} is a Boolean (null means no) because
 * provider rows stored before it existed don't have it, and Jackson 3 can't fill a primitive from a missing field.
 */
public record Capabilities(boolean structuredOutput, boolean toolCalling, boolean streaming, Boolean webSearch, int maxContext) {

    /** Whether the model can search the web through its provider's own search tool. */
    public boolean searchesTheWeb() {
        return Boolean.TRUE.equals(webSearch);
    }


    /** Human-readable reasons this model can't serve {@code task}; empty when it can. */
    public List<String> shortfallsFor(Task task) {
        Task.Needs needs = task.needs();
        List<String> missing = new ArrayList<>();
        if (needs.structuredOutput() && !structuredOutput) missing.add("structured output");
        if (needs.toolCalling() && !toolCalling) missing.add("tool calling");
        if (needs.streaming() && !streaming) missing.add("streaming");
        if (needs.webSearch() && !searchesTheWeb()) missing.add("web search");
        if (maxContext < needs.minContext()) {
            missing.add("a context window of at least " + needs.minContext() + " tokens (has " + maxContext + ")");
        }
        return missing;
    }
}
