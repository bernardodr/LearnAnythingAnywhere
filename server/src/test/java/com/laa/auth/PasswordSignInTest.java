package com.laa.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import com.laa.IntegrationTest;

/**
 * Email and password sign-in, through the API: how passwords are stored, which are allowed, limits on guessing,
 * password changes ending other sign-ins, and owner accounts.
 */
class PasswordSignInTest extends IntegrationTest {

    // ------------------------------------------------------------------ signing up and in

    @Test
    void aLearnerSignsUpAndSignsInWithTheirPassword() {
        String email = email();
        Resp up = signUp(email, PASSWORD);
        assertThat(up.status()).as(up.raw()).isEqualTo(201);
        assertThat(up.body().get("user").get("role").asString()).isEqualTo("LEARNER");
        assertThat(up.body().get("user").get("hasPassword").asBoolean()).isTrue();

        Resp in = signIn(email.toUpperCase(), PASSWORD);
        assertThat(in.status()).as(in.raw()).isEqualTo(200);
        assertThat(get("/auth/me", in.body().get("token").asString()).body().get("email").asString()).isEqualTo(email);
    }

    @Test
    void everyAccountIsTheSameKindAndNobodyCanChooseOwner() {
        Resp asOwner = post("/auth/sign-up", null, Map.of("email", email(), "password", PASSWORD, "role", "OWNER"));
        assertThat(asOwner.status()).as(asOwner.raw()).isEqualTo(201);
        assertThat(asOwner.body().get("user").get("role").asString()).isEqualTo("LEARNER");
    }

    @Test
    void anEmailCanOnlySignUpOnce() {
        String email = email();
        ok(signUp(email, PASSWORD));
        Resp again = signUp(email, "a different but long password");
        assertThat(again.status()).isEqualTo(409);
        assertThat(again.body().get("detail").asString()).contains("already exists");
    }

    @Test
    void passwordsAreStoredAsSaltedArgon2idNeverAsTyped() {
        String a = email(), b = email();
        ok(signUp(a, PASSWORD));
        ok(signUp(b, PASSWORD));
        String hashA = hashOf(a), hashB = hashOf(b);
        assertThat(hashA).startsWith("{argon2}$argon2id$v=19$m=19456,t=2,p=1$").doesNotContain(PASSWORD).doesNotContain("kettle");
        assertThat(hashA).as("a fresh salt per password").isNotEqualTo(hashB);
    }

    @Test
    void aWrongPasswordAndAnUnknownEmailGetTheSameAnswer() {
        String email = email();
        ok(signUp(email, PASSWORD));
        Resp wrong = signIn(email, "not the right password at all");
        Resp unknown = signIn(email(), PASSWORD);
        assertThat(wrong.status()).isEqualTo(401);
        assertThat(unknown.status()).isEqualTo(401);
        assertThat(wrong.body().get("detail").asString()).isEqualTo(unknown.body().get("detail").asString())
                .isEqualTo("Email or password is incorrect.");
    }

    @Test
    void theTitlePageIsToldWhatItCanOffer() {
        Resp options = get("/auth/options", null);
        assertThat(options.body().get("devLogin").asBoolean()).isTrue();
        assertThat(options.body().get("passwordMinLength").asInt()).isEqualTo(15);
    }

    // ------------------------------------------------------------------ the rules

    @Test
    void weakPasswordsAreRefusedWithAReason() {
        String email = email();
        assertThat(problem(email, "fourteen chars")).contains("at least 15 characters");
        assertThat(problem(email, "1q2w3e4r5t6y7u8i9o0p")).contains("most common");
        assertThat(problem(email, "Learn Anything Anywhere")).contains("most common");
        assertThat(problem(email, "my " + email + " pw")).contains("email");
        assertThat(problem(email, "aaaaaaaaaaaaaaaaaaaa")).contains("one character repeated");
        assertThat(problem(email, "x".repeat(64) + "y".repeat(65))).contains("128 characters or fewer");
        assertThat(jdbc.sql("SELECT count(*) FROM users WHERE email = :e").param("e", email).query(Integer.class).single()).isZero();

        assertThat(signUp(email, "tulip orbit sky!").status()).as("15+ characters, any characters").isEqualTo(201);
    }

