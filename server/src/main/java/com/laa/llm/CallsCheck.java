package com.laa.llm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

import com.laa.llm.prompt.PromptLibrary;
import com.laa.llm.structured.StructuredOutputs;

/**
 * Refuses to start the app if a registered call ({@link Calls}) doesn't fit, so a mismatch shows up the moment it's
 * made, not when a learner first reaches it:
 * <ul>
 *   <li>every call ships its prompt at {@value #VERSION};</li>
 *   <li>a structured call's output type has a JSON schema, and its task is marked as needing structured output;</li>
 *   <li>a text call's task is marked as needing streaming (text is only ever streamed);</li>
 *   <li>no two calls share an id.</li>
 * </ul>
 */
@Component
public class CallsCheck implements SmartInitializingSingleton {

    /** Every call ships this version; routes can move to later ones that exist ({@link PromptLibrary#exists}). */
    static final String VERSION = "v1";

    private final PromptLibrary prompts;
    private final StructuredOutputs outputs;

    CallsCheck(PromptLibrary prompts, StructuredOutputs outputs) {
        this.prompts = prompts;
        this.outputs = outputs;
    }

    @Override
    public void afterSingletonsInstantiated() {
        List<String> problems = problems(Calls.all());
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Registered calls don't fit:\n- " + String.join("\n- ", problems));
        }
    }

    /** What's wrong with these calls, one line each (empty when they all fit). */
    public List<String> problems(Collection<? extends Call> calls) {
        List<String> problems = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Call call : calls) {
            String id = call.id();
            if (!ids.add(id)) problems.add(id + ": registered twice");
            if (!prompts.has(call, VERSION)) problems.add(id + ": no prompt file prompts/" + PromptLibrary.fileName(call, VERSION));
            Task.Needs needs = call.task().needs();
            switch (call) {
                case TextCall text -> {
                    if (!needs.streaming()) {
                        problems.add(id + ": text is streamed, but " + text.task() + " isn't marked as needing streaming");
                    }
                }
                case StructuredCall<?> structured -> {
                    if (!needs.structuredOutput()) {
                        problems.add(id + ": " + structured.task() + " isn't marked as needing structured output");
                    }
                    try {
                        outputs.forType(structured.output());
                    } catch (RuntimeException e) {
                        problems.add(id + ": no JSON schema for " + structured.output().getSimpleName() + " (" + e.getMessage() + ")");
                    }
                }
            }
        }
        return problems;
    }
}
