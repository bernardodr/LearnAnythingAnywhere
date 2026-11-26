package com.laa.safety;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The em dash filter's rules (issue #80): what each kind of dash becomes, and what's left alone. */
class DashesTest {

    @Test
    void aDashBetweenWordsBecomesAComma() {
        assertThat(Dashes.replace("Small steps — they add up.")).isEqualTo("Small steps, they add up.");
        assertThat(Dashes.replace("Small steps—they add up.")).isEqualTo("Small steps, they add up.");
        assertThat(Dashes.replace("Pick one ― then try it")).isEqualTo("Pick one, then try it");
        assertThat(Dashes.replace("a two-em ⸺ dash")).isEqualTo("a two-em, dash");
        assertThat(Dashes.replace("an en dash – used the same way")).isEqualTo("an en dash, used the same way");
    }

    @Test
    void theCommasItLeavesAreTidied() {
        assertThat(Dashes.replace("Wait for it —")).isEqualTo("Wait for it");
        assertThat(Dashes.replace("A pause —.")).isEqualTo("A pause.");
        assertThat(Dashes.replace("(— an aside)")).isEqualTo("(an aside)");
        assertThat(Dashes.replace("this — — that")).isEqualTo("this, that");
        assertThat(Dashes.replace("first line —\nsecond line")).isEqualTo("first line\nsecond line");
    }

    @Test
    void rangesListMarkersAndBlanksKeepTheirMeaning() {
        assertThat(Dashes.replace("from 1990—2000")).isEqualTo("from 1990–2000");
        assertThat(Dashes.replace("— Pick the step\n— Try it once")).isEqualTo("- Pick the step\n- Try it once");
        assertThat(Dashes.replace("| Name | — |")).isEqualTo("| Name | - |");
        assertThat(Dashes.replace("—")).isEqualTo("-");
    }

    @Test
    void escapesForADashAreCaughtToo() {
        assertThat(Dashes.replace("<p>Look up&mdash;then down</p>")).isEqualTo("<p>Look up, then down</p>");
        assertThat(Dashes.replace("<p>A&#8212;B</p>")).isEqualTo("<p>A, B</p>");
        assertThat(Dashes.replace("const s = 'Pick \\u2014 then try';")).isEqualTo("const s = 'Pick, then try';");
    }

    @Test
    void everythingElseIsLeftAlone() {
        String plain = "Ranges like 5–10, well-known words, i-- in a loop, and &amp; stay as they are.";
        assertThat(Dashes.replace(plain)).isSameAs(plain);
        String noDash = "No dashes here at all.";
        assertThat(Dashes.replace(noDash)).isSameAs(noDash);
        assertThat(Dashes.replace(null)).isNull();
    }
}
