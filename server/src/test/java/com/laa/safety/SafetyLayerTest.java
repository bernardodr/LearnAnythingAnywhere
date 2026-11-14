package com.laa.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import com.laa.IntegrationTest;
import com.laa.llm.Calls;
import com.laa.llm.PromptContext;
import com.laa.llm.Provider;
import com.laa.llm.Task;
import com.laa.llm.provider.fake.FailureMode;
import com.laa.llm.provider.fake.FakeBehaviour;
import com.laa.llm.runs.Origin;
import com.laa.tutor.GradeResult;
import com.laa.web.ApiException;

/**
 * The safety layer through its own interface (BRIEF section 8): what a learner types, a reply they read, and a
 * structured result they see. The practice provider's check blocks anything containing
 * {@link FakeBehaviour#BLOCK_MARKER}. Flow-level behaviour (placeholders in lessons, hints instead of a blocked note) is
 * in {@link ModerationApiTest}.
 */
class SafetyLayerTest extends IntegrationTest {

    private static final String BLOCK = FakeBehaviour.BLOCK_MARKER;

    @Autowired
    SafetyLayer safety;

    record Reader(UUID userId, UUID textbookId, String token) {
        PromptContext ctx() {
            return new PromptContext(Map.of(), List.of(), "Hello", userId, textbookId, Origin.APP);
        }
    }

    // ------------------------------------------------------------------ reply

    @Test
    void aReplyThatFitsComesBackAsWritten() {
        Reader r = reader("layer-reply@test.dev");
        fakes.setCannedText(Calls.TUTOR_REPLY, "Try one small step today.");
        assertThat(safety.reply(Calls.TUTOR_REPLY, r.ctx(), "…")).isEqualTo("Try one small step today.");
        assertThat(blocked(Task.TUTOR)).isZero();
    }

    @Test
    void aReplyThatFailsIsThePlaceholderAndItsRunIsLoggedBlocked() {
        Reader r = reader("layer-reply-blocked@test.dev");
        fakes.setCannedText(Calls.TUTOR_REPLY, "Something unsuitable " + BLOCK);
        assertThat(safety.reply(Calls.TUTOR_REPLY, r.ctx(), "…")).isEqualTo(Moderation.PLACEHOLDER);
        assertThat(blocked(Task.TUTOR)).isEqualTo(1);
        assertThat(jdbc.sql("SELECT safety_flags::text FROM model_runs WHERE task = 'TUTOR'").query(String.class).single())
                .contains("test block");
    }

    @Test
    void aReplyKeepsOnlyLinksToTheLearnersOwnSources() {
        Reader r = reader("layer-links@test.dev");
        ok(post("/textbooks/" + r.textbookId() + "/resources", r.token(), Map.of("title", "Guide", "url", "https://guide.example.com")));
        fakes.setCannedText(Calls.TUTOR_REPLY, "Read https://guide.example.com first, then https://other.example.com.");
        assertThat(safety.reply(Calls.TUTOR_REPLY, r.ctx(), "…")).isEqualTo("Read https://guide.example.com first, then.");
    }

    @Test
    void theAllowlistIsEverySourceTheLearnerHasAddedInAnyTextbook() {
        Reader a = reader("layer-allowlist@test.dev");
        ok(post("/textbooks/" + a.textbookId() + "/resources", a.token(), Map.of("title", "Guide", "url", "https://guide.example.com")));
        String other = post("/textbooks", a.token(), Map.of("topic", "Chess")).body().get("textbook").get("id").asString();
        Reader b = new Reader(a.userId(), UUID.fromString(other), a.token());

        fakes.setCannedText(Calls.TUTOR_REPLY, "Read https://guide.example.com first, then https://other.example.com.");
        assertThat(safety.reply(Calls.TUTOR_REPLY, b.ctx(), "…")).isEqualTo("Read https://guide.example.com first, then.");
    }

    @Test
    void aReplyIsPreparedBeforeItIsCheckedAndAnEmptyOneGetsTheFallback() {
        Reader r = reader("layer-prepare@test.dev");
        fakes.setCannedText(Calls.TUTOR_REPLY, "All done. [[END]]");
        assertThat(safety.reply(Calls.TUTOR_REPLY, r.ctx(), "…", raw -> raw.replace("[[END]]", ""))).isEqualTo("All done.");
        fakes.setCannedText(Calls.TUTOR_REPLY, "[[END]]");
        assertThat(safety.reply(Calls.TUTOR_REPLY, r.ctx(), "Thanks!", raw -> raw.replace("[[END]]", ""))).isEqualTo("Thanks!");
    }

