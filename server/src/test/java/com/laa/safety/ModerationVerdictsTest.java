package com.laa.safety;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.laa.IntegrationTest;
import com.laa.llm.Calls;
import com.laa.llm.GenerationProfile;

/** The classifier's answer is read strictly: a block for an item can't be overruled by a second verdict for it. */
class ModerationVerdictsTest extends IntegrationTest {

    @Autowired
    Moderation moderation;

    @Test
    void aRepeatedIdCantOverruleABlock() {
        fakes.setCanned(Calls.CONTENT_CHECK, """
                {"items": [
                  {"id": "1", "verdict": "BLOCK", "categories": ["scary_content"], "reason": "too scary"},
                  {"id": "1", "verdict": "PASS", "categories": [], "reason": ""}
                ]}""");
        Moderation.Result r = moderation.checkText("A story", GenerationProfile.ADULT_DEFAULT, Moderation.Kind.OUTPUT, null, null);
        assertThat(r.passed()).isFalse();
        assertThat(r.reasons()).containsEntry("text", "too scary");
    }

    @Test
    void anAnswerForAnotherItemDoesntCount() {
        fakes.setCanned(Calls.CONTENT_CHECK, """
                {"items": [{"id": "2", "verdict": "PASS", "categories": [], "reason": ""}]}""");
        Moderation.Result r = moderation.checkText("A story", GenerationProfile.ADULT_DEFAULT, Moderation.Kind.OUTPUT, null, null);
        assertThat(r.reasons()).containsEntry("text", "not checked");
    }
}
