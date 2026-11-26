package com.laa.llm;

import java.math.BigDecimal;

/**
 * One model a provider offers. Prices are USD per million tokens and may be null (unknown / free).
 * {@code webSearchPer1k} is what its provider charges per 1,000 web searches (null: none, or free).
 */
public record ModelInfo(
        String id,
        String label,
        ModelTier tier,
        Capabilities capabilities,
        BigDecimal inputPerMTok,
        BigDecimal outputPerMTok,
        BigDecimal webSearchPer1k) {}
