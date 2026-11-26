package com.laa.llm.provider.fake;

import java.time.Duration;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.laa.llm.Call;
import com.laa.llm.Provider;
import com.laa.llm.StructuredCall;
import com.laa.llm.TextCall;

/**
 * Mutable switches for FAKE and FAKE_B (tests and the /dev endpoint): failure modes, and replies pinned per call
 * (e.g. a DECLINE from the topic gate) in place of the scripted ones.
 */
@Component
public class FakeBehaviour {

    private final Map<Provider, FailureMode> modes = new EnumMap<>(Provider.class);
    /** Text containing this is blocked by the fake content check, so tests can trigger a block where they want one. */
    public static final String BLOCK_MARKER = "[fake:block]";
    /**
     * Text containing this makes the fake content check answer in the wrong shape, every time, so the check can't run:
     * tests use it to show that a safety check that's down stops what it was checking, at exactly that check.
     */
    public static final String CHECK_DOWN_MARKER = "[fake:check-down]";

    private final Map<Call, String> canned = new HashMap<>();
    private final Map<Call, String> cannedText = new HashMap<>();
    private final Map<Call, String> lastSystem = new HashMap<>();
    private volatile Duration hang = Duration.ofSeconds(30);

    /** Pins the reply to exactly this call ({@code json} as-is; the gateway still validates it). */
    public synchronized void setCanned(StructuredCall<?> call, String json) {
        canned.put(call, json);
    }

    /** Pins the reply to exactly this text call, in place of its script. */
    public synchronized void setCannedText(TextCall call, String text) {
        cannedText.put(call, text);
    }

    synchronized String canned(Call call) {
        return canned.get(call);
    }

    /**
     * The system prompt the practice provider was last sent for this call, or null: tests read it to check what reached
     * the model (for example, the learner's familiarity in a lesson's plan).
     */
    public synchronized String lastSystemPrompt(Call call) {
        return lastSystem.get(call);
    }

    synchronized void sent(Call call, String system) {
        lastSystem.put(call, system);
    }

    synchronized String cannedText(Call call) {
        return cannedText.get(call);
    }

    public synchronized FailureMode mode(Provider provider) {
        return modes.getOrDefault(provider, FailureMode.NONE);
    }

    public synchronized void set(Provider provider, FailureMode mode) {
        if (!provider.isFake()) throw new IllegalArgumentException(provider + " is not a fake provider");
        modes.put(provider, mode);
    }

    /** INVALID_OUTPUT_ONCE flips back to NONE after it has fired. */
    synchronized boolean consumeInvalidOnce(Provider provider) {
        if (mode(provider) == FailureMode.INVALID_OUTPUT_ONCE) {
            modes.put(provider, FailureMode.NONE);
            return true;
        }
        return false;
    }

    public Duration hang() {
        return hang;
    }

    public void setHang(Duration hang) {
        this.hang = hang;
    }

    public synchronized void reset() {
        modes.clear();
        canned.clear();
        cannedText.clear();
        lastSystem.clear();
        hang = Duration.ofSeconds(30);
    }
}
