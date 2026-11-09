package com.laa.auth;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    private final JdbcClient jdbc;

    public UserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AppUser> findByEmail(String email) {
        return jdbc.sql("SELECT id, email, display_name, role FROM users WHERE email = :email")
                .param("email", email.toLowerCase())
                .query((rs, i) -> map(rs))
                .optional();
    }

    public Optional<AppUser> findById(UUID id) {
        return jdbc.sql("SELECT id, email, display_name, role FROM users WHERE id = :id")
                .param("id", id)
                .query((rs, i) -> map(rs))
                .optional();
    }

    public AppUser create(String email, String displayName, Role role) {
        return jdbc.sql("""
                INSERT INTO users (email, display_name, role) VALUES (:email, :name, :role)
                RETURNING id, email, display_name, role""")
                .param("email", email.toLowerCase())
                .param("name", displayName)
                .param("role", role.name())
                .query((rs, i) -> map(rs))
                .single();
    }

    public AppUser updateRole(UUID id, Role role) {
        return jdbc.sql("UPDATE users SET role = :role WHERE id = :id RETURNING id, email, display_name, role")
                .param("id", id)
                .param("role", role.name())
                .query((rs, i) -> map(rs))
                .single();
    }

    private static AppUser map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AppUser(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("display_name"),
                Role.valueOf(rs.getString("role")));
    }
}
