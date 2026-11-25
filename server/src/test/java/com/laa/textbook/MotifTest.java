package com.laa.textbook;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.laa.llm.structured.StructuredOutputs;
import com.laa.mission.MissionDraft;

import tools.jackson.databind.json.JsonMapper;

/**
 * Cover motifs: matching one from a book's title until the AI picks one, the server's set agreeing with the drawings
 * the app has, and the mission draft's schema offering exactly that set.
 */
class MotifTest {

    @ParameterizedTest
    @CsvSource({
            "Bouldering, MOUNTAIN",
            "The Night Sky, STARS",
            "Sourdough bread, WHEAT",
            "Python, DOTS",
            "Watercolour painting, BRUSH",
            "Spanish for travel, SPEECH",
            "Chess openings, PAWN",
            "A Little History of Art, SHAPES",
            "Roman history, COLUMN",
            "The World of Plants, LEAF",
            "Guitar for beginners, NOTE",
            "Personal finance, BARS",
            "Marathon training, PULSE",
            "Quantum physics, ATOM",
            "Knots, ORNAMENT",
    })
    void aTitleGetsAFittingMotif(String title, Motif expected) {
        assertThat(Motif.guess(title)).isEqualTo(expected);
    }

    @Test
    void shortWordsMatchOnlyWholeWords() {
        assertThat(Motif.guess("Artificial intelligence")).as("'art' isn't 'artificial'").isNotEqualTo(Motif.SHAPES);
        assertThat(Motif.guess("Career change")).as("'car' isn't 'career'").isNotEqualTo(Motif.GEAR);
        assertThat(Motif.guess("Start a podcast")).as("'art' isn't inside 'start'").isNotEqualTo(Motif.SHAPES);
        assertThat(Motif.guess("Art")).isEqualTo(Motif.SHAPES);
    }

    @Test
    void nothingToGoOnGivesTheOrnament() {
        assertThat(Motif.guess(null)).isEqualTo(Motif.ORNAMENT);
        assertThat(Motif.guess("   ")).isEqualTo(Motif.ORNAMENT);
    }

    @Test
    void keysRoundTripAndUnknownKeysAreNone() {
        for (Motif m : Motif.values()) assertThat(Motif.fromKey(m.key())).isEqualTo(m);
        assertThat(Motif.fromKey("unicorn")).isNull();
        assertThat(Motif.fromKey(null)).isNull();
    }

    /** The app draws every motif the server can hand out, and nothing the server doesn't know (Motif.tsx's MOTIFS). */
    @Test
    void theAppDrawsExactlyTheServersMotifs() throws IOException {
        String tsx = Files.readString(Path.of("../app/src/components/Motif.tsx"));
        var list = Pattern.compile("export const MOTIFS = \\[(.*?)\\] as const", Pattern.DOTALL).matcher(tsx);
        assertThat(list.find()).as("MOTIFS in Motif.tsx").isTrue();
        List<String> app = Pattern.compile("'([a-z]+)'").matcher(list.group(1)).results().map(r -> r.group(1)).toList();
        assertThat(app).containsExactlyElementsOf(Arrays.stream(Motif.values()).map(Motif::key).toList());
    }

    /** The model is offered the motifs as an enum, so a structured answer can only name one of them. */
    @Test
    void theDraftsSchemaOffersEveryMotif() {
        String schema = new StructuredOutputs(JsonMapper.builder().build(), null).forType(MissionDraft.class).json();
        for (Motif m : Motif.values()) assertThat(schema).contains("\"" + m.name() + "\"");
    }
}
