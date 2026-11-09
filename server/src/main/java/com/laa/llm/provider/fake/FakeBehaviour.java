package com.laa.llm.provider.fake;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.laa.llm.Provider;

/** Mutable failure switches for FAKE and FAKE_B (tests and the /dev endpoint). */
@Component
public class FakeBehaviour {

    private final Map<Provider, FailureMode> modes = new EnumMap<>(Provider.class);
    private volatile Duration hang = Duration.ofSeconds(30);

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
        hang = Duration.ofSeconds(30);
    }
}
