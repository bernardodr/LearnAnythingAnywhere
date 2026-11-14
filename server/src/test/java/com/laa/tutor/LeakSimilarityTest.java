package com.laa.tutor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.laa.llm.GenerationProfile;
import com.laa.llm.GenerationProfile.LinksPolicy;

/** The text-similarity half of the leak guard, and the reveal-answer rule (BRIEF section 6.5). */
class LeakSimilarityTest {

    private static final List<String> SECRETS = List.of(
            "Keeping your hips close to the wall moves your weight over your feet",
            "Straight arms let your skeleton hold you so your forearms rest");
    private static final String PARTIAL_ANSWER = "I would keep my arms straight while I climb.";

    @Test
    void restatingAHiddenPointIsALeak() {
        assertThat(LeakSimilarity.leaks("Nice! Also remember: keeping your hips close to the wall moves weight onto your feet.",
                SECRETS, PARTIAL_ANSWER)).hasValueSatisfying(r -> assertThat(r).contains("repeats"));
    }

    @Test
    void paraphrasingMostOfTheHiddenKeyWordsIsALeak() {
        assertThat(LeakSimilarity.leaks("Think about it: hips near the wall, and your weight goes onto the feet.",
                SECRETS, PARTIAL_ANSWER)).hasValueSatisfying(r -> assertThat(r).contains("key words"));
    }

    @Test
    void echoingTheLearnersOwnWordsIsNotALeak() {
        // "straight arms" is in the learner's answer; saying it back gives nothing away.
        assertThat(LeakSimilarity.leaks("Yes, keeping your arms straight is exactly right.", SECRETS, PARTIAL_ANSWER)).isEmpty();
    }

    @Test
    void aSocraticQuestionIsNotALeak() {
        assertThat(LeakSimilarity.leaks("Good start. Where is the rest of your body while your arms hang straight?",
                SECRETS, PARTIAL_ANSWER)).isEmpty();
        assertThat(LeakSimilarity.leaks("What changes when you move closer?", SECRETS, "")).isEmpty();
    }

    @Test
    void answersAreRevealedOnlyWhenTheProfileAllowsIt() {
        assertThat(Practice.mayRevealAnswer(GenerationProfile.ADULT_DEFAULT, 99)).isFalse();
        var afterThree = new GenerationProfile(12, true, Set.of(), LinksPolicy.OFF, 3, null);
        assertThat(Practice.mayRevealAnswer(afterThree, 2)).isFalse();
        assertThat(Practice.mayRevealAnswer(afterThree, 3)).isTrue();
    }
}
