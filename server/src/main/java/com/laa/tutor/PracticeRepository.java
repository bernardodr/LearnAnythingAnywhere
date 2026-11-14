package com.laa.tutor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Attempts at free-response blocks, and each learner's progress on them (hint ladder, met). */
@Repository
public class PracticeRepository {

    public record Progress(int hintsRevealed, boolean answerRevealed, Instant metAt, String evidencePath) {}

    public record Attempt(UUID id, int attemptNo, String answer, String verdict, Map<String, Object> feedback, Instant createdAt) {}

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final JsonMapper json;

    PracticeRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public Progress progress(UUID lessonId, String blockId, UUID userId) {
        return jdbc.sql("SELECT hints_revealed, answer_revealed, met_at, evidence_path FROM practice_progress "
                        + "WHERE lesson_id = :l AND block_id = :b AND user_id = :u")
                .param("l", lessonId).param("b", blockId).param("u", userId)
                .query((rs, i) -> new Progress(rs.getInt("hints_revealed"), rs.getBoolean("answer_revealed"),
                        rs.getTimestamp("met_at") == null ? null : rs.getTimestamp("met_at").toInstant(),
                        rs.getString("evidence_path")))
                .optional().orElse(new Progress(0, false, null, null));
    }

    /** Reveals the next hint (up to {@code total}); returns how many are revealed now. */
    public int revealHint(UUID lessonId, String blockId, UUID userId, int total) {
        return jdbc.sql("""
                        INSERT INTO practice_progress (lesson_id, block_id, user_id, hints_revealed) VALUES (:l, :b, :u, 1)
                        ON CONFLICT (lesson_id, block_id, user_id)
                        DO UPDATE SET hints_revealed = least(practice_progress.hints_revealed + 1, :total)
                        RETURNING hints_revealed""")
                .param("l", lessonId).param("b", blockId).param("u", userId).param("total", total)
                .query(Integer.class).single();
    }

    public void revealAnswer(UUID lessonId, String blockId, UUID userId) {
        upsert(lessonId, blockId, userId, "answer_revealed = true", "answer_revealed", "true");
    }

    /** Marks the block met; returns true only the first time (so evidence is written once). */
    public boolean markMet(UUID lessonId, String blockId, UUID userId) {
        jdbc.sql("INSERT INTO practice_progress (lesson_id, block_id, user_id) VALUES (:l, :b, :u) ON CONFLICT DO NOTHING")
                .param("l", lessonId).param("b", blockId).param("u", userId).update();
        return jdbc.sql("UPDATE practice_progress SET met_at = now() WHERE lesson_id = :l AND block_id = :b AND user_id = :u AND met_at IS NULL")
                .param("l", lessonId).param("b", blockId).param("u", userId).update() == 1;
    }

    public void setEvidence(UUID lessonId, String blockId, UUID userId, String path) {
        jdbc.sql("UPDATE practice_progress SET evidence_path = :p WHERE lesson_id = :l AND block_id = :b AND user_id = :u")
                .param("p", path).param("l", lessonId).param("b", blockId).param("u", userId).update();
    }

    public List<Attempt> attempts(UUID lessonId, String blockId, UUID userId) {
        return jdbc.sql("SELECT id, attempt_no, answer, verdict, feedback, created_at FROM attempts "
                        + "WHERE lesson_id = :l AND block_id = :b AND user_id = :u ORDER BY attempt_no")
                .param("l", lessonId).param("b", blockId).param("u", userId)
                .query((rs, i) -> new Attempt(rs.getObject("id", UUID.class), rs.getInt("attempt_no"), rs.getString("answer"),
                        rs.getString("verdict"), json.readValue(rs.getString("feedback"), MAP), rs.getTimestamp("created_at").toInstant()))
                .list();
    }

    public Attempt insertAttempt(UUID lessonId, String blockId, UUID userId, String answer, String verdict, Map<String, Object> feedback) {
        return jdbc.sql("""
                        INSERT INTO attempts (lesson_id, block_id, user_id, answer, verdict, feedback, attempt_no)
                        VALUES (:l, :b, :u, :a, :v, :f::jsonb,
                                (SELECT coalesce(max(attempt_no), 0) + 1 FROM attempts WHERE lesson_id = :l AND block_id = :b AND user_id = :u))
                        RETURNING id, attempt_no, answer, verdict, feedback, created_at""")
                .param("l", lessonId).param("b", blockId).param("u", userId).param("a", answer).param("v", verdict)
                .param("f", json.writeValueAsString(feedback))
                .query((rs, i) -> new Attempt(rs.getObject("id", UUID.class), rs.getInt("attempt_no"), rs.getString("answer"),
                        rs.getString("verdict"), json.readValue(rs.getString("feedback"), MAP), rs.getTimestamp("created_at").toInstant()))
                .single();
    }

    private void upsert(UUID lessonId, String blockId, UUID userId, String set, String column, String value) {
        jdbc.sql("INSERT INTO practice_progress (lesson_id, block_id, user_id, " + column + ") VALUES (:l, :b, :u, " + value + ") "
                        + "ON CONFLICT (lesson_id, block_id, user_id) DO UPDATE SET " + set)
                .param("l", lessonId).param("b", blockId).param("u", userId).update();
    }
}
