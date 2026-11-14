package com.laa.llm;

import reactor.core.publisher.Flux;

/**
 * The only way the app talks to a model. The owner's routing decides the provider per task;
 * callers never choose one.
 */
public interface LlmGateway {

    /** Structured output validated against {@code type}'s JSON schema (and Jakarta constraints). */
    default <T> T structured(Task task, PromptContext ctx, Class<T> type, GenerationProfile profile) {
        return structuredResult(task, ctx, type, profile).value();
    }

    /** As {@link #structured}, plus the run id, provider and model that produced it. */
    <T> LlmResult<T> structuredResult(Task task, PromptContext ctx, Class<T> type, GenerationProfile profile);

    /**
     * Streamed text. Falls back to the next provider only if the current one fails before its first
     * token; after that the error is surfaced (half an answer can't be swapped for another).
     */
    Flux<String> stream(Task task, PromptContext ctx, GenerationProfile profile);

    /**
     * As {@link #stream}, also reporting the id of the run that produced the reply once it completes, so a reply
     * the safety layer holds back afterwards can be logged as BLOCKED.
     */
    default Flux<String> stream(Task task, PromptContext ctx, GenerationProfile profile, java.util.function.Consumer<java.util.UUID> onRun) {
        return stream(task, ctx, profile);
    }
}
