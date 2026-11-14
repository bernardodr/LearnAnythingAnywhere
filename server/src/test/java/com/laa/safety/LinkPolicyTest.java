package com.laa.safety;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.laa.llm.GenerationProfile.LinksPolicy;

/** The links policy, enforced on the server (BRIEF section 8). */
class LinkPolicyTest {

    private static final String TEXT = "Read [the guide](https://example.com/guide) or see https://other.example.org/page. Then practise.";
    private static final Set<String> MINE = Set.of("https://example.com/guide/");

    @Test
    void offKeepsLinkTextAndDropsEveryAddress() {
        assertThat(LinkPolicy.apply(TEXT, LinksPolicy.OFF, MINE)).isEqualTo("Read the guide or see. Then practise.");
        assertThat(LinkPolicy.apply("Visit www.example.com today", LinksPolicy.OFF, Set.of())).isEqualTo("Visit today");
    }

    @Test
    void allowlistKeepsOnlyTheLearnersOwnSources() {
        assertThat(LinkPolicy.apply(TEXT, LinksPolicy.ALLOWLIST, MINE))
                .isEqualTo("Read [the guide](https://example.com/guide) or see. Then practise.");
        assertThat(LinkPolicy.apply("Bare: https://example.com/guide", LinksPolicy.ALLOWLIST, MINE))
                .isEqualTo("Bare: https://example.com/guide");
    }

    @Test
    void openChangesNothing() {
        assertThat(LinkPolicy.apply(TEXT, LinksPolicy.OPEN, Set.of())).isEqualTo(TEXT);
    }

    @Test
    void textWithoutLinksIsUntouchedIncludingIndentation() {
        String md = "- Step one\n    - a nested point\n\n    code  with  spaces";
        assertThat(LinkPolicy.apply(md, LinksPolicy.OFF, Set.of())).isEqualTo(md);
    }

    @Test
    void storedLessonJsonKeepsItsIdentifiers() {
        Map<String, Object> block = Map.of("id", "b1", "type", "text", "md", "See https://x.example.com for more",
                "citations", List.of("r1"));
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) LinkPolicy.applyAll(block, LinksPolicy.OFF, Set.of());
        assertThat(out).containsEntry("md", "See for more").containsEntry("id", "b1").containsEntry("citations", List.of("r1"));
    }
}
