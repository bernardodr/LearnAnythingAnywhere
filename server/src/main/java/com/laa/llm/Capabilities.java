package com.laa.llm;

import java.util.ArrayList;
import java.util.List;

/** What a model can do. Declared in application.yml per model. */
public record Capabilities(boolean structuredOutput, boolean toolCalling, boolean streaming, int maxContext) {

    /** Human-readable reasons this model can't serve {@code task}; empty when it can. */
    public List<String> shortfallsFor(Task task) {
        Task.Needs needs = task.needs();
        List<String> missing = new ArrayList<>();
        if (needs.structuredOutput() && !structuredOutput) missing.add("structured output");
        if (needs.toolCalling() && !toolCalling) missing.add("tool calling");
        if (needs.streaming() && !streaming) missing.add("streaming");
        if (maxContext < needs.minContext()) {
            missing.add("a context window of at least " + needs.minContext() + " tokens (has " + maxContext + ")");
        }
        return missing;
    }
}
