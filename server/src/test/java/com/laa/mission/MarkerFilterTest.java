package com.laa.mission;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class MarkerFilterTest {

    private static final String M = "[[MISSION_READY]]";

    /** Feeds the chunks and returns everything the learner would see, in order. */
    private static String shown(MarkerFilter f, List<String> chunks) {
        StringBuilder out = new StringBuilder();
        for (String c : chunks) out.append(f.accept(c));
        out.append(f.finish());
        return out.toString();
    }

    @Test
    void aMarkerSplitAcrossChunksIsRemovedAndNeverPartlyShown() {
        MarkerFilter f = new MarkerFilter(M);
        StringBuilder seen = new StringBuilder();
        for (String c : List.of("Thanks, that's ", "all I need. [[MIS", "SION_RE", "ADY]]")) {
            String out = f.accept(c);
            assertThat(out).doesNotContain("[[");
            seen.append(out);
        }
        seen.append(f.finish());
        assertThat(seen.toString()).isEqualTo("Thanks, that's all I need.");
        assertThat(f.markerSeen()).isTrue();
        assertThat(f.text()).isEqualTo(seen.toString()); // what streamed is exactly what's stored
    }

    @Test
    void spacesBetweenWordsStillStreamPromptly() {
        MarkerFilter f = new MarkerFilter(M);
        assertThat(f.accept("What ")).isEqualTo("What");
        assertThat(f.accept("would ")).isEqualTo(" would");
        assertThat(f.finish()).isEmpty();
    }

    @Test
    void textThatOnlyLooksLikeTheStartOfAMarkerIsReleased() {
        MarkerFilter f = new MarkerFilter(M);
        assertThat(shown(f, List.of("Use [[brackets]] or [[MIS", "TAKES"))).isEqualTo("Use [[brackets]] or [[MISTAKES");
        assertThat(f.markerSeen()).isFalse();

        MarkerFilter atEnd = new MarkerFilter(M);
        assertThat(shown(atEnd, List.of("Ends with [["))).isEqualTo("Ends with [[");
        assertThat(atEnd.markerSeen()).isFalse();
    }

    @Test
    void everyMarkerIsRemovedWhereverItAppears() {
        MarkerFilter f = new MarkerFilter(M);
        assertThat(shown(f, List.of(M + "Hi " + M, "there" + M))).isEqualTo("Hi there");
        assertThat(f.markerSeen()).isTrue();
    }

    @Test
    void aReplyWithNoMarkerPassesThroughUnchanged() {
        MarkerFilter f = new MarkerFilter(M);
        assertThat(shown(f, List.of("What ", "would ", "success ", "look like?"))).isEqualTo("What would success look like?");
        assertThat(f.markerSeen()).isFalse();
    }
}
