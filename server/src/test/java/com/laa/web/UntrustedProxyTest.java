package com.laa.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.TestPropertySource;

import com.laa.IntegrationTest;

/**
 * {@code X-Forwarded-For} on a connection that doesn't come from an internal proxy is ignored, so a client can't
 * forge it to escape a limit. Here loopback isn't on the internal list, so the test client counts as anyone else.
 */
@TestPropertySource(properties = "server.tomcat.remoteip.internal-proxies=192.0.2.1/32")
class UntrustedProxyTest extends IntegrationTest {

    @Test
    void aForwardedAddressFromAnyoneElseIsIgnored() {
        for (int i = 0; i < 5; i++) assertThat(signUpFrom("203.0.113." + i).status()).isEqualTo(201);
        assertThat(signUpFrom("203.0.113.99").status()).as("every sign-up counts against the connection's address")
                .isEqualTo(429);
    }

    private Resp signUpFrom(String forwardedFor) {
        String email = "untrusted-" + UUID.randomUUID().toString().substring(0, 8) + "@test.dev";
        return call(HttpMethod.POST, "/auth/sign-up", null, Map.of("email", email, "password", PASSWORD),
                Map.of("X-Forwarded-For", forwardedFor));
    }
}
