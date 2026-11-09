package com.laa.workspace;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * A textbook's /teach workspace, stored as files: {@code MISSION.md}, {@code RESOURCES.md}, {@code GLOSSARY.md},
 * {@code NOTES.md} and {@code learning-records/0001-<slug>.md} (BRIEF section 4). Every write bumps the
 * version and keeps a copy in {@code workspace_file_versions}.
 */
@Repository
public class WorkspaceFiles {

    public static final String MISSION = "MISSION.md";

    private static final Pattern ALLOWED = Pattern.compile(
            "MISSION\\.md|RESOURCES\\.md|GLOSSARY\\.md|NOTES\\.md|learning-records/\\d{4}-[a-z0-9]+(-[a-z0-9]+)*\\.md");

    public record WorkspaceFile(String path, String content, int version) {}

    private final JdbcClient jdbc;

    WorkspaceFiles(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public static boolean isValidPath(String path) {
        return path != null && ALLOWED.matcher(path).matches();
    }

    public Optional<WorkspaceFile> read(UUID textbookId, String path) {
        return jdbc.sql("SELECT path, content, version FROM workspace_files WHERE textbook_id = :t AND path = :p")
                .param("t", textbookId).param("p", path)
                .query((rs, i) -> new WorkspaceFile(rs.getString("path"), rs.getString("content"), rs.getInt("version")))
                .optional();
    }

    public List<String> list(UUID textbookId) {
        return jdbc.sql("SELECT path FROM workspace_files WHERE textbook_id = :t ORDER BY path")
                .param("t", textbookId).query(String.class).list();
    }

    /** Writes a new version of the file and returns it. */
    @Transactional
    public WorkspaceFile write(UUID textbookId, String path, String content) {
        if (!isValidPath(path)) throw new IllegalArgumentException("Not a workspace path: " + path);
        int version = jdbc.sql("""
                        INSERT INTO workspace_files (textbook_id, path, content, version) VALUES (:t, :p, :c, 1)
                        ON CONFLICT (textbook_id, path)
                        DO UPDATE SET content = EXCLUDED.content, version = workspace_files.version + 1
                        RETURNING version""")
                .param("t", textbookId).param("p", path).param("c", content)
                .query(Integer.class).single();
        jdbc.sql("INSERT INTO workspace_file_versions (textbook_id, path, version, content) VALUES (:t, :p, :v, :c)")
                .param("t", textbookId).param("p", path).param("v", version).param("c", content).update();
        return new WorkspaceFile(path, content, version);
    }
}
