package com.laa.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.laa.IntegrationTest;

/**
 * V14 (interactive diagrams) on a database that already has routes, as the user's and production databases will.
 * Runs the real migrations into a schema of its own: up to V13, then the routes an existing database could have,
 * then V14.
 */
class DiagramsMigrationTest extends IntegrationTest {

    private static final String SCHEMA = "v14_check";

    @Autowired
    DataSource dataSource;

    @AfterEach
    void dropSchema() {
        jdbc.sql("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE").update();
    }

    @Test
    void v14AllowsTheDiagramTaskAndMovesOnlyLessonWritingRoutesStillOnV1() {
        Flyway.configure().dataSource(dataSource).schemas(SCHEMA).locations("classpath:db/migration").target("13").load().migrate();
        JdbcClient db = JdbcClient.create(dataSource);
        route(db, "LESSON_WRITE", "v1", true);   // an ordinary database: moves to v2
        route(db, "LESSON_WRITE", "v1", false);  // an old, inactive route: moves too, so switching back to it is safe
        route(db, "TUTOR", "v1", true);          // another task: untouched
        assertThatThrownBy(() -> route(db, "DIAGRAM", "v1", true)).as("before V14 there is no DIAGRAM task")
                .hasMessageContaining("model_routes_task_check");

        Flyway.configure().dataSource(dataSource).schemas(SCHEMA).locations("classpath:db/migration").target("14").load().migrate();

        assertThat(db.sql("SELECT task || ':' || prompt_version FROM " + SCHEMA + ".model_routes ORDER BY task, prompt_version")
                .query(String.class).list()).containsExactly("LESSON_WRITE:v2", "LESSON_WRITE:v2", "TUTOR:v1");
        route(db, "DIAGRAM", "v1", true); // now allowed
    }

    @Test
    void aLessonWritingVersionTheOwnerChoseOnPurposeIsKept() {
        Flyway.configure().dataSource(dataSource).schemas(SCHEMA).locations("classpath:db/migration").target("13").load().migrate();
        JdbcClient db = JdbcClient.create(dataSource);
        route(db, "LESSON_WRITE", "v7", true); // not v1: someone picked it

        Flyway.configure().dataSource(dataSource).schemas(SCHEMA).locations("classpath:db/migration").target("14").load().migrate();

        assertThat(db.sql("SELECT prompt_version FROM " + SCHEMA + ".model_routes").query(String.class).single()).isEqualTo("v7");
    }

    private static void route(JdbcClient db, String task, String version, boolean active) {
        db.sql("INSERT INTO " + SCHEMA + ".model_routes (task, model_tier, params, prompt_version, fallbacks, active) "
                        + "VALUES (:task, 'LARGE', '{}'::jsonb, :version, '[]'::jsonb, :active)")
                .paramSource(Map.of("task", task, "version", version, "active", active)).update();
    }
}