    @Test
    void theSamePasswordTypedOnAnotherKeyboardStillMatches() {
        String email = email();
        ok(signUp(email, "ｃｏｐｐｅｒ ｋｅｔｔｌｅ ｎｏｒｔｈ")); // full-width letters, as some keyboards type them
        assertThat(signIn(email, "copper kettle north").status()).isEqualTo(200);
    }

    // ------------------------------------------------------------------ guessing

    @Test
    void fiveWrongTriesPauseThatEmailEvenForTheRightPassword() {
        String email = email(), other = email();
        ok(signUp(email, PASSWORD));
        ok(signUp(other, PASSWORD));
        for (int i = 0; i < 5; i++) assertThat(signIn(email, "wrong password number " + i).status()).isEqualTo(401);

        Resp paused = signIn(email, PASSWORD);
        assertThat(paused.status()).isEqualTo(429);
        assertThat(paused.body().get("detail").asString()).contains("Too many tries").contains("15 minutes");
        assertThat(signIn(other, PASSWORD).status()).as("other emails carry on").isEqualTo(200);
    }

    @Test
    void oneAddressGetsTwentyTries() {
        String email = email();
        ok(signUp(email, PASSWORD));
        for (int i = 0; i < 20; i++) assertThat(signIn(email(), PASSWORD).status()).isEqualTo(401);
        assertThat(signIn(email, PASSWORD).status()).isEqualTo(429);
    }

    @Test
    void aRightPasswordClearsTheEmailsCount() {
        String email = email();
        ok(signUp(email, PASSWORD));
        for (int i = 0; i < 4; i++) signIn(email, "wrong password number " + i);
        ok(signIn(email, PASSWORD));
        for (int i = 0; i < 4; i++) signIn(email, "wrong password again " + i);
        assertThat(signIn(email, PASSWORD).status()).isEqualTo(200);
    }

    // ------------------------------------------------------------------ changing a password

    @Test
    void changingYourPasswordEndsYourOtherSignInsButNotThisOne() {
        String email = email();
        ok(signUp(email, PASSWORD));
        String laptop = token(signIn(email, PASSWORD));
        String phone = token(signIn(email, PASSWORD));

        Resp wrongCurrent = put("/auth/password", laptop, Map.of("currentPassword", "not my password at all", "newPassword", "a brand new long password"));
        assertThat(wrongCurrent.status()).isEqualTo(403);

        Resp changed = put("/auth/password", laptop, Map.of("currentPassword", PASSWORD, "newPassword", "a brand new long password"));
        assertThat(changed.status()).as(changed.raw()).isEqualTo(200);
        String fresh = token(changed);

        assertThat(get("/auth/me", laptop).status()).isEqualTo(401);
        assertThat(get("/auth/me", phone).status()).isEqualTo(401);
        assertThat(get("/auth/me", fresh).status()).isEqualTo(200);
        assertThat(signIn(email, PASSWORD).status()).isEqualTo(401);
        assertThat(signIn(email, "a brand new long password").status()).isEqualTo(200);
    }

    @Test
    void aNewPasswordFollowsTheRules() {
        String email = email();
        String token = token(signUp(email, PASSWORD));
        Resp weak = put("/auth/password", token, Map.of("currentPassword", PASSWORD, "newPassword", "short"));
        assertThat(weak.status()).isEqualTo(400);
        assertThat(get("/auth/me", token).status()).as("nothing changed").isEqualTo(200);
    }

