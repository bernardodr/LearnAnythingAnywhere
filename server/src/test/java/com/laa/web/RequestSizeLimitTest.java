package com.laa.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.laa.IntegrationTest;

/** Request bodies are capped (256 KB); every LAA request is short text. */
class RequestSizeLimitTest extends IntegrationTest {

    @Test
    void aBodyThatSaysItsOverTheCapIsRefusedBeforeAnythingReadsIt() {
        // A String body goes out with a Content-Length.
        Resp big = post("/auth/sign-in", null, "{\"email\":\"big@test.dev\",\"password\":\"" + "x".repeat(300 * 1024) + "\"}");
        assertThat(big.status()).isEqualTo(413);
        assertThat(big.body().get("detail").asString()).isEqualTo("That request is too large.");
    }

    @Test
    void aBodySentWithoutALengthStopsAtTheCap() {
        // A Map body is streamed out without a length.
        Resp big = post("/auth/sign-in", null, Map.of("email", "big@test.dev", "password", "x".repeat(300 * 1024)));
        assertThat(big.status()).isEqualTo(413);
        assertThat(big.body().get("detail").asString()).isEqualTo("That request is too large.");
    }

    @Test
    void anOrdinaryBodyGoesThrough() {
        assertThat(post("/auth/sign-in", null, Map.of("email", "small@test.dev", "password", "x".repeat(10 * 1024))).status())
                .isEqualTo(401);
    }
}
