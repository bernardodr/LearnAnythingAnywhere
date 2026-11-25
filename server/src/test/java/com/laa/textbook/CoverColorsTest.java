package com.laa.textbook;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * The cover cloths are listed twice: here on the server ({@link Textbook#COVER_COLORS}, which gives each new book the
 * cloth of its volume) and in the app's design tokens ({@code colors.covers} in {@code app/src/theme.ts}, which the
 * new-book screen uses to preview the cover a book is about to get). This test keeps the two lists the same, in the same
 * order, so the preview always matches the book that's made. A plain unit test: it only reads a file.
 */
class CoverColorsTest {

    @Test
    void theAppsCoverColoursAreTheServersInTheSameOrder() throws IOException {
        String theme = Files.readString(Path.of("../app/src/theme.ts"));
        var covers = Pattern.compile("covers: \\{(.*?)\\}", Pattern.DOTALL).matcher(theme);
        assertThat(covers.find()).as("colors.covers in theme.ts").isTrue();
        List<String> app = Pattern.compile("'(#[0-9A-Fa-f]{6})'").matcher(covers.group(1)).results()
                .map(r -> r.group(1).toUpperCase()).toList();
        assertThat(app).containsExactlyElementsOf(Textbook.COVER_COLORS);
    }
}
