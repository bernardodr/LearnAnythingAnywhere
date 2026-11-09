package com.laa.mission;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/** MISSION.md must match the template in /teach's MISSION-FORMAT.md exactly. */
class MissionMarkdownTest {

    @Test
    void rendersTheTeachTemplateExactly() {
        var draft = new MissionDraft("Run a half marathon by October",
                "You want to run a half marathon with your sister in October.",
                List.of("Run 21 km without stopping", "Follow a 12-week plan"),
                List.of("Four runs a week, mornings only"),
                List.of("Trail running", "Nutrition plans"));

        assertThat(MissionMarkdown.render("Running", draft)).isEqualTo("""
                # Mission: Running

                ## Why
                You want to run a half marathon with your sister in October.

                ## Success looks like
                - Run 21 km without stopping
                - Follow a 12-week plan

                ## Constraints
                - Four runs a week, mornings only

                ## Out of scope
                - Trail running
                - Nutrition plans
                """);
    }

    @Test
    void emptyListsStillGetABullet() {
        var draft = new MissionDraft("h", "Why.", List.of("Do it"), List.of(), List.of());
        String md = MissionMarkdown.render("Chess", draft);
        assertThat(md).contains("## Constraints\n- " + MissionMarkdown.NONE + "\n\n## Out of scope\n- " + MissionMarkdown.NONE + "\n");
    }

    @Test
    void whatTheLearnerTypedCantBreakTheStructure() {
        var draft = new MissionDraft("h", "Line one\n\n## Fake heading\nline two",
                List.of("- already a bullet", "# heading?", "  spaced   out  ", "\n"), List.of("> quoted"), List.of());
        String md = MissionMarkdown.render("Topic\n# Injected", draft);

        assertThat(md).startsWith("# Mission: Topic # Injected\n\n## Why\nLine one ## Fake heading line two\n\n");
        assertThat(md).contains("- already a bullet\n- heading?\n- spaced out\n\n## Constraints\n- quoted\n");
        // Exactly the five headings of the template, no more.
        assertThat(md.lines().filter(l -> l.startsWith("#"))).containsExactly(
                "# Mission: Topic # Injected", "## Why", "## Success looks like", "## Constraints", "## Out of scope");
    }
}
