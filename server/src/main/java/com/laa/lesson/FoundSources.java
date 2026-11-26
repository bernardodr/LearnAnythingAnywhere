package com.laa.lesson;

import java.util.List;
import java.util.function.UnaryOperator;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.laa.safety.ShownText;

/**
 * What the research call returns for one lesson ({@code Calls.RESEARCH}, issue #82): the sources a model found by
 * searching the web, before LAA has checked them. {@link SourceFinder} keeps only those whose address really opens (and,
 * for a child-safe reader, only on the owner's list of educational sites), and adds them to the book's sources.
 *
 * <p>The title, site and note are shown to the learner, so they're {@link ShownText} and go through the safety check;
 * the address isn't text a learner reads, and the links policy decides whether it's shown.
 *
 * @param sources up to six; the prompt asks for two to four, and an empty list is a fine answer
 */
public record FoundSources(@NotNull @Size(max = 6) List<@Valid @NotNull Source> sources) implements ShownText<FoundSources> {

    /**
     * One source.
     *
     * @param title the page's own title, as the search result showed it
     * @param url   the exact https address from the search result
     * @param site  who publishes it, in a few words ("NASA", "Royal Museums Greenwich")
     * @param note  one sentence on what the learner will find there and when to read it
     */
    public record Source(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 500) String url,
            @NotBlank @Size(max = 120) String site,
            @NotBlank @Size(max = 300) String note) {}

    @Override
    public FoundSources mapShown(UnaryOperator<String> f) {
        return new FoundSources(sources.stream()
                .map(s -> new Source(f.apply(s.title()), s.url(), f.apply(s.site()), f.apply(s.note()))).toList());
    }
}
