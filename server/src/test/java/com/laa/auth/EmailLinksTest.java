package com.laa.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.laa.IntegrationTest;

/** Verifying an email, resetting a forgotten password, and the limits on sign-ups and emails. */
class EmailLinksTest extends IntegrationTest {

    // ------------------------------------------------------------------ verifying

    @Test
    void signingUpSendsALinkThatVerifiesTheEmailOnce() {
        String email = email();
        Resp up = signUp(email);
        assertThat(up.body().get("user").get("emailVerified").asBoolean()).isFalse();
        String token = up.body().get("token").asString();

        var sent = mailer.outbox().getLast();
        assertThat(sent.subject()).contains("Confirm your email");
        assertThat(sent.body()).contains("http://localhost:8081/verify-email?token=");
        String link = emailedToken(email, "/verify-email");
        assertThat(hashes()).as("only a hash is stored").doesNotContain(link);

        assertThat(post("/auth/verify-email", null, Map.of("token", link)).status()).isEqualTo(200);
        assertThat(get("/auth/me", token).body().get("emailVerified").asBoolean()).isTrue();
        Resp again = post("/auth/verify-email", null, Map.of("token", link));
        assertThat(again.status()).isEqualTo(400);
        assertThat(again.body().get("detail").asString()).contains("expired or has already been used");
    }

    @Test
    void anExpiredLinkDoesntWork() {
        String email = email();
        signUp(email);
        String link = emailedToken(email, "/verify-email");
        jdbc.sql("UPDATE email_tokens SET expires_at = now() - interval '1 minute'").update();
        assertThat(post("/auth/verify-email", null, Map.of("token", link)).status()).isEqualTo(400);
    }

    @Test
    void aNewLinkReplacesTheOldOne() {
        String email = email();
        String token = signUp(email).body().get("token").asString();
        String first = emailedToken(email, "/verify-email");
        assertThat(post("/auth/verify-email/resend", token, null).body().get("sent").asBoolean()).isTrue();
        String second = emailedToken(email, "/verify-email");
        assertThat(second).isNotEqualTo(first);
        assertThat(post("/auth/verify-email", null, Map.of("token", first)).status()).isEqualTo(400);
        assertThat(post("/auth/verify-email", null, Map.of("token", second)).status()).isEqualTo(200);
        assertThat(post("/auth/verify-email/resend", token, null).body().get("sent").asBoolean()).as("nothing left to verify").isFalse();
    }

    // ------------------------------------------------------------------ resetting a password

    @Test
    void aResetLinkSetsANewPasswordEndsOldSignInsAndWorksOnce() {
        String email = email();
        String before = signUp(email).body().get("token").asString();

        Resp asked = post("/auth/password-reset", null, Map.of("email", email.toUpperCase()));
        assertThat(asked.status()).isEqualTo(202);
        var sent = mailer.outbox().getLast();
        assertThat(sent.subject()).contains("Reset your");
        String link = emailedToken(email, "/reset-password");

        Resp weak = post("/auth/password-reset/confirm", null, Map.of("token", link, "newPassword", "short"));
        assertThat(weak.status()).as("the rules apply, and the link still works").isEqualTo(400);

        Resp reset = post("/auth/password-reset/confirm", null, Map.of("token", link, "newPassword", "a brand new long password"));
        assertThat(reset.status()).as(reset.raw()).isEqualTo(200);
        assertThat(reset.body().get("user").get("emailVerified").asBoolean()).as("the link proved the inbox").isTrue();
        assertThat(get("/auth/me", reset.body().get("token").asString()).status()).isEqualTo(200);
        assertThat(get("/auth/me", before).status()).as("older sign-ins end").isEqualTo(401);
        assertThat(post("/auth/sign-in", null, Map.of("email", email, "password", PASSWORD)).status()).isEqualTo(401);
        assertThat(post("/auth/sign-in", null, Map.of("email", email, "password", "a brand new long password")).status()).isEqualTo(200);

        assertThat(post("/auth/password-reset/confirm", null, Map.of("token", link, "newPassword", "yet another long password")).status())
                .as("once only").isEqualTo(400);
    }

    @Test
    void anUnknownEmailGetsTheSameAnswerAndNoEmail() {
        String email = email();
        signUp(email);
        mailer.clearOutbox();
        Resp known = post("/auth/password-reset", null, Map.of("email", email));
        Resp unknown = post("/auth/password-reset", null, Map.of("email", email()));
        assertThat(unknown.status()).isEqualTo(known.status());
        assertThat(unknown.body().get("message").asString()).isEqualTo(known.body().get("message").asString());
        assertThat(mailer.outbox()).hasSize(1);
    }

    @Test
    void anAddressGetsAtMostThreeEmailsIn15Minutes() {
        String email = email();
        signUp(email); // the verification email is the first
        for (int i = 0; i < 4; i++) assertThat(post("/auth/password-reset", null, Map.of("email", email)).status()).isEqualTo(202);
        assertThat(mailer.outbox().stream().filter(m -> m.to().equals(email))).hasSize(3);
    }

    @Test
    void oneNetworkAddressGetsTenResetRequests() {
        for (int i = 0; i < 10; i++) assertThat(post("/auth/password-reset", null, Map.of("email", email())).status()).isEqualTo(202);
        assertThat(post("/auth/password-reset", null, Map.of("email", email())).status()).isEqualTo(429);
    }

    // ------------------------------------------------------------------ sign-ups

    @Test
    void oneNetworkAddressMakesAtMostFiveAccountsIn15Minutes() {
        for (int i = 0; i < 5; i++) assertThat(signUp(email()).status()).isEqualTo(201);
        Resp sixth = signUp(email());
        assertThat(sixth.status()).isEqualTo(429);
        assertThat(sixth.body().get("detail").asString()).contains("Too many tries");
    }

    // ------------------------------------------------------------------ helpers

    private static String email() {
        return "mail-" + UUID.randomUUID().toString().substring(0, 8) + "@test.dev";
    }

    private Resp signUp(String email) {
        Resp r = post("/auth/sign-up", null, Map.of("email", email, "password", PASSWORD));
        return r;
    }

    private java.util.List<String> hashes() {
        return jdbc.sql("SELECT token_hash FROM email_tokens").query(String.class).list();
    }
}
