package com.laa.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.laa.web.ApiException;

/**
 * Limits guessing (NIST SP 800-63B-4 rate limiting): a password for one email, the parent PIN for one parent, and
 * everything from one network address.
 * <ul>
 *   <li>Each key counts failed tries within {@value #MINUTES} minutes of its first one. Once it reaches its limit, that
 *       key is paused for {@value #MINUTES} minutes: every try is refused, right or wrong, before anything is
 *       checked.</li>
 *   <li>A success clears that key (an email's count, a PIN's count), but not the network address's.</li>
 * </ul>
 * Kept in memory, so a restart clears it, and one server only (prototype). Behind a proxy, the address is the proxy's.
 */
@Component
public class Throttle {

    static final int MINUTES = 15;
    private static final Duration WINDOW = Duration.ofMinutes(MINUTES);

    /** Something tries are counted against, and how many failures it may have in a window. */
    public record Key(String id, int limit) {}

    public static Key email(String email) {
        return new Key("email:" + email.toLowerCase(), 5);
    }

    public static Key address(String address) {
        return new Key("address:" + address, 20);
    }

    public static Key pin(UUID parentId) {
        return new Key("pin:" + parentId, 5);
    }

    private record Count(Instant start, int failures, Instant pausedUntil) {}

    private final Cache<String, Count> counts = Caffeine.newBuilder()
            .expireAfterWrite(WINDOW.multipliedBy(2))
            .maximumSize(100_000)
            .build();

    /** Refuses (429) if any key is paused. Call it before checking anything. */
    public void guard(Key... keys) {
        Instant now = Instant.now();
        for (Key key : keys) {
            Count c = counts.getIfPresent(key.id());
            if (c != null && c.pausedUntil() != null && now.isBefore(c.pausedUntil())) {
                long minutes = Math.max(1, (Duration.between(now, c.pausedUntil()).toSeconds() + 59) / 60);
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many tries. Please wait "
                        + (minutes == 1 ? "a minute" : minutes + " minutes") + " and try again.");
            }
        }
    }

    public void failed(Key... keys) {
        Instant now = Instant.now();
        for (Key key : keys) {
            counts.asMap().compute(key.id(), (id, c) -> {
                boolean fresh = c == null || now.isAfter(c.start().plus(WINDOW)) && (c.pausedUntil() == null || !now.isBefore(c.pausedUntil()));
                int failures = fresh ? 1 : c.failures() + 1;
                Instant start = fresh ? now : c.start();
                return new Count(start, failures, failures >= key.limit() ? now.plus(WINDOW) : null);
            });
        }
    }

    public void succeeded(Key key) {
        counts.invalidate(key.id());
    }

    /** Forgets every count. For tests, which all come from one address. */
    public void reset() {
        counts.invalidateAll();
    }
}
