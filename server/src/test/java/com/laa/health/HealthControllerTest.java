package com.laa.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.AbstractDataSource;

/**
 * The deploy and the uptime monitor read the status code: 200 means the server can serve, 503 means it can't.
 * The healthy answer is covered over HTTP in {@code ServerApplicationTests}; a database that's gone is easier to fake here.
 */
class HealthControllerTest {

    @Test
    void aDatabaseThatCantBeReachedIs503() {
        var health = new HealthController(JdbcClient.create(new AbstractDataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                throw new SQLException("Connection refused");
            }

            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                throw new SQLException("Connection refused");
            }
        }));

        var answer = health.health();

        assertThat(answer.getStatusCode().value()).isEqualTo(503);
        assertThat(answer.getBody()).isEqualTo(Map.of("status", "DOWN", "database", "DOWN"));
    }
}
