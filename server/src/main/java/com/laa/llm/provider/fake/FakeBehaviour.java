package com.laa.llm.provider.fake;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.laa.llm.Provider;
import com.laa.llm.Task;

/**
 * Mutable switches for FAKE and FAKE_B (tests and the /dev endpoint): failure modes, and canned structured
 * replies per task (e.g. a DECLINE from the topic gate) in place of the schema-generated JSON.
 */
@Component
public class FakeBehaviour {

    private final Map<Provider, FailureMode> modes = new EnumMap<>(Provider.class);
    private final Map<Task, String> canned = new EnumMap<>(Task.class);
    private final Map<Task, String> cannedText = new EnumMap<>(Task.class);
    private volatile Duration hang = Duration.ofSeconds(30);

    /** Structured calls for {@code task} return {@code json} as-is (it's still validated by the gateway). */
    public synchronized void setCanned(Task task, String json) {
        canned.put(task, json);
    }

    /** Text and streamed calls for {@code task} return {@code text} instead of the default script. */
    public synchronized void setCannedText(Task task, String text) {
        cannedText.put(task, text);
    }

    synchronized String canned(Task task) {
        return canned.get(task);
    }

    synchronized String cannedText(Task task) {
        return cannedText.get(task);
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
        hang = Duration.ofSeconds(30);
    }
}
