package com.laa.llm.runs;

import java.math.BigDecimal;
import java.util.UUID;

import com.laa.llm.Provider;
import com.laa.llm.RunStatus;
import com.laa.llm.Task;
import com.laa.llm.provider.ErrorKind;

/** One provider attempt, as written to {@code model_runs}. */
public record RunRecord(
        Task task,
        Provider provider,
        String model,
        String promptVersion,
        UUID routeId,
        Origin origin,
        int attempt,
        Integer tokensIn,
        Integer tokensOut,
        BigDecimal costEstimate,
        long latencyMs,
        RunStatus status,
        ErrorKind errorKind,
        String errorMessage,
        UUID userId,
        UUID textbookId) {}
