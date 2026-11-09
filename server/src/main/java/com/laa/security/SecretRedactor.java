package com.laa.security;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.regex.Pattern;

/**
 * Scrubs secrets from any text that might reach a log, the database or an API response.
 * Knows the exact configured secrets (registered at startup) and also masks common key shapes.
 * Static so the Logback converters can use it before/without Spring.
 */
public final class SecretRedactor {

    public static final String MASK = "[REDACTED]";

    private static final Set<String> SECRETS = new CopyOnWriteArraySet<>();

    private static final List<Pattern> KEY_SHAPES = List.of(
            Pattern.compile("sk-(?:ant-|proj-)?[A-Za-z0-9_\\-]{12,}"),   // OpenAI / Anthropic
            Pattern.compile("AIza[0-9A-Za-z_\\-]{20,}"),                 // Google
            Pattern.compile("(?i)(bearer\\s+)[A-Za-z0-9._\\-]{12,}"),     // bearer tokens
            Pattern.compile("(?i)((?:api[_-]?key|x-api-key|x-goog-api-key)[\"']?\\s*[:=]\\s*[\"']?)[^\\s\"',}&]+"));

    private SecretRedactor() {}

    /** Register a secret value; blanks and very short values (likely not secrets) are ignored. */
    public static void register(String secret) {
        if (secret != null && secret.strip().length() >= 6) {
            SECRETS.add(secret.strip());
        }
    }

    public static String redact(String text) {
        if (text == null || text.isEmpty()) return text;
        String out = text;
        for (String secret : SECRETS) {
            if (out.contains(secret)) out = out.replace(secret, MASK);
        }
        for (Pattern p : KEY_SHAPES) {
            out = p.matcher(out).replaceAll(m -> m.groupCount() >= 1 && m.group(1) != null
                    ? java.util.regex.Matcher.quoteReplacement(m.group(1) + MASK)
                    : MASK);
        }
        return out;
    }

    /** Test hook. */
    static void clearForTests() {
        SECRETS.clear();
    }
}
