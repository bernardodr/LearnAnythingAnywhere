package com.laa.llm.runs;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** A {@code model_runs} row as the owner API returns it. */
public record ModelRun(
        UUID id,
        String task,
        String provider,
        String model,
        String promptVersion,
        UUID routeId,
        String origin,
        int attempt,
        Integer tokensIn,
        Integer tokensOut,
        BigDecimal costEstimate,
        int latencyMs,
        String status,
        String errorKind,
        String errorMessage,
        List<String> safetyFlags,
        UUID userId,
        UUID textbookId,
        OffsetDateTime createdAt) {}
