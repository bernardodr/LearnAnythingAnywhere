package com.laa.profile;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.laa.llm.GenerationProfile;
import com.laa.llm.GenerationProfile.LinksPolicy;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Generation profiles (reader settings) and the users they belong to. */
@Repository
public class ProfileRepository {

    /** A stored profile: whose it is ({@code userId}), who manages it ({@code ownerUserId}, the same account) and its settings. */
    public record Stored(UUID id, UUID userId, UUID ownerUserId, GenerationProfile settings) {}

    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};
    private static final String COLUMNS = "p.id, u.id AS user_id, p.owner_user_id, p.age, p.child_safe, p.filters::text AS filters, "
            + "p.links_policy, p.reveal_after_attempts, p.level";

    private final JdbcClient jdbc;
    private final JsonMapper json;

    ProfileRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public Optional<Stored> forUser(UUID userId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM users u JOIN generation_profiles p ON p.id = u.profile_id WHERE u.id = :u")
                .param("u", userId).query(this::map).optional();
    }

    public Optional<Stored> find(UUID profileId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM generation_profiles p JOIN users u ON u.profile_id = p.id WHERE p.id = :p")
                .param("p", profileId).query(this::map).optional();
    }

    /**
     * The user's profile, creating it with {@code s} if they have none. Safe when several first requests arrive at once:
     * the user's row is locked while it's checked and created, so exactly one profile is made and everyone gets it.
     */
    @Transactional
    public Stored forUserOrCreate(UUID userId, GenerationProfile s) {
        Optional<Stored> existing = forUser(userId);
        if (existing.isPresent()) return existing.get();
        jdbc.sql("SELECT id FROM users WHERE id = :u FOR UPDATE").param("u", userId).query(UUID.class).optional();
        return forUser(userId).orElseGet(() -> create(userId, userId, s));
    }

    /** Creates a profile for {@code userId}, managed by {@code ownerUserId}, and attaches it to the user. */
    @Transactional
    public Stored create(UUID userId, UUID ownerUserId, GenerationProfile s) {
        UUID id = jdbc.sql("INSERT INTO generation_profiles (owner_user_id, age, child_safe, filters, links_policy, reveal_after_attempts, level) "
                        + "VALUES (:o, :age, :cs, :f::jsonb, :lp, :r, :lvl) RETURNING id")
                .param("o", ownerUserId).param("age", s.age()).param("cs", s.childSafe())
                .param("f", json.writeValueAsString(s.filters().stream().sorted().toList()))
                .param("lp", s.linksPolicy().name()).param("r", s.revealAfterAttempts()).param("lvl", s.level())
                .query(UUID.class).single();
        jdbc.sql("UPDATE users SET profile_id = :p WHERE id = :u").param("p", id).param("u", userId).update();
        return find(id).orElseThrow();
    }

    public Stored update(UUID profileId, GenerationProfile s) {
        jdbc.sql("UPDATE generation_profiles SET age = :age, child_safe = :cs, filters = :f::jsonb, links_policy = :lp, "
                        + "reveal_after_attempts = :r, level = :lvl WHERE id = :id")
                .param("age", s.age()).param("cs", s.childSafe())
                .param("f", json.writeValueAsString(s.filters().stream().sorted().toList()))
                .param("lp", s.linksPolicy().name()).param("r", s.revealAfterAttempts()).param("lvl", s.level())
                .param("id", profileId).update();
        return find(profileId).orElseThrow();
    }

    private Stored map(ResultSet rs, int row) throws SQLException {
        int age = rs.getInt("age");
        Integer ageOrNull = rs.wasNull() ? null : age;
        int reveal = rs.getInt("reveal_after_attempts");
        Integer revealOrNull = rs.wasNull() ? null : reveal;
        Set<String> filters = Set.copyOf(json.readValue(rs.getString("filters"), STRINGS));
        return new Stored(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class), rs.getObject("owner_user_id", UUID.class),
                new GenerationProfile(ageOrNull, rs.getBoolean("child_safe"), filters, LinksPolicy.valueOf(rs.getString("links_policy")),
                        revealOrNull, rs.getString("level")));
    }
}
