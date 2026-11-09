package com.laa.auth;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code laa.auth.*} */
@ConfigurationProperties("laa.auth")
public record AuthProperties(
        String jwtSecret,
        @DefaultValue("12h") Duration tokenTtl,
        List<String> ownerEmails,
        @DefaultValue("true") boolean devLoginEnabled) {

    public AuthProperties {
        ownerEmails = ownerEmails == null ? List.of()
                : ownerEmails.stream().map(e -> e.strip().toLowerCase()).filter(e -> !e.isEmpty()).toList();
    }

    public boolean isOwnerEmail(String email) {
        return ownerEmails.contains(email.toLowerCase());
    }

    @Override
    public String toString() {
        return "AuthProperties[jwtSecret=[hidden], tokenTtl=" + tokenTtl + ", ownerEmails=" + ownerEmails
                + ", devLoginEnabled=" + devLoginEnabled + "]";
    }
}
