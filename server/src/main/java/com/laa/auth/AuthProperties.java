package com.laa.auth;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code laa.auth.*}
 * <ul>
 *   <li>{@code ownerEmails}: the only emails that may be OWNER;</li>
 *   <li>{@code ownerSetupCode}: what signing up with one of those emails needs; while it's unset they can't sign up;</li>
 *   <li>{@code devLoginEnabled}: email-only sign-in, for local development and tests; off unless turned on.</li>
 * </ul>
 */
@ConfigurationProperties("laa.auth")
public record AuthProperties(
        String jwtSecret,
        @DefaultValue("12h") Duration tokenTtl,
        List<String> ownerEmails,
        String ownerSetupCode,
        @DefaultValue("false") boolean devLoginEnabled) {

    public AuthProperties {
        ownerEmails = ownerEmails == null ? List.of()
                : ownerEmails.stream().map(e -> e.strip().toLowerCase()).filter(e -> !e.isEmpty()).toList();
        ownerSetupCode = ownerSetupCode == null || ownerSetupCode.isBlank() ? null : ownerSetupCode.strip();
    }

    public boolean isOwnerEmail(String email) {
        return ownerEmails.contains(email.toLowerCase());
    }

    @Override
    public String toString() {
        return "AuthProperties[jwtSecret=[hidden], tokenTtl=" + tokenTtl + ", ownerEmails=" + ownerEmails
                + ", ownerSetupCode=" + (ownerSetupCode == null ? "[unset]" : "[hidden]") + ", devLoginEnabled=" + devLoginEnabled + "]";
    }
}
