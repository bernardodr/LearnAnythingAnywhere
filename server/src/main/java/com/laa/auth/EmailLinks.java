package com.laa.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-use links sent by email: a 256-bit random token, of which only a SHA-256 is stored. A new link for a purpose
 * cancels that user's older unused ones. Redeeming is atomic, so a link works once, before it expires.
 */
@Component
class EmailLinks {

    enum Purpose {
        VERIFY("/verify-email", Duration.ofHours(24)),
        RESET("/reset-password", Duration.ofMinutes(30));

        final String path;
        final Duration lifetime;

        Purpose(String path, Duration lifetime) {
            this.path = path;
            this.lifetime = lifetime;
        }
    }

    private final JdbcClient jdbc;
    private final String appUrl;
    private final SecureRandom random = new SecureRandom();

    EmailLinks(JdbcClient jdbc, @Value("${laa.app-url}") String appUrl) {
        this.jdbc = jdbc;
        this.appUrl = appUrl.endsWith("/") ? appUrl.substring(0, appUrl.length() - 1) : appUrl;
    }

    /** A new link for {@code userId}; their older unused links for the same purpose stop working. */
    @Transactional
    String create(UUID userId, Purpose purpose) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.sql("UPDATE email_tokens SET used_at = now() WHERE user_id = :u AND purpose = :p AND used_at IS NULL")
                .param("u", userId).param("p", purpose.name()).update();
        jdbc.sql("INSERT INTO email_tokens (user_id, purpose, token_hash, expires_at) VALUES (:u, :p, :h, now() + make_interval(secs => :s))")
                .param("u", userId).param("p", purpose.name()).param("h", hash(token)).param("s", purpose.lifetime.toSeconds())
                .update();
        return appUrl + purpose.path + "?token=" + token;
    }

    /** Whose link this is, if it's still good, without using it up. */
    Optional<UUID> peek(String token, Purpose purpose) {
        if (token == null || token.isBlank()) return Optional.empty();
        return jdbc.sql("SELECT user_id FROM email_tokens WHERE token_hash = :h AND purpose = :p AND used_at IS NULL AND expires_at > now()")
                .param("h", hash(token)).param("p", purpose.name()).query(UUID.class).optional();
    }

    /** Uses the link up: whose it was, if it was still good. */
    Optional<UUID> redeem(String token, Purpose purpose) {
        if (token == null || token.isBlank()) return Optional.empty();
        return jdbc.sql("""
                        UPDATE email_tokens SET used_at = now()
                        WHERE token_hash = :h AND purpose = :p AND used_at IS NULL AND expires_at > now()
                        RETURNING user_id""")
                .param("h", hash(token)).param("p", purpose.name()).query(UUID.class).optional();
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
