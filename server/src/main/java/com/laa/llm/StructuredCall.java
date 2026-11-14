package com.laa.llm;

/**
 * A call whose reply is an object of {@code output}, validated against its JSON schema
 * ({@link LlmGateway#structuredResult}). Its task must be routed to models with structured output.
 */
public record StructuredCall<T>(Task task, String variant, Class<T> output, String label) implements Call {}
