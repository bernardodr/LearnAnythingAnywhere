package com.laa.chat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.laa.llm.ChatMessage;

/** Mission-interview and (from M4) tutor chat, per textbook. */
@Repository
public class ChatMessageRepository {

    public enum Kind { MISSION, TUTOR }

    public record StoredMessage(UUID id, ChatMessage.Role role, String content, Instant createdAt) {
        public ChatMessage toChat() {
            return new ChatMessage(role, content);
        }
    }

    private final JdbcClient jdbc;

    ChatMessageRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<StoredMessage> thread(UUID textbookId, Kind kind) {
        return jdbc.sql("SELECT id, role, content, created_at FROM chat_messages "
                        + "WHERE textbook_id = :t AND kind = :k AND lesson_id IS NULL ORDER BY created_at, id")
                .param("t", textbookId).param("k", kind.name())
                .query((rs, i) -> new StoredMessage(rs.getObject("id", UUID.class),
                        ChatMessage.Role.valueOf(rs.getString("role")), rs.getString("content"),
                        rs.getTimestamp("created_at").toInstant()))
                .list();
    }

    /** The tutor chat inside one lesson. */
    public List<StoredMessage> lessonThread(UUID lessonId) {
        return jdbc.sql("SELECT id, role, content, created_at FROM chat_messages "
                        + "WHERE lesson_id = :l AND kind = 'TUTOR' ORDER BY created_at, id")
                .param("l", lessonId)
                .query((rs, i) -> new StoredMessage(rs.getObject("id", UUID.class),
                        ChatMessage.Role.valueOf(rs.getString("role")), rs.getString("content"),
                        rs.getTimestamp("created_at").toInstant()))
                .list();
    }

    /** Stores a tutor-chat question and its reply together; returns the reply's id. */
    @Transactional
    public UUID appendLessonTurn(UUID textbookId, UUID lessonId, String question, String reply) {
        insert(textbookId, lessonId, Kind.TUTOR, ChatMessage.Role.USER, question);
        return insert(textbookId, lessonId, Kind.TUTOR, ChatMessage.Role.ASSISTANT, reply);
    }

    /**
     * Stores a learner turn and the reply together, so a failed reply leaves no orphan question or answer.
     * {@code userContent} null stores only the reply (the interview's opening question). Returns the reply's id.
     */
    @Transactional
    public UUID appendTurn(UUID textbookId, Kind kind, String userContent, String assistantContent) {
        if (userContent != null) insert(textbookId, null, kind, ChatMessage.Role.USER, userContent);
        return insert(textbookId, null, kind, ChatMessage.Role.ASSISTANT, assistantContent);
    }

    private UUID insert(UUID textbookId, UUID lessonId, Kind kind, ChatMessage.Role role, String content) {
        return jdbc.sql("INSERT INTO chat_messages (textbook_id, lesson_id, kind, role, content) VALUES (:t, :l, :k, :r, :c) RETURNING id")
                .param("t", textbookId).param("l", lessonId).param("k", kind.name()).param("r", role.name()).param("c", content)
                .query(UUID.class).single();
    }
}
