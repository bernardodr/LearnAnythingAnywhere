package com.laa.tutor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.laa.llm.GenerationProfile;
import com.laa.llm.GenerationProfile.LinksPolicy;

/** The reveal-answer rule (BRIEF section 6.5): only when the reader settings allow it, after that many tries. */
class RevealAnswerTest {

    @Test
    void answersAreRevealedOnlyWhenTheProfileAllowsIt() {
        assertThat(Practice.mayRevealAnswer(GenerationProfile.ADULT_DEFAULT, 99)).isFalse();
        var afterThree = new GenerationProfile(12, true, Set.of(), LinksPolicy.OFF, 3, null);
        assertThat(Practice.mayRevealAnswer(afterThree, 2)).isFalse();
        assertThat(Practice.mayRevealAnswer(afterThree, 3)).isTrue();
    }
}
