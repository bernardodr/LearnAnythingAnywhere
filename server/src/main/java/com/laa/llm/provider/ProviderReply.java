package com.laa.llm.provider;

/**
 * A whole reply, or one streamed chunk (token counts usually arrive only on the last chunk). {@code searches} is how
 * many web searches the provider ran for it (billed per search, on top of tokens); 0 for calls that don't search.
 */
public record ProviderReply(String text, Integer tokensIn, Integer tokensOut, int searches) {

    public ProviderReply(String text, Integer tokensIn, Integer tokensOut) {
        this(text, tokensIn, tokensOut, 0);
    }
}
