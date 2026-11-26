package com.laa.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.laa.IntegrationTest;

/**
 * V19 (familiarity, issue #83) on a database with books and routes, as production's has: the column (1 to 5, or null
 * for books from before), and the preface, planning and writing routes moved to the prompts that use it.
 */
class FamiliarityMigrationTest extends IntegrationTest {

    private static final String SCHEMA = "v19_check";

    @Autowired
    DataSource dataSource;

    @AfterEach
    void dropSchema() {
        jdbc.sql("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE").update();
    }

    @Test
    void v19AddsFamiliarityAndMovesOnlyRoutesOnThePreviousVersion() {
        migrate("18");
        JdbcClient db = JdbcClient.create(dataSource);
        UUID user = db.sql("INSERT INTO " + SCHEMA + ".users (email, display_name, role) VALUES ('f@test.dev', 'F', 'LEARNER') RETURNING id")
                .query(UUID.class).single();
        db.sql("INSERT INTO " + SCHEMA + ".textbooks (user_id, title, topic, status, cover_color, volume, topic_gate) "
                + "VALUES (:u, 'Old book', 'Old book', 'ACTIVE', '#2F4A3B', 1, '{}'::jsonb)").param("u", user).update();
        route(db, "MISSION_INTERVIEW", "v2");
        route(db, "LESSON_PLAN", "v1");
        route(db, "LESSON_WRITE", "v7");
        route(db, "TUTOR", "v1");

        migrate("19");

        assertThat(db.sql("SELECT familiarity FROM " + SCHEMA + ".textbooks").query(Integer.class).optional()).as("old books: none").isEmpty();
        assertThat(db.sql("SELECT task || ':' || prompt_version FROM " + SCHEMA + ".model_routes ORDER BY task").query(String.class).list())
                .containsExactly("LESSON_PLAN:v2", "LESSON_WRITE:v7", "MISSION_INTERVIEW:v3", "TUTOR:v1");
        assertThatThrownBy(() -> db.sql("UPDATE " + SCHEMA + ".textbooks SET familiarity = 6").update()).hasMessageContaining("familiarity");
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
