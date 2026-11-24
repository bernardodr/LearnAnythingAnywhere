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
 * V15 (the facelift's covers) on a database that already has books and routes, as production's does. Runs the real
 * migrations into a schema of its own: up to V14, then books and routes an existing database could have, then V15.
 */
class CoversMigrationTest extends IntegrationTest {

    private static final String SCHEMA = "v15_check";

    @Autowired
    DataSource dataSource;

    @AfterEach
    void dropSchema() {
        jdbc.sql("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE").update();
    }

    @Test
    void v15RecoloursOldCoversByVolumeKeepsOthersAndMovesThePrefaceToV2() {
        migrate("14");
        JdbcClient db = JdbcClient.create(dataSource);
        UUID user = db.sql("INSERT INTO " + SCHEMA + ".users (email, display_name, role) VALUES ('m@test.dev', 'M', 'LEARNER') RETURNING id")
                .query(UUID.class).single();
        book(db, user, 1, "#3E4C5E");
        book(db, user, 2, "#5A6B4A");
        book(db, user, 3, "#B4552F");
        book(db, user, 4, "#23466B");
        book(db, user, 5, "#3E4C5E"); // the fifth book wraps round to the first new colour
        book(db, user, 6, "#ABCDEF"); // not an old palette colour: left alone
        route(db, "MISSION_INTERVIEW", "v1");
        route(db, "TUTOR", "v1");

        migrate("15");

        assertThat(db.sql("SELECT cover_color FROM " + SCHEMA + ".textbooks ORDER BY volume").query(String.class).list())
                .containsExactly("#2F4A3B", "#D4A548", "#46668A", "#A9533A", "#2F4A3B", "#ABCDEF");
        assertThat(db.sql("SELECT count(*) FROM " + SCHEMA + ".textbooks WHERE cover_motif IS NULL").query(Integer.class).single())
                .as("no motifs yet: the server matches them from titles").isEqualTo(6);
        assertThat(db.sql("SELECT task || ':' || prompt_version FROM " + SCHEMA + ".model_routes ORDER BY task").query(String.class).list())
                .containsExactly("MISSION_INTERVIEW:v2", "TUTOR:v1");

        db.sql("UPDATE " + SCHEMA + ".textbooks SET cover_motif = 'stars' WHERE volume = 1").update();
        assertThatThrownBy(() -> db.sql("UPDATE " + SCHEMA + ".textbooks SET cover_motif = 'Stars; drop' WHERE volume = 2").update())
                .as("only lower-case names").hasMessageContaining("cover_motif");
    }

    @Test
    void aPrefaceVersionTheOwnerChoseOnPurposeIsKept() {
        migrate("14");
        JdbcClient db = JdbcClient.create(dataSource);
        route(db, "MISSION_INTERVIEW", "v7");

        migrate("15");

        assertThat(db.sql("SELECT prompt_version FROM " + SCHEMA + ".model_routes").query(String.class).single()).isEqualTo("v7");
    }

    private void migrate(String target) {
        Flyway.configure().dataSource(dataSource).schemas(SCHEMA).locations("classpath:db/migration").target(target).load().migrate();
    }

    private static void book(JdbcClient db, UUID user, int volume, String color) {
        db.sql("INSERT INTO " + SCHEMA + ".textbooks (user_id, title, topic, status, cover_color, volume, topic_gate) "
                        + "VALUES (:u, :t, :t, 'ACTIVE', :c, :v, '{}'::jsonb)")
                .paramSource(Map.of("u", user, "t", "Book " + volume, "c", color, "v", volume)).update();
    }

    private static void route(JdbcClient db, String task, String version) {
        db.sql("INSERT INTO " + SCHEMA + ".model_routes (task, model_tier, params, prompt_version, fallbacks, active) "
                        + "VALUES (:task, 'LARGE', '{}'::jsonb, :version, '[]'::jsonb, true)")
                .paramSource(Map.of("task", task, "version", version)).update();
    }
}
