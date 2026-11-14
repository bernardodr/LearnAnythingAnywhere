package com.laa.safety;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReadingAgeTest {

    private static final String CHILD = "The cat sat on the mat. It was a big red mat. The cat was happy. "
            + "It liked to sit in the sun. The sun was warm. The cat went to sleep.";
    private static final String ADULT = "Periodisation organises training into sequential phases, systematically "
            + "manipulating volume and intensity to optimise physiological adaptation while mitigating accumulated "
            + "fatigue, thereby facilitating supercompensation before competitive performance.";

    @Test
    void simpleTextReadsYoungerThanDenseTechnicalText() {
        Double child = ReadingAge.of(CHILD);
        Double adult = ReadingAge.of(ADULT + " " + ADULT);
        assertThat(child).isBetween(5.0, 9.0);
        assertThat(adult).isGreaterThan(18.0);
    }

    @Test
    void tooLittleTextHasNoScore() {
        assertThat(ReadingAge.of("Hello there.")).isNull();
        assertThat(ReadingAge.of(null)).isNull();
    }

    @Test
    void syllablesAreCountedRoughlyRight() {
        assertThat(ReadingAge.syllables("cat")).isEqualTo(1);
        assertThat(ReadingAge.syllables("climbing")).isEqualTo(2);
        assertThat(ReadingAge.syllables("make")).isEqualTo(1);
        assertThat(ReadingAge.syllables("periodisation")).isBetween(5, 6); // a heuristic: "io" counts as one vowel group
    }
}
