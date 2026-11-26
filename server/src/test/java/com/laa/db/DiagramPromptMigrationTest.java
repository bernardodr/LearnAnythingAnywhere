package com.laa.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.laa.IntegrationTest;

/**
 * V16 (diagram prompt v2, issue #78) on a database with routes, as production's has. Runs the real migrations into a
 * schema of its own: up to V15, then the routes an existing database could have, then V16.
 */
class DiagramPromptMigrationTest extends IntegrationTest {

    private static final String SCHEMA = "v16_check";

    @Autowired
    DataSource dataSource;

    @AfterEach
    void dropSchema() {
        jdbc.sql("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE").update();
    }

    @Test
    void v16MovesDiagramsStillOnV1AndNothingElse() {
        migrate("15");
        JdbcClient db = JdbcClient.create(dataSource);
        route(db, "DIAGRAM", "v1");
        route(db, "LESSON_WRITE", "v1");

        migrate("16");

        assertThat(db.sql("SELECT task || ':' || prompt_version FROM " + SCHEMA + ".model_routes ORDER BY task").query(String.class).list())
                .containsExactly("DIAGRAM:v2", "LESSON_WRITE:v1");
    }

    @Test
    void aDiagramVersionTheOwnerChoseOnPurposeIsKept() {
        migrate("15");
        JdbcClient db = JdbcClient.create(dataSource);
        route(db, "DIAGRAM", "v7");

        migrate("16");

        assertThat(db.sql("SELECT prompt_version FROM " + SCHEMA + ".model_routes").query(String.class).single()).isEqualTo("v7");
    }

    private void migrate(String target) {
        Flyway.configure().dataSource(dataSource).schemas(SCHEMA).locations("classpath:db/migration").target(target).load().migrate();
    }

    private static void route(JdbcClient db, String task, String version) {
        db.sql("INSERT INTO " + SCHEMA + ".model_routes (task, model_tier, params, prompt_version, fallbacks, active) "
                        + "VALUES (:task, 'LARGE', '{}'::jsonb, :version, '[]'::jsonb, true)")
                .paramSource(Map.of("task", task, "version", version)).update();
    }
}
