package com.laa.auth;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Passwords: how they're stored and which ones are allowed.
 * <ul>
 *   <li><b>Storing</b> (OWASP Password Storage Cheat Sheet): Argon2id with 19 MiB of memory, 2 iterations and
 *       parallelism 1, a fresh random 16-byte salt per password, a 32-byte hash. Stored tagged ({@code {argon2}…}) with
 *       its parameters, so {@link #needsUpgrade} spots hashes made with weaker settings and they're redone at the next
 *       sign-in.</li>
 *   <li><b>Allowed</b> (NIST SP 800-63B-4, a password as the only sign-in factor): 15 to 128 characters, counted as
 *       Unicode code points after NFKC normalisation; any characters, spaces included; no composition rules. Refused:
 *       anything on the common-passwords list ({@code auth/common-passwords.txt}), the learner's own email, and one
 *       character repeated.</li>
 * </ul>
 * Every password is NFKC-normalised before it's hashed or compared, so the same password typed on different keyboards
 * matches.
 */
@Component
public class Passwords {

    public static final int MIN_LENGTH = 15;
    public static final int MAX_LENGTH = 128;

    private static final String ARGON2 = "argon2";
    private static final String LIST = "auth/common-passwords.txt";

    private final PasswordEncoder encoder;
    private final Set<String> common;
    /** Compared against when there's no stored hash, so an unknown email takes as long as a wrong password. */
    private final String decoy;

    Passwords() {
        this(new Argon2PasswordEncoder(16, 32, 1, 19 * 1024, 2));
    }

    /** With a given Argon2 encoder; tests use a weaker one to make an old-style hash. */
    Passwords(Argon2PasswordEncoder argon2) {
        this.encoder = new DelegatingPasswordEncoder(ARGON2, Map.of(ARGON2, argon2));
        this.common = load();
        this.decoy = encoder.encode(UUID.randomUUID().toString());
    }

    public String hash(String password) {
        return encoder.encode(normalise(password));
    }

    /** Whether {@code password} is the one {@code hash} was made from. With no hash it still does the work, then says no. */
    public boolean matches(String password, String hash) {
        String typed = normalise(password == null ? "" : password);
        if (hash == null) {
            encoder.matches(typed, decoy);
            return false;
        }
        return encoder.matches(typed, hash);
    }

    /** Made with weaker settings than today's: hash it again while the password is at hand. */
    public boolean needsUpgrade(String hash) {
        return hash != null && encoder.upgradeEncoding(hash);
    }

    /** What's wrong with {@code password} as a new password for {@code email}, in words a learner can act on. */
    public Optional<String> problem(String password, String email) {
        String p = normalise(password == null ? "" : password);
        int length = p.codePointCount(0, p.length());
        if (length < MIN_LENGTH) {
            return Optional.of("Use at least " + MIN_LENGTH + " characters. Three or four random words work well.");
        }
        if (length > MAX_LENGTH) return Optional.of("Use " + MAX_LENGTH + " characters or fewer.");
        String lower = p.toLowerCase(Locale.ROOT);
        if (common.contains(lower)) {
            return Optional.of("That password is one of the most common, so it’s easy to guess. Try three or four random words.");
        }
        String mail = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        String name = mail.contains("@") ? mail.substring(0, mail.indexOf('@')) : mail;
        if (!mail.isEmpty() && (lower.contains(mail) || lower.strip().equals(name))) {
            return Optional.of("Your password can’t be your email address.");
        }
        if (p.codePoints().distinct().count() == 1) return Optional.of("That’s one character repeated. Try three or four random words.");
        return Optional.empty();
    }

    static String normalise(String password) {
        return Normalizer.normalize(password, Normalizer.Form.NFKC);
    }

    private static Set<String> load() {
        Set<String> out = new HashSet<>();
        try (InputStream in = Passwords.class.getClassLoader().getResourceAsStream(LIST)) {
            if (in == null) throw new IllegalStateException("Missing " + LIST);
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line; (line = r.readLine()) != null; ) {
                if (line.isBlank() || line.startsWith("#")) continue;
                out.add(normalise(line).toLowerCase(Locale.ROOT));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return Set.copyOf(out);
    }
}
