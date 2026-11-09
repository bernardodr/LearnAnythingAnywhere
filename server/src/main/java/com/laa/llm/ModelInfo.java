package com.laa.llm;

import java.math.BigDecimal;

/** One model a provider offers. Prices are USD per million tokens and may be null (unknown / free). */
public record ModelInfo(
        String id,
        String label,
        ModelTier tier,
        Capabilities capabilities,
        BigDecimal inputPerMTok,
        BigDecimal outputPerMTok) {}
