package com.laa.textbook;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.laa.mission.MissionDraft;
import com.laa.textbook.Textbook.Status;
import com.laa.web.ApiException;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class TextbookRepository {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};
    private static final String COLUMNS = "id, user_id, title, topic, status, cover_color, volume, topic_gate, "
            + "mission_draft, mission_headline, created_at";

    private final JdbcClient jdbc;
    private final JsonMapper json;

    TextbookRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * The next volume number goes to this textbook; the unique (user_id, volume) key guards a race. {@code profileId}
     * is the learner's generation profile the topic was checked against (null in tests that don't need one).
     */
    public Textbook create(UUID userId, String title, String topic, Map<String, Object> topicGate, UUID profileId) {
        int volume = jdbc.sql("SELECT coalesce(max(volume), 0) + 1 FROM textbooks WHERE user_id = :u")
                .param("u", userId).query(Integer.class).single();
        return jdbc.sql("INSERT INTO textbooks (user_id, title, topic, status, cover_color, volume, topic_gate, profile_id) "
                        + "VALUES (:u, :title, :topic, 'INTERVIEW', :cover, :volume, :gate::jsonb, :profile) RETURNING " + COLUMNS)
                .param("u", userId).param("title", title).param("topic", topic).param("profile", profileId)
                .param("cover", Textbook.coverFor(volume)).param("volume", volume)
                .param("gate", json.writeValueAsString(topicGate))
                .query(this::map).single();
    }

    /** Only the owner's textbooks: another user's id behaves as if it didn't exist. */
    public Optional<Textbook> findForUser(UUID id, UUID userId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM textbooks WHERE id = :id AND user_id = :u")
                .param("id", id).param("u", userId).query(this::map).optional();
    }

    /** As {@link #findForUser}, or 404. */
    public Textbook requireForUser(UUID id, UUID userId) {
        return findForUser(id, userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No such textbook"));
    }

    /** The shelf, in the learner's own order: books they've arranged first, then the rest (new ones too) by volume. */
    public List<Textbook> listForUser(UUID userId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM textbooks WHERE user_id = :u AND status <> 'ARCHIVED' "
                        + "ORDER BY shelf_position NULLS LAST, volume, created_at")
                .param("u", userId).query(this::map).list();
    }

    /** Puts the learner's books in this order. Ids that aren't theirs are ignored. */
    @Transactional
    public void arrange(UUID userId, List<UUID> ids) {
        for (int i = 0; i < ids.size(); i++) {
            jdbc.sql("UPDATE textbooks SET shelf_position = :p WHERE id = :id AND user_id = :u")
                    .param("p", i).param("id", ids.get(i)).param("u", userId).update();
        }
    }

    public void saveDraft(UUID id, MissionDraft draft) {
        jdbc.sql("UPDATE textbooks SET mission_draft = :d::jsonb WHERE id = :id")
                .param("d", json.writeValueAsString(draft)).param("id", id).update();
    }

    /** INTERVIEW → ACTIVE, once. Returns false if the textbook wasn't in its interview any more. */
    public boolean activate(UUID id, MissionDraft confirmed) {
        return jdbc.sql("UPDATE textbooks SET status = 'ACTIVE', mission_draft = :d::jsonb, mission_headline = :h "
                        + "WHERE id = :id AND status = 'INTERVIEW'")
                .param("d", json.writeValueAsString(confirmed)).param("h", confirmed.headline()).param("id", id)
                .update() == 1;
    }

    private Textbook map(ResultSet rs, int row) throws SQLException {
        String draft = rs.getString("mission_draft");
        return new Textbook(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("title"),
                rs.getString("topic"),
                Status.valueOf(rs.getString("status")),
                rs.getString("cover_color"),
                rs.getInt("volume"),
                json.readValue(rs.getString("topic_gate"), MAP),
                draft == null ? null : json.readValue(draft, MissionDraft.class),
                rs.getString("mission_headline"),
                rs.getTimestamp("created_at").toInstant());
    }
}
