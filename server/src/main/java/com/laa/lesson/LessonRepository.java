package com.laa.lesson;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.laa.lesson.LessonDraft.ReferenceSheet;
import com.laa.lesson.LessonRules.Marking;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class LessonRepository {

    /**
     * A stored lesson. {@code marking} and {@code plan} are server-only; see {@link LessonController} for the client view.
     * {@code sourceIds} are the book's sources (RESOURCES.md ids) found for this lesson when it was written (issue #82).
     */
    public record Lesson(UUID id, UUID textbookId, int seq, String title, String subtitle, String win, String missionLink,
                         List<Map<String, Object>> blocks, ReferenceSheet referenceSheet, Map<String, Marking> marking,
                         LessonPlan plan, UUID modelRunId, List<String> sourceIds, Instant readAt, Instant createdAt) {}

    private static final TypeReference<List<Map<String, Object>>> BLOCKS = new TypeReference<>() {};
    private static final TypeReference<Map<String, Marking>> MARKING = new TypeReference<>() {};
    private static final String COLUMNS = "id, textbook_id, seq, title, subtitle, win, mission_link, blocks, reference_sheet, "
            + "rubrics, plan, model_run_id, source_ids, read_at, created_at";

    private final JdbcClient jdbc;
    private final JsonMapper json;

    LessonRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * Saves a lesson that has passed its checks: only {@link LessonChecks} can make a {@link LessonChecks.CheckedLesson}.
     * {@code sourceIds}: the sources found for it ({@link SourceFinder}), possibly none.
     */
    public Lesson insert(UUID textbookId, int seq, LessonChecks.CheckedLesson lesson, String missionLink, LessonPlan plan,
                         UUID modelRunId, List<String> sourceIds) {
        LessonDraft draft = lesson.draft();
        LessonRules.Result result = lesson.result();
        return jdbc.sql("INSERT INTO lessons (textbook_id, seq, title, subtitle, win, mission_link, blocks, reference_sheet, "
                        + "rubrics, plan, status, model_run_id, source_ids) VALUES (:t, :seq, :title, :subtitle, :win, :link, "
                        + ":blocks::jsonb, :sheet::jsonb, :rubrics::jsonb, :plan::jsonb, 'READY', :run, :sources) RETURNING " + COLUMNS)
                .param("t", textbookId).param("seq", seq)
                .param("title", draft.title().strip()).param("subtitle", draft.subtitle().strip())
                .param("win", draft.win().strip()).param("link", missionLink)
                .param("blocks", json.writeValueAsString(result.blocks()))
                .param("sheet", json.writeValueAsString(draft.referenceSheet()))
                .param("rubrics", json.writeValueAsString(result.marking()))
                .param("plan", json.writeValueAsString(plan))
                .param("run", modelRunId)
                .param("sources", sourceIds.toArray(String[]::new))
                .query(this::map).single();
    }

    public List<Lesson> list(UUID textbookId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM lessons WHERE textbook_id = :t ORDER BY seq")
                .param("t", textbookId).query(this::map).list();
    }

    public Optional<Lesson> find(UUID textbookId, int seq) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM lessons WHERE textbook_id = :t AND seq = :seq")
                .param("t", textbookId).param("seq", seq).query(this::map).optional();
    }

    /** Only a lesson in one of this user's textbooks. */
    public Optional<Lesson> findForUser(UUID lessonId, UUID userId) {
        return jdbc.sql("SELECT " + COLUMNS.replaceAll("(\\w+)", "l.$1") + " FROM lessons l JOIN textbooks t ON t.id = l.textbook_id "
                        + "WHERE l.id = :id AND t.user_id = :u")
                .param("id", lessonId).param("u", userId).query(this::map).optional();
    }

    /** Marks the lesson read, once; returns the read time. */
    public Instant markRead(UUID lessonId) {
        return jdbc.sql("UPDATE lessons SET read_at = coalesce(read_at, now()) WHERE id = :id RETURNING read_at")
                .param("id", lessonId).query(java.sql.Timestamp.class).single().toInstant();
    }

    private Lesson map(ResultSet rs, int row) throws SQLException {
        String sheet = rs.getString("reference_sheet");
        var readAt = rs.getTimestamp("read_at");
        return new Lesson(
                rs.getObject("id", UUID.class),
                rs.getObject("textbook_id", UUID.class),
                rs.getInt("seq"),
                rs.getString("title"),
                rs.getString("subtitle"),
                rs.getString("win"),
                rs.getString("mission_link"),
                json.readValue(rs.getString("blocks"), BLOCKS),
                sheet == null ? null : json.readValue(sheet, ReferenceSheet.class),
                json.readValue(rs.getString("rubrics"), MARKING),
                json.readValue(rs.getString("plan"), LessonPlan.class),
                rs.getObject("model_run_id", UUID.class),
                List.of((String[]) rs.getArray("source_ids").getArray()),
                readAt == null ? null : readAt.toInstant(),
                rs.getTimestamp("created_at").toInstant());
    }
}
