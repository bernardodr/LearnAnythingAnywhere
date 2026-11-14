package com.laa.llm;

/**
 * The only way the app talks to a model. Callers say which call they're making ({@link Calls}); the owner's routing
 * decides the provider per task, and callers never choose one.
 */
public interface LlmGateway {

    /** The call's structured output, validated against its type's JSON schema (and Jakarta constraints). */
    default <T> T structured(StructuredCall<T> call, PromptContext ctx, GenerationProfile profile) {
        return structuredResult(call, ctx, profile).value();
    }

    /** As {@link #structured}, plus the run id, provider and model that produced it. */
    <T> LlmResult<T> structuredResult(StructuredCall<T> call, PromptContext ctx, GenerationProfile profile);

    /**
     * Streamed text, with the id of the run that produced it. Falls back to the next provider only if the current
     * one fails before its first token; after that the error is surfaced (half an answer can't be swapped for
     * another).
     */
    LlmStream stream(TextCall call, PromptContext ctx, GenerationProfile profile);
}
