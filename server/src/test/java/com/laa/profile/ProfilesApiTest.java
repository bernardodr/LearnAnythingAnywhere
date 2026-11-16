package com.laa.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.laa.IntegrationTest;

import tools.jackson.databind.JsonNode;

/** Reader settings, parents and children, and the parent PIN (BRIEF sections 8 and 9, screen 7; milestone M6). */
class ProfilesApiTest extends IntegrationTest {

    @Test
    void anAdultStartsWithDefaultsAndManagesTheirOwnSettings() {
        String adult = login("settings-adult@test.dev");
        JsonNode me = get("/profiles/me", adult).body();
        assertThat(me.get("age").isNull()).isTrue();
        assertThat(me.get("readingLevel").asString()).isEqualTo("Adult");
        assertThat(me.get("childSafe").asBoolean()).isFalse();
        assertThat(me.get("linksPolicy").asString()).isEqualTo("ALLOWLIST");
        assertThat(me.get("canEdit").asBoolean()).isTrue();
        assertThat(me.get("managedByParent").asBoolean()).isFalse();
        assertThat(me.get("allCategories").size()).isEqualTo(5);

        Resp saved = put("/profiles/" + me.get("id").asString(), adult, settings(16, false, List.of("gambling"), List.of("spiders"), "OFF", 3));
        ok(saved);
        assertThat(saved.body().get("age").asInt()).isEqualTo(16);
        assertThat(saved.body().get("readingLevel").asString()).isEqualTo("Older teen (ages 16–18)");
        assertThat(saved.body().get("categories").toString()).isEqualTo("[\"gambling\"]");
        assertThat(saved.body().get("extras").toString()).isEqualTo("[\"spiders\"]");
        assertThat(saved.body().get("revealAfterAttempts").asInt()).isEqualTo(3);
        assertThat(get("/profiles/me", adult).body().get("linksPolicy").asString()).isEqualTo("OFF");

        // Profiles for children under 13 are only made by a parent.
        Resp young = put("/profiles/" + me.get("id").asString(), adult, settings(9, true, List.of(), List.of(), "OFF", null));
        assertThat(young.status()).isEqualTo(400);
        assertThat(young.body().get("detail").asString()).contains("parent");

        Resp unknown = put("/profiles/" + me.get("id").asString(), adult, settings(null, false, List.of("nope"), List.of(), "OFF", null));
        assertThat(unknown.status()).isEqualTo(400);

        // Only a parent can add children.
        assertThat(post("/profiles/children", adult, Map.of("email", "kid@test.dev", "age", 9)).status()).isEqualTo(403);
    }

    @Test
    void aParentAddsAChildWhoCanSeeButNotChangeTheirSettings() {
        Family f = family(9, true);

        JsonNode mine = get("/profiles/me", f.child()).body();
        assertThat(mine.get("id").asString()).isEqualTo(f.childProfileId());
        assertThat(mine.get("age").asInt()).isEqualTo(9);
        assertThat(mine.get("readingLevel").asString()).isEqualTo("Confident reader (ages 9–10)");
        assertThat(mine.get("childSafe").asBoolean()).isTrue();
        assertThat(mine.get("linksPolicy").asString()).isEqualTo("OFF");
        assertThat(mine.get("managedByParent").asBoolean()).isTrue();
        assertThat(mine.get("canEdit").asBoolean()).isFalse();

        Resp childEdit = put("/profiles/" + f.childProfileId(), f.child(), settings(9, false, List.of(), List.of(), "OPEN", null));
        assertThat(childEdit.status()).isEqualTo(403);
        assertThat(childEdit.body().get("detail").asString()).isEqualTo("Your parent manages these settings.");

        JsonNode family = get("/profiles/children", f.parent()).body();
        assertThat(family.get("pinSet").asBoolean()).isFalse();
        assertThat(family.get("children").size()).isEqualTo(1);
        assertThat(family.get("children").get(0).get("profile").get("canEdit").asBoolean()).isTrue();

        // Without a PIN the parent can change it straight away; a child's profile needs an age.
        ok(put("/profiles/" + f.childProfileId(), f.parent(), settings(10, true, List.of(), List.of("spiders"), "OFF", 3)));
        assertThat(put("/profiles/" + f.childProfileId(), f.parent(), settings(null, true, List.of(), List.of(), "OFF", null)).status())
                .isEqualTo(400);

        // Another parent can't see it at all.
        Family other = family(8, true);
        assertThat(get("/profiles/" + f.childProfileId(), other.parent()).status()).isEqualTo(404);
        assertThat(put("/profiles/" + f.childProfileId(), other.parent(), settings(10, false, List.of(), List.of(), "OPEN", null)).status())
                .isEqualTo(404);
    }

