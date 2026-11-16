package com.laa.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.laa.security.SecretRedactor;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * HS256 sign-in tokens signed with JWT_SECRET. Claims: sub (user id), email, role, and {@value #VERSION_CLAIM}, the
 * user's password version when it was issued. A token counts only while that's still the user's version, so changing
 * or resetting a password ends every older sign-in. Versions are cached for a minute and forgotten on a change.
 * Prototype-grade otherwise: no refresh tokens.
 */
@Component
public class JwtService {

    static final String ISSUER = "laa";
    public static final String ROLE_CLAIM = "role";
    static final String VERSION_CLAIM = "pv";

    private final JwtEncoder encoder;
    private final NimbusJwtDecoder decoder;
    private final AuthProperties props;
    private final UserRepository users;
    private final Cache<UUID, Integer> versions = Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(1)).maximumSize(100_000).build();

    public JwtService(AuthProperties props, UserRepository users) {
        this.props = props;
        this.users = users;
        String secret = props.jwtSecret() == null ? "" : props.jwtSecret();
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32 || "change-me".equals(secret)) {
            throw new IllegalStateException(
                    "JWT_SECRET must be set to at least 32 bytes (generate one with: openssl rand -hex 32)");
        }
        SecretRedactor.register(secret);
        SecretKey key = new SecretKeySpec(bytes, "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        this.decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(ISSUER), stillCurrent()));
    }

    public String issue(AppUser user, int passwordVersion) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(user.id().toString())
                .issuedAt(now)
                .expiresAt(now.plus(props.tokenTtl()))
                .claim("email", user.email())
                .claim(ROLE_CLAIM, user.role().name())
                .claim(VERSION_CLAIM, passwordVersion)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /** The user's password changed: check their tokens against the database again. */
    public void forget(UUID userId) {
        versions.invalidate(userId);
    }

    public long ttlSeconds() {
        return props.tokenTtl().toSeconds();
    }

    JwtDecoder decoder() {
        return decoder;
    }

    private OAuth2TokenValidator<Jwt> stillCurrent() {
        OAuth2Error ended = new OAuth2Error("invalid_token", "This sign-in has ended. Please sign in again.", null);
        return jwt -> {
            UUID id;
            try {
                id = UUID.fromString(jwt.getSubject());
            } catch (RuntimeException e) {
                return OAuth2TokenValidatorResult.failure(ended);
            }
            Object claim = jwt.getClaims().get(VERSION_CLAIM);
            Integer current = versions.get(id, users::passwordVersion);
            return claim instanceof Number n && current != null && n.intValue() == current
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(ended);
        };
    }
}
