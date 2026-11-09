package com.laa.llm.provider;

import com.laa.llm.Provider;

import reactor.core.publisher.Flux;

/** Adapter seam: one per configured provider. Provider-specific options are handled inside. */
public interface ProviderClient {

    Provider provider();

    /** Blocking call. Throws on any provider failure; the gateway classifies and falls back. */
    ProviderReply call(ProviderRequest request);

    Flux<ProviderReply> stream(ProviderRequest request);
}
