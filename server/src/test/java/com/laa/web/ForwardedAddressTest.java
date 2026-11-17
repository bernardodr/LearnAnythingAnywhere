package com.laa.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.laa.IntegrationTest;

/**
 * Behind the proxy, limits count each client, not the proxy. The client's address comes from {@code X-Forwarded-For}
 * when the connection comes from an internal proxy: here the test client on loopback, as Caddy on the Docker network
 * is in production. {@link UntrustedProxyTest} covers a connection from anyone else.
 */
class ForwardedAddressTest extends IntegrationTest {

    @Test
    void limitsCountTheClientTheProxyForwards() {
        for (int i = 0; i < 5; i++) assertThat(signUpFrom("203.0.113.7").status()).isEqualTo(201);
        assertThat(signUpFrom("203.0.113.7").status()).as("the sixth sign-up from one client").isEqualTo(429);
        assertThat(signUpFrom("203.0.113.8").status()).as("another client behind the same proxy").isEqualTo(201);
    }

    @Test
    void whatAClientPutsAheadOfTheProxysEntryIsIgnored() {
        // The client is the last address the proxy added; anything the client sent before it doesn't count.
        for (int i = 0; i < 5; i++) assertThat(signUpFrom("198.51.100." + i + ", 203.0.113.9").status()).isEqualTo(201);
        assertThat(signUpFrom("198.51.100.99, 203.0.113.9").status()).isEqualTo(429);
    }

    private Resp signUpFrom(String forwardedFor) {
        String email = "fwd-" + UUID.randomUUID().toString().substring(0, 8) + "@test.dev";
        return call(HttpMethod.POST, "/auth/sign-up", null, Map.of("email", email, "password", PASSWORD),
                Map.of("X-Forwarded-For", forwardedFor));
    }
}
