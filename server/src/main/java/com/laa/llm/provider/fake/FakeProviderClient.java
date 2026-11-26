package com.laa.llm.provider.fake;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.laa.llm.Calls;
import com.laa.llm.ChatMessage;
import com.laa.llm.Provider;
import com.laa.llm.provider.ErrorKind;
import com.laa.llm.provider.ProviderCallException;
import com.laa.llm.provider.ProviderClient;
import com.laa.llm.provider.ProviderReply;
import com.laa.llm.provider.ProviderRequest;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Deterministic offline provider. Structured calls return JSON generated from the requested schema
 * (string fields read "[FAKE] fieldName"); text calls return a canned sentence per task.
 * Token counts are estimated at 4 characters per token.
 */
public final class FakeProviderClient implements ProviderClient {

    private final Provider provider;
    private final FakeBehaviour behaviour;

    public FakeProviderClient(Provider provider, FakeBehaviour behaviour) {
        this.provider = provider;
        this.behaviour = behaviour;
    }

    @Override
    public Provider provider() {
        return provider;
    }

    @Override
    public ProviderReply call(ProviderRequest request) {
        failIfConfigured(true);
        String text;
        // Structured by the kind of call, not by the schema: a call that searches the web gets its schema as
        // instructions (no native JSON mode), so it arrives here without one.
        if (request.outputSchema() != null || request.call() instanceof com.laa.llm.StructuredCall<?>) {
            boolean invalid = behaviour.consumeInvalidOnce(provider)
                    || behaviour.mode(provider) == FailureMode.INVALID_OUTPUT;
            String canned = behaviour.canned(request.call());
            String scripted = canned != null ? canned : FakeScripts.structured(request, tag());
            text = invalid ? "{\"unexpected\": \"this does not match the schema\"}"
                    : scripted != null ? scripted
                    : FakeJson.fromSchema(request.outputSchema(), provider.name());
        } else {
            text = cannedText(request);
        }
        // A call that searches the web reports one search, as a real one would bill it.
        return new ProviderReply(text, estimateTokens(request), tokens(text), request.task().needs().webSearch() ? 1 : 0);
    }

    @Override
    public Flux<ProviderReply> stream(ProviderRequest request) {
        return Flux.defer(() -> {
            if (behaviour.mode(provider) == FailureMode.TIMEOUT) {
                return Mono.delay(behaviour.hang()).thenMany(Flux.<ProviderReply>empty());
            }
            failIfConfigured(false);
            String text = cannedText(request);
            List<ProviderReply> chunks = new ArrayList<>();
            String[] words = text.split("(?<= )");
            for (int i = 0; i < words.length; i++) {
                boolean last = i == words.length - 1;
                chunks.add(new ProviderReply(words[i], last ? estimateTokens(request) : null, last ? tokens(text) : null));
            }
            return Flux.fromIterable(chunks);
        });
    }

    private void failIfConfigured(boolean blockingCall) {
        switch (behaviour.mode(provider)) {
            case TIMEOUT -> {
                if (blockingCall) sleep(behaviour.hang());
                throw new ProviderCallException(ErrorKind.TIMEOUT, provider + " (fake) timed out");
            }
            case RATE_LIMIT -> throw new ProviderCallException(ErrorKind.RATE_LIMIT, provider + " (fake) 429 rate limited", 429);
            case SERVER_ERROR -> throw new ProviderCallException(ErrorKind.SERVER_ERROR, provider + " (fake) 503 unavailable", 503);
            case PROVIDER_ERROR -> throw new ProviderCallException(ErrorKind.PROVIDER_ERROR, provider + " (fake) 400 bad request", 400);
            default -> { }
        }
    }

    private String cannedText(ProviderRequest request) {
        String canned = behaviour.cannedText(request.call());
        if (canned != null) return canned;
        if (request.call().equals(Calls.INTERVIEW_TURN)) return FakeScripts.interviewTurn(request, tag());
        return "[" + provider + "] This is a deterministic " + request.task() + " reply from the fake provider.";
    }

    private String tag() {
        return "[" + provider + "] ";
    }

    private static Integer estimateTokens(ProviderRequest request) {
        int chars = request.system() == null ? 0 : request.system().length();
        for (ChatMessage m : request.messages()) chars += m.content() == null ? 0 : m.content().length();
        return Math.max(1, chars / 4);
    }

    private static Integer tokens(String text) {
        return Math.max(1, text.length() / 4);
    }

    private static void sleep(Duration d) {
        try {
            Thread.sleep(d);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
