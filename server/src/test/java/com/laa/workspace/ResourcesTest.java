package com.laa.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** RESOURCES.md in /teach's RESOURCES-FORMAT.md. */
class ResourcesTest {

    private static final String TEACH_EXAMPLE = """
            # System Design Interview Resources

            ## Knowledge

            - [Hello Interview: Delivery Framework](https://www.hellointerview.com/learn/system-design/in-a-hurry/delivery)
              Written by ex-Meta/Amazon interviewers. Use for: interview structure.
            - [MDN: HTTP response status codes](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Status)
              Authoritative definitions. Use for: 301 vs 302.
            - Book: Designing Data-Intensive Applications, by Martin Kleppmann
              The standard text. Use for: storage and replication.

            ## Wisdom (Communities)

            - [r/ExperiencedDevs](https://www.reddit.com/r/ExperiencedDevs/)
              Use for: interview experience reports.

            ## Gaps
            - No vetted peer-mock community found yet.
            """;

    @Test
    void knowledgeEntriesAreNumberedAndCommunitiesAreNot() {
        var list = Resources.parse(TEACH_EXAMPLE);
        assertThat(list).extracting(Resources.Resource::id).containsExactly("r1", "r2", "r3");
        assertThat(list.get(1).title()).isEqualTo("MDN: HTTP response status codes");
        assertThat(list.get(1).url()).isEqualTo("https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Status");
        assertThat(list.get(1).note()).isEqualTo("Authoritative definitions. Use for: 301 vs 302.");
        assertThat(list.get(2).url()).isNull();
        assertThat(Resources.parse(null)).isEmpty();
    }

    @Test
    void appendingCreatesTheFileInTeachFormat() {
        assertThat(Resources.append(null, "Bouldering", "Lattice Training: Footwork", "https://example.org/footwork", "Use for: footwork drills."))
                .isEqualTo("""
                        # Bouldering Resources

                        ## Knowledge

                        - [Lattice Training: Footwork](https://example.org/footwork)
                          Use for: footwork drills.
                        """);
    }

    @Test
    void appendingAddsToTheEndOfKnowledgeBeforeOtherSections() {
        String updated = Resources.append(TEACH_EXAMPLE, "x", "New [book]", "https://example.org/a b)", "  A   note ");
        var list = Resources.parse(updated);
        assertThat(list).extracting(Resources.Resource::id).containsExactly("r1", "r2", "r3", "r4");
        assertThat(list.get(3).title()).isEqualTo("New (book)");
        assertThat(list.get(3).url()).isEqualTo("https://example.org/a%20b%29");
        assertThat(list.get(3).note()).isEqualTo("A note");
        assertThat(updated).contains("  A note\n\n## Wisdom (Communities)");

        String withoutKnowledge = Resources.append("# Chess Resources\n\n## Wisdom (Communities)\n\n- [r/chess](https://reddit.com/r/chess)\n",
                "Chess", "Lichess practice", "https://lichess.org/practice", "");
        assertThat(withoutKnowledge).startsWith("# Chess Resources\n\n## Knowledge\n\n- [Lichess practice](https://lichess.org/practice)\n\n## Wisdom");
        assertThat(Resources.parse(withoutKnowledge)).hasSize(1);
    }

    @Test
    void onlyWebAddressesAreAccepted() {
        assertThat(Resources.isWebUrl("https://example.org/x")).isTrue();
        assertThat(Resources.isWebUrl("http://example.org")).isTrue();
        assertThat(Resources.isWebUrl("javascript:alert(1)")).isFalse();
        assertThat(Resources.isWebUrl("example.org")).isFalse();
        assertThat(Resources.isWebUrl("https://")).isFalse();
        assertThat(Resources.isWebUrl("not a url")).isFalse();
    }
}
