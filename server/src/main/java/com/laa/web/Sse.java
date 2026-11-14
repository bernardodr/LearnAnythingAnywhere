package com.laa.web;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.laa.llm.LlmUnavailableException;
import com.laa.security.SecretRedactor;

/**
 * Streams one chat turn as server-sent events: the work runs on the given executor and sends named JSON events;
 * the emitter completes when it returns. An {@link ApiException} becomes an {@code error} event with its own message
 * (they're written to be shown, e.g. the safety check's); a provider outage one with {@code unavailableMessage};
 * anything else one with a generic message. A client that goes away stops the work (the next send throws).
 */
public final class Sse {

    private static final Logger log = LoggerFactory.getLogger(Sse.class);
    private static final long TIMEOUT_MS = Duration.ofMinutes(5).toMillis();

    /** Sends one named event; throws {@link ClientGone} if the client has disconnected. */
    public interface Sender {
        void send(String event, Object data);
    }

    public static final class ClientGone extends RuntimeException {
        ClientGone(Throwable cause) {
            super(cause);
        }
    }

    private Sse() {}

    public static SseEmitter stream(ExecutorService executor, String what, String unavailableMessage, Consumer<Sender> work) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        Sender sender = (event, data) -> send(emitter, event, data);
        executor.execute(() -> {
            try {
                work.accept(sender);
                emitter.complete();
            } catch (ApiException e) {
                log.warn("{} stopped: {}", what, SecretRedactor.redact(e.getMessage()));
                fail(emitter, e.getMessage());
            } catch (LlmUnavailableException e) {
                log.warn("{} failed: {}", what, SecretRedactor.redact(e.getMessage()));
                fail(emitter, unavailableMessage);
            } catch (RuntimeException e) {
                if (causedByClientGone(e)) {
                    emitter.complete();
                    return;
                }
                log.error("{} failed: {}", what, SecretRedactor.redact(String.valueOf(e.getMessage())), e);
                fail(emitter, "Something went wrong. Nothing was lost: send it again.");
            }
        });
        return emitter;
    }

    private static void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            throw new ClientGone(e);
        }
    }

    private static void fail(SseEmitter emitter, String message) {
        try {
            send(emitter, "error", Map.of("message", message));
        } catch (ClientGone ignored) {
            // nobody is listening
        }
        emitter.complete();
    }

    private static boolean causedByClientGone(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof ClientGone) return true;
        }
        return false;
    }
}
