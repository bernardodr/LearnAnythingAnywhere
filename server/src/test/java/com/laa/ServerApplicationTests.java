package com.laa;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ServerApplicationTests extends IntegrationTest {

    @Test
    void healthReportsUpWithDatabase() {
        Resp r = get("/health", null);
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body().get("status").asString()).isEqualTo("UP");
        assertThat(r.body().get("database").asString()).isEqualTo("UP");
    }
}