    @Test
    void onceAParentSetsAPinEveryChangeToAChildsSettingsNeedsIt() {
        Family f = family(7, true);
        assertThat(put("/profiles/pin", f.parent(), Map.of("pin", "12")).status()).isEqualTo(400);
        ok(put("/profiles/pin", f.parent(), Map.of("pin", "2468")));
        assertThat(get("/profiles/children", f.parent()).body().get("pinSet").asBoolean()).isTrue();

        Map<String, Object> change = settings(7, false, List.of("gambling"), List.of(), "OFF", null);
        String path = "/profiles/" + f.childProfileId();
        Resp noPin = put(path, f.parent(), change);
        assertThat(noPin.status()).isEqualTo(403);
        assertThat(noPin.body().get("detail").asString()).contains("PIN");
        assertThat(call(HttpMethod.PUT, path, f.parent(), change, Map.of("X-Parent-Pin", "1111")).status()).isEqualTo(403);

        Resp withPin = call(HttpMethod.PUT, path, f.parent(), change, Map.of("X-Parent-Pin", "2468"));
        ok(withPin);
        assertThat(withPin.body().get("childSafe").asBoolean()).isFalse();
        assertThat(withPin.body().get("categories").toString()).isEqualTo("[\"gambling\"]");

        // Changing the PIN needs the current one.
        assertThat(put("/profiles/pin", f.parent(), Map.of("pin", "1357")).status()).isEqualTo(403);
        ok(put("/profiles/pin", f.parent(), Map.of("pin", "1357", "currentPin", "2468")));
    }

    @Test
    void aChildSignsInWithThePasswordTheirParentChose() {
        String parent = parent();
        Resp weak = post("/profiles/children", parent, Map.of("email", "kid-weak@test.dev", "age", 9, "password", "short"));
        assertThat(weak.status()).isEqualTo(400);
        assertThat(weak.body().get("detail").asString()).contains("at least 15 characters");
        assertThat(get("/profiles/children", parent).body().get("children").size()).as("no child made").isZero();

        String email = "kid-" + java.util.UUID.randomUUID().toString().substring(0, 8) + "@test.dev";
        ok(post("/profiles/children", parent, Map.of("email", email, "age", 9, "password", PASSWORD)));
        Resp in = post("/auth/sign-in", null, Map.of("email", email, "password", PASSWORD));
        assertThat(in.status()).as(in.raw()).isEqualTo(200);
        assertThat(get("/profiles/me", in.body().get("token").asString()).body().get("managedByParent").asBoolean()).isTrue();
    }

    @Test
    void aParentResetsTheirChildsPasswordWithThePinAndTheChildsSignInsEnd() {
        String parent = parent();
        String email = "kid-" + java.util.UUID.randomUUID().toString().substring(0, 8) + "@test.dev";
        String childId = post("/profiles/children", parent, Map.of("email", email, "age", 10, "password", PASSWORD)).body().get("userId").asString();
        String child = post("/auth/sign-in", null, Map.of("email", email, "password", PASSWORD)).body().get("token").asString();
        ok(put("/profiles/pin", parent, Map.of("pin", "2468")));

        String path = "/profiles/children/" + childId + "/password";
        Map<String, Object> reset = Map.of("password", "violet harbour lantern");
        assertThat(put(path, parent, reset).status()).as("needs the PIN").isEqualTo(403);
        assertThat(call(HttpMethod.PUT, path, parent, reset, Map.of("X-Parent-Pin", "2468")).status()).isEqualTo(204);

        assertThat(get("/auth/me", child).status()).as("the child's old sign-in has ended").isEqualTo(401);
        assertThat(post("/auth/sign-in", null, Map.of("email", email, "password", PASSWORD)).status()).isEqualTo(401);
        assertThat(post("/auth/sign-in", null, Map.of("email", email, "password", "violet harbour lantern")).status()).isEqualTo(200);

        // Only their own parent can.
        assertThat(call(HttpMethod.PUT, path, parent(), reset, Map.of("X-Parent-Pin", "2468")).status()).isEqualTo(404);
    }

    @Test
    void fiveWrongPinsPauseThePinEvenForTheRightOne() {
        Family f = family(8, true);
        ok(put("/profiles/pin", f.parent(), Map.of("pin", "2468")));
        Map<String, Object> change = settings(8, true, List.of(), List.of(), "OFF", null);
        String path = "/profiles/" + f.childProfileId();
        for (int i = 0; i < 5; i++) {
            assertThat(call(HttpMethod.PUT, path, f.parent(), change, Map.of("X-Parent-Pin", "100" + i)).status()).isEqualTo(403);
        }
        Resp paused = call(HttpMethod.PUT, path, f.parent(), change, Map.of("X-Parent-Pin", "2468"));
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

    private String parent() {
        String email = "parent-" + java.util.UUID.randomUUID().toString().substring(0, 8) + "@test.dev";
        return post("/auth/sign-up", null, Map.of("email", email, "password", PASSWORD, "role", "PARENT")).body().get("token").asString();
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
