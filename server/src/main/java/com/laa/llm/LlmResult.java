package com.laa.llm;

import java.util.UUID;

/** A successful call plus where it ran (status is OK, or FALLBACK if a fallback provider answered). */
public record LlmResult<T>(T value, UUID runId, Provider provider, String model, RunStatus status) {}