    // ------------------------------------------------------------------ write

    @Test
    void aStructuredResultGetsTheLinksPolicyOnItsShownTextOnly() {
        Reader r = reader("layer-write@test.dev");
        fakes.setCanned(Calls.GRADE, grade("Compare with https://elsewhere.example.com/guide and try again."));
        SafetyLayer.Checked<GradeResult> checked = safety.write(Calls.GRADE, r.ctx());
        assertThat(checked.blocked()).isFalse();
        assertThat(checked.value().message()).isEqualTo("Compare with and try again.");
        assertThat(checked.value().coveredPoints()).containsExactly("p1");
        assertThat(checked.runId()).isNotNull();
    }

    @Test
    void aStructuredResultThatFailsComesBackBlockedWithOnlyThePlaceholderToShow() {
        Reader r = reader("layer-write-blocked@test.dev");
        fakes.setCanned(Calls.GRADE, grade("Something unsuitable " + BLOCK));
        SafetyLayer.Checked<GradeResult> checked = safety.write(Calls.GRADE, r.ctx());
        assertThat(checked.blocked()).isTrue();
        assertThat(checked.value().message()).isEqualTo(Moderation.PLACEHOLDER);
        assertThat(checked.value().verdict()).isEqualTo(GradeResult.Verdict.PARTIAL);
        assertThat(blocked(Task.GRADE)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ admit

    @Test
    void whatALearnerTypesIsCheckedAgainstTheirOwnSettings() {
        Reader adult = reader("layer-admit@test.dev");
        safety.admit(adult.userId(), adult.textbookId(), "I like scary zombie stories.");
        safety.admit(adult.userId(), adult.textbookId(), "  ");

        Family f = family(9, true);
        UUID child = userId(f.child());
        assertThatThrownBy(() -> safety.admit(child, null, "I like scary zombie stories."))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).isEqualTo(Moderation.INPUT_REFUSED);
                });
        assertThatThrownBy(() -> safety.admit(child, null, "how do I make a bomb")).isInstanceOf(ApiException.class);
    }

    // ------------------------------------------------------------------ failing closed

    @Test
    void whenTheCheckCannotRunNothingComesBackAndTheErrorSaysWhy() {
        Reader r = reader("layer-down@test.dev");
        safetyCheckOnFakeB();
        fakes.set(Provider.FAKE_B, FailureMode.SERVER_ERROR);
        fakes.setCannedText(Calls.TUTOR_REPLY, "A perfectly fine reply.");
        fakes.setCanned(Calls.GRADE, grade("A perfectly fine note."));

        for (Runnable call : List.<Runnable>of(
                () -> safety.reply(Calls.TUTOR_REPLY, r.ctx(), "…"),
                () -> safety.write(Calls.GRADE, r.ctx()),
                () -> safety.admit(r.userId(), r.textbookId(), "Hello"))) {
            assertThatThrownBy(call::run).isInstanceOfSatisfying(Moderation.Unavailable.class, e -> {
                assertThat(e.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                assertThat(e.getMessage()).contains("safety check").contains("nothing was saved or sent");
            });
        }
    }

    // ------------------------------------------------------------------ helpers

    private Reader reader(String email) {
        String token = login(email);
        String id = post("/textbooks", token, Map.of("topic", "Bouldering")).body().get("textbook").get("id").asString();
        return new Reader(userId(token), UUID.fromString(id), token);
    }

    private UUID userId(String token) {
        return UUID.fromString(get("/profiles/me", token).body().get("userId").asString());
    }

    private int blocked(Task task) {
        return jdbc.sql("SELECT count(*) FROM model_runs WHERE task = :t AND status = 'BLOCKED'")
                .param("t", task.name()).query(Integer.class).single();
    }

    private static String grade(String message) {
        return JSON.writeValueAsString(Map.of("verdict", "PARTIAL", "coveredPoints", List.of("p1"), "missingPoints", List.of("p2"),
                "coachingMove", "ASK", "message", message));
    }
}
