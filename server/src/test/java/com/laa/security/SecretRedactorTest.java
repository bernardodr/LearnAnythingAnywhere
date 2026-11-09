package com.laa.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.laa.llm.provider.ErrorClassifier;
import com.laa.llm.provider.ErrorKind;
import com.laa.llm.provider.ProviderCallException;

class SecretRedactorTest {

    @Test
    void masksRegisteredSecretsAndCommonKeyShapes() {
        SecretRedactor.register("XYZ 990011");
        assertThat(SecretRedactor.redact("Authorization failed for key XYZ 990011")).doesNotContain("XYZ 990011");
        assertThat(SecretRedactor.redact("x sk-proj-abcdefghijklmnop y")).isEqualTo("x [REDACTED] y");
        assertThat(SecretRedactor.redact("AIzaSyA1234567890abcdefghijk")).isEqualTo("[REDACTED]");
        assertThat(SecretRedactor.redact("Bearer eyJhbGciOiJIUzI1NiJ9.abc.def")).isEqualTo("Bearer [REDACTED]");
        assertThat(SecretRedactor.redact("{\"api_key\": \"whatever123\"}")).doesNotContain("whatever123");
        assertThat(SecretRedactor.redact("nothing secret here")).isEqualTo("nothing secret here");
    }

    @Test
    void classifiesProviderFailures() {
        assertThat(ErrorClassifier.classify(new RuntimeException(new java.net.SocketTimeoutException("Read timed out"))))
                .isEqualTo(ErrorKind.TIMEOUT);
        assertThat(ErrorClassifier.classify(new ProviderCallException(ErrorKind.RATE_LIMIT, "429", 429)))
                .isEqualTo(ErrorKind.RATE_LIMIT);
        assertThat(ErrorClassifier.classify(new IllegalStateException("boom"))).isEqualTo(ErrorKind.PROVIDER_ERROR);
    }
}
