package com.laa.llm.provider;

/** A whole reply, or one streamed chunk (token counts usually arrive only on the last chunk). */
public record ProviderReply(String text, Integer tokensIn, Integer tokensOut) {}
