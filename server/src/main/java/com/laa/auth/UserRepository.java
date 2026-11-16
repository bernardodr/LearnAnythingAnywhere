package com.laa.auth;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    /** A user with their sign-in details: the stored password hash (null if they have none) and its version. */
    public record Account(AppUser user, String passwordHash, int passwordVersion) {}

    private static final String COLUMNS = "id, email, display_name, role, password_hash, password_version";

    private final JdbcClient jdbc;

    public UserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AppUser> findByEmail(String email) {
        return accountByEmail(email).map(Account::user);
    }

    public Optional<AppUser> findById(UUID id) {
        return account(id).map(Account::user);
    }

    public Optional<Account> accountByEmail(String email) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM users WHERE email = :email")
                .param("email", email.toLowerCase())
                .query((rs, i) -> account(rs))
                .optional();
    }

    public Optional<Account> account(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM users WHERE id = :id")
                .param("id", id)
                .query((rs, i) -> account(rs))
                .optional();
    }

    /** The version a sign-in token must carry to still count, or null if there's no such user. */
    public Integer passwordVersion(UUID id) {
        return jdbc.sql("SELECT password_version FROM users WHERE id = :id").param("id", id)
                .query(Integer.class).optional().orElse(null);
    }

    public AppUser create(String email, String displayName, Role role) {
        return create(email, displayName, role, null);
    }

    /** A new user; {@code passwordHash} may be null (dev sign-in). */
    public AppUser create(String email, String displayName, Role role, String passwordHash) {
        return jdbc.sql("""
                INSERT INTO users (email, display_name, role, password_hash) VALUES (:email, :name, :role, :hash)
                RETURNING id, email, display_name, role""")
                .param("email", email.toLowerCase())
                .param("name", displayName)
                .param("role", role.name())
                .param("hash", passwordHash)
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

    /** A new password: ends every sign-in made under the old one. Returns the new version. */
    public int setPassword(UUID id, String passwordHash) {
        return jdbc.sql("UPDATE users SET password_hash = :hash, password_version = password_version + 1 WHERE id = :id "
                        + "RETURNING password_version")
                .param("id", id).param("hash", passwordHash)
                .query(Integer.class).single();
    }

    /** The same password, hashed again with today's settings. Sign-ins carry on. */
    public void rehash(UUID id, String passwordHash) {
        jdbc.sql("UPDATE users SET password_hash = :hash WHERE id = :id").param("id", id).param("hash", passwordHash).update();
    }

    private static Account account(ResultSet rs) throws SQLException {
        return new Account(map(rs), rs.getString("password_hash"), rs.getInt("password_version"));
    }

    private static AppUser map(ResultSet rs) throws SQLException {
        return new AppUser(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("display_name"),
                Role.valueOf(rs.getString("role")));
    }
}
