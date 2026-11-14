package com.laa.llm;

import java.util.List;
import java.util.UUID;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * A streamed reply and the run that produced it. The run id comes with the result (as {@link LlmResult#runId} does
 * for structured output), so every gateway adapter has to supply one: a reply held back later can always be logged
 * as BLOCKED.
 *
 * @param text  the reply, in order. Cold: each subscription is a new model call, so subscribe once.
 * @param runId completes once {@code text} has, with the id of the run that answered (the last fallback tried)
 */
public record LlmStream(Flux<String> text, Mono<UUID> runId) {

    /** The whole reply and its run. */
    public record Whole(String text, UUID runId) {}

    /** Waits for the whole reply. Throws {@link LlmUnavailableException} if no provider could answer. */
    public Whole whole() {
        List<String> chunks = text.collectList().block();
        return new Whole(chunks == null ? "" : String.join("", chunks), runId.block());
    }
}
