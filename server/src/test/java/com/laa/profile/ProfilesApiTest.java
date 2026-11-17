package com.laa.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.laa.IntegrationTest;

import tools.jackson.databind.JsonNode;

/**
 * Reader settings and the parent PIN (BRIEF sections 8 and 9, screen 7). Accounts are for adults; a child uses a
 * parent's account, whose settings the parent sets for them and locks with the PIN.
 */
class ProfilesApiTest extends IntegrationTest {

    @Test
    void anAccountStartsWithAdultDefaultsAndCanBeSetUpForAChild() {
        String token = login("settings-" + UUID.randomUUID().toString().substring(0, 8) + "@test.dev");
        JsonNode me = get("/profiles/me", token).body();
        assertThat(me.get("age").isNull()).isTrue();
        assertThat(me.get("readingLevel").asString()).isEqualTo("Adult");
        assertThat(me.get("childSafe").asBoolean()).isFalse();
        assertThat(me.get("linksPolicy").asString()).isEqualTo("ALLOWLIST");
        assertThat(me.get("pinSet").asBoolean()).isFalse();
        assertThat(me.get("allCategories").size()).isEqualTo(5);

        Resp young = put("/profiles/" + me.get("id").asString(), token, settings(9, true, List.of(), List.of("spiders"), "OFF", 3));
        assertThat(young.status()).as(young.raw()).isEqualTo(200);
        assertThat(young.body().get("readingLevel").asString()).isEqualTo("Confident reader (ages 9–10)");
        assertThat(young.body().get("extras").toString()).isEqualTo("[\"spiders\"]");

        assertThat(put("/profiles/" + me.get("id").asString(), token, settings(4, true, List.of(), List.of(), "OFF", null)).status()).isEqualTo(400);
        assertThat(put("/profiles/" + me.get("id").asString(), token, settings(null, false, List.of("nope"), List.of(), "OFF", null)).status())
                .isEqualTo(400);
    }

    @Test
    void someoneElsesSettingsAreNotFound() {
        ChildReader a = childReader(9, true);
        String other = login("settings-other-" + UUID.randomUUID().toString().substring(0, 8) + "@test.dev");
        assertThat(get("/profiles/" + a.profileId(), other).status()).isEqualTo(404);
        assertThat(put("/profiles/" + a.profileId(), other, settings(null, false, List.of(), List.of(), "OPEN", null)).status()).isEqualTo(404);
    }

    @Test
    void onceAPinIsSetChangingTheSettingsNeedsIt() {
        ChildReader r = childReader(7, true);
        assertThat(put("/profiles/pin", r.token(), Map.of("pin", "12")).status()).isEqualTo(400);
        ok(put("/profiles/pin", r.token(), Map.of("pin", "2468")));
        assertThat(get("/profiles/me", r.token()).body().get("pinSet").asBoolean()).isTrue();

        Map<String, Object> change = settings(null, false, List.of(), List.of(), "OPEN", null);
        String path = "/profiles/" + r.profileId();
        Resp noPin = put(path, r.token(), change);
        assertThat(noPin.status()).isEqualTo(403);
        assertThat(noPin.body().get("detail").asString()).contains("parent PIN");
        assertThat(call(HttpMethod.PUT, path, r.token(), change, Map.of("X-Parent-Pin", "1111")).status()).isEqualTo(403);
        assertThat(get("/profiles/me", r.token()).body().get("age").asInt()).as("unchanged").isEqualTo(7);

        Resp withPin = call(HttpMethod.PUT, path, r.token(), change, Map.of("X-Parent-Pin", "2468"));
        ok(withPin);
        assertThat(withPin.body().get("readingLevel").asString()).isEqualTo("Adult");

        // Changing the PIN needs the current one.
        assertThat(put("/profiles/pin", r.token(), Map.of("pin", "1357")).status()).isEqualTo(403);
        ok(put("/profiles/pin", r.token(), Map.of("pin", "1357", "currentPin", "2468")));
    }

