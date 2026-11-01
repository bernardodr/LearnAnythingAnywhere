package com.laa;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ServerApplicationTests {

    @LocalServerPort
    int port;

    @Test
    void healthReportsUpWithDatabase() {
        String body = RestClient.create("http://localhost:" + port)
                .get().uri("/health").retrieve().body(String.class);
        assertThat(body).contains("\"status\":\"UP\"").contains("\"database\":\"UP\"");
    }
}