    @Test
    void anAccountWithNoPasswordYetCanSetOne() {
        String email = email();
        String dev = login(email);
        assertThat(get("/auth/me", dev).body().get("hasPassword").asBoolean()).isFalse();
        assertThat(signIn(email, PASSWORD).status()).as("no password, no password sign-in").isEqualTo(401);

        Map<String, Object> first = new HashMap<>();
        first.put("newPassword", PASSWORD);
        Resp set = put("/auth/password", dev, first);
        assertThat(set.status()).as(set.raw()).isEqualTo(200);
        assertThat(set.body().get("user").get("hasPassword").asBoolean()).isTrue();
        assertThat(signIn(email, PASSWORD).status()).isEqualTo(200);

        Map<String, Object> again = new HashMap<>();
        again.put("newPassword", "another long password here");
        assertThat(put("/auth/password", token(set), again).status()).as("now the current one is needed").isEqualTo(403);
    }

    @Test
    void aHashMadeWithWeakerSettingsIsRedoneAtSignIn() {
        String email = email();
        ok(signUp(email, PASSWORD));
        String weak = "{argon2}" + new Argon2PasswordEncoder(16, 32, 1, 4096, 1).encode(PASSWORD);
        jdbc.sql("UPDATE users SET password_hash = :h WHERE email = :e").param("h", weak).param("e", email).update();

        ok(signIn(email, PASSWORD));
        assertThat(hashOf(email)).startsWith("{argon2}$argon2id$v=19$m=19456,t=2,p=1$");
        assertThat(signIn(email, PASSWORD).status()).isEqualTo(200);
    }

    // ------------------------------------------------------------------ owners

    @Test
    void anOwnerEmailNeedsTheSetupCodeToSignUp() {
        String founder = "founder@test.dev";
        jdbc.sql("DELETE FROM users WHERE email = :e").param("e", founder).update();

        Resp none = signUp(founder, PASSWORD);
        assertThat(none.status()).isEqualTo(403);
        assertThat(none.body().get("code").asString()).isEqualTo("owner-setup-code");
        Resp wrong = post("/auth/sign-up", null, Map.of("email", founder, "password", PASSWORD, "ownerSetupCode", "guess"));
        assertThat(wrong.status()).isEqualTo(403);
        assertThat(wrong.body().get("detail").asString()).contains("isn’t right");

        Resp right = post("/auth/sign-up", null, Map.of("email", founder, "password", PASSWORD, "ownerSetupCode", "test-owner-setup-code"));
        assertThat(right.status()).as(right.raw()).isEqualTo(201);
        assertThat(right.body().get("user").get("role").asString()).isEqualTo("OWNER");
        assertThat(get("/owner/calls", token(right)).status()).isEqualTo(200);
    }

    @Test
    void anOwnerTakenOffTheListIsALearnerAtTheirNextSignIn() {
        String email = email();
        ok(signUp(email, PASSWORD));
        jdbc.sql("UPDATE users SET role = 'OWNER' WHERE email = :e").param("e", email).update();

        Resp in = signIn(email, PASSWORD);
        assertThat(in.body().get("user").get("role").asString()).isEqualTo("LEARNER");
        assertThat(get("/owner/calls", token(in)).status()).isEqualTo(403);
    }

    // ------------------------------------------------------------------ helpers

    private static String email() {
        return "pw-" + UUID.randomUUID().toString().substring(0, 8) + "@test.dev";
    }

    private Resp signUp(String email, String password) {
        return post("/auth/sign-up", null, Map.of("email", email, "password", password));
    }

    private Resp signIn(String email, String password) {
        return post("/auth/sign-in", null, Map.of("email", email, "password", password));
    }

    private String problem(String email, String password) {
        Resp r = signUp(email, password);
        assertThat(r.status()).as(r.raw()).isEqualTo(400);
        return r.body().get("detail").asString();
    }

    private String hashOf(String email) {
        return jdbc.sql("SELECT password_hash FROM users WHERE email = :e").param("e", email).query(String.class).single();
    }

    private static String token(Resp r) {
        assertThat(r.status()).as(r.raw()).isBetween(200, 299);
        return r.body().get("token").asString();
    }
}
