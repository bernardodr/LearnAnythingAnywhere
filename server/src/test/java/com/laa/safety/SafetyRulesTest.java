package com.laa.safety;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.laa.llm.GenerationProfile;
import com.laa.llm.GenerationProfile.LinksPolicy;

/** LAA's own deterministic safety layer (BRIEF section 8). */
class SafetyRulesTest {

    private static final GenerationProfile ADULT = GenerationProfile.ADULT_DEFAULT;
    private static final GenerationProfile CHILD = new GenerationProfile(9, true, Set.of(), LinksPolicy.OFF, null, null);

    @Test
    void harmfulInstructionsAreDeclinedForEveryoneButMentionsAreNot() {
        assertThat(SafetyRules.alwaysDeclined("how to make a pipe bomb")).hasValue("instructions for weapons or explosives");
        assertThat(SafetyRules.alwaysDeclined("Cooking meth at home")).hasValue("instructions for making drugs");
        assertThat(SafetyRules.alwaysDeclined("best way to hurt myself")).hasValue("self-harm");
        assertThat(SafetyRules.alwaysDeclined("hacking into my friend's instagram")).hasValue("breaking into other people's accounts");
        assertThat(SafetyRules.alwaysDeclined("making a fake ID")).hasValue("forging documents or money");

        assertThat(SafetyRules.alwaysDeclined("the history of bomb disposal")).isEmpty();
        assertThat(SafetyRules.alwaysDeclined("how vaccines are made")).isEmpty();
        assertThat(SafetyRules.alwaysDeclined("how to make bread")).isEmpty();
    }

    @Test
    void childSafeLeavesOutEveryCategoryAndAnAdultNone() {
        assertThat(SafetyRules.leftOut("The best poker strategy", CHILD)).containsExactly("gambling");
        assertThat(SafetyRules.leftOut("Horror films with the most gore", CHILD)).containsExactly("scary content");
        assertThat(SafetyRules.leftOut("How rifles work", CHILD)).containsExactly("violence and weapons");
        assertThat(SafetyRules.leftOut("How rifles work", ADULT)).isEmpty();
        assertThat(SafetyRules.leftOut("Wine tasting for beginners", ADULT)).isEmpty();

        var filtered = new GenerationProfile(null, false, Set.of("gambling", "spiders"), LinksPolicy.OFF, null, null);
        assertThat(SafetyRules.leftOut("Poker odds and spiders", filtered)).containsExactly("gambling", "spiders");
        assertThat(SafetyRules.leftOut("How rifles work", filtered)).isEmpty();
        assertThat(SafetyRules.extras(filtered)).containsExactly("spiders");
    }

    @Test
    void ordinaryChildrensLessonsAreNotCaughtByLookalikeWords() {
        for (String innocent : new String[] {
                "Carbon dating tells us how old a fossil is.",
                "You can see Venus with the naked eye.",
                "Make a bath bomb with baking soda.",
                "Ask an adult to help with the glue gun.",
                "Root beer floats are a fizzy treat.",
                "Weeding the garden helps the carrots grow.",
                "Smoke rises because warm air is lighter.",
                "It can feel scary to speak in front of the class, and that's normal.",
                "You can bet the tortoise will win in the end."}) {
            assertThat(SafetyRules.leftOut(innocent, CHILD)).as(innocent).isEmpty();
            assertThat(SafetyRules.alwaysDeclined(innocent)).as(innocent).isEmpty();
        }
    }
}