    @Test
    void thePinLocksThePasswordToo() {
        String email = "lock-" + UUID.randomUUID().toString().substring(0, 8) + "@test.dev";
        String token = post("/auth/sign-up", null, Map.of("email", email, "password", PASSWORD)).body().get("token").asString();
        ok(put("/profiles/pin", token, Map.of("pin", "2468")));

        Resp noPin = put("/auth/password", token, Map.of("currentPassword", PASSWORD, "newPassword", "a brand new long password"));
        assertThat(noPin.status()).isEqualTo(403);
        assertThat(noPin.body().get("detail").asString()).contains("parent PIN");
        Resp withPin = put("/auth/password", token, Map.of("currentPassword", PASSWORD, "newPassword", "a brand new long password", "pin", "2468"));
        assertThat(withPin.status()).as(withPin.raw()).isEqualTo(200);
    }

    @Test
    void aForgottenPinIsReplacedWithTheAccountPassword() {
        String email = "forgot-pin-" + UUID.randomUUID().toString().substring(0, 8) + "@test.dev";
        String token = post("/auth/sign-up", null, Map.of("email", email, "password", PASSWORD)).body().get("token").asString();
        ok(put("/profiles/pin", token, Map.of("pin", "2468")));

        assertThat(post("/profiles/pin/reset", token, Map.of("password", "not the password at all", "pin", "9753")).status()).isEqualTo(403);
        ok(post("/profiles/pin/reset", token, Map.of("password", PASSWORD, "pin", "9753")));
        String profile = get("/profiles/me", token).body().get("id").asString();
        Map<String, Object> change = settings(10, true, List.of(), List.of(), "OFF", null);
        assertThat(call(HttpMethod.PUT, "/profiles/" + profile, token, change, Map.of("X-Parent-Pin", "2468")).status()).isEqualTo(403);
        ok(call(HttpMethod.PUT, "/profiles/" + profile, token, change, Map.of("X-Parent-Pin", "9753")));
    }

    @Test
    void theLockCanBeTurnedOffWithThePin() {
        ChildReader r = childReader(8, true);
        ok(put("/profiles/pin", r.token(), Map.of("pin", "2468")));
        assertThat(call(HttpMethod.DELETE, "/profiles/pin", r.token(), null, Map.of("X-Parent-Pin", "1111")).status()).isEqualTo(403);
        ok(call(HttpMethod.DELETE, "/profiles/pin", r.token(), null, Map.of("X-Parent-Pin", "2468")));
        assertThat(get("/profiles/me", r.token()).body().get("pinSet").asBoolean()).isFalse();
        ok(put("/profiles/" + r.profileId(), r.token(), settings(null, false, List.of(), List.of(), "OPEN", null)));
    }

    @Test
    void fiveWrongPinsPauseThePinEvenForTheRightOne() {
        ChildReader r = childReader(8, true);
        ok(put("/profiles/pin", r.token(), Map.of("pin", "2468")));
        Map<String, Object> change = settings(8, true, List.of(), List.of(), "OFF", null);
        String path = "/profiles/" + r.profileId();
        for (int i = 0; i < 5; i++) {
            assertThat(call(HttpMethod.PUT, path, r.token(), change, Map.of("X-Parent-Pin", "100" + i)).status()).isEqualTo(403);
        }
        Resp paused = call(HttpMethod.PUT, path, r.token(), change, Map.of("X-Parent-Pin", "2468"));
        assertThat(paused.status()).isEqualTo(429);
        assertThat(paused.body().get("detail").asString()).contains("Too many tries");
    }

    @Test
    void readerSettingsShapeWhatTheTopicGateAllows() {
        String adult = login("settings-gate@test.dev");
        String id = get("/profiles/me", adult).body().get("id").asString();
        assertThat(post("/textbooks", adult, Map.of("topic", "Poker strategy")).status()).isEqualTo(201);
        ok(put("/profiles/" + id, adult, settings(null, false, List.of("gambling"), List.of(), "ALLOWLIST", null)));
        Resp declined = post("/textbooks", adult, Map.of("topic", "Blackjack basics"));
        assertThat(declined.body().get("verdict").asString()).isEqualTo("DECLINE");
        assertThat(declined.body().get("message").asString()).contains("gambling", "reader settings");
    }

    private static Map<String, Object> settings(Integer age, boolean childSafe, List<String> categories, List<String> extras,
                                                String links, Integer reveal) {
        Map<String, Object> m = new HashMap<>();
        m.put("age", age);
        m.put("childSafe", childSafe);
        m.put("categories", categories);
        m.put("extras", extras);
        m.put("linksPolicy", links);
        m.put("revealAfterAttempts", reveal);
        return m;
    }
}
