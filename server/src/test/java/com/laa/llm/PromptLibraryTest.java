package com.laa.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.laa.llm.prompt.PromptLibrary;

class PromptLibraryTest {

    private final PromptLibrary prompts = new PromptLibrary();

    @Test
    void everyTaskShipsV1WithAllItsVariants() {
        for (Task task : Task.values()) {
            assertThat(prompts.exists(task, "v1")).as(task.name()).isTrue();
        }
        assertThat(prompts.exists(Task.MISSION_INTERVIEW, "v9")).isFalse();
    }

    @Test
    void aNewRouteStartsOnTheNewestCompleteVersion() {
        // Lesson writing has v2 (it can place diagrams) and the preface v2 (its draft picks the cover motif);
        // everything else is still on v1.
        assertThat(prompts.latest(Task.LESSON_WRITE)).isEqualTo("v2");
        assertThat(prompts.latest(Task.MISSION_INTERVIEW)).isEqualTo("v2");
        assertThat(prompts.latest(Task.DIAGRAM)).isEqualTo("v1");
        assertThat(prompts.latest(Task.SAFETY_CHECK)).isEqualTo("v1");
        assertThat(prompts.render(Calls.LESSON_WRITE, "v2", java.util.Map.of())).contains("DIAGRAM", "brief", "caption");
        assertThat(prompts.render(Calls.LESSON_WRITE, "v1", java.util.Map.of())).doesNotContain("DIAGRAM");
    }

    @Test
    void thePrefaceDraftV2AsksForAMotifFromEveryOneInTheSet() {
        String draft = prompts.render(Calls.MISSION_DRAFT, "v2", java.util.Map.of("title", "Bouldering"));
        for (com.laa.textbook.Motif m : com.laa.textbook.Motif.values()) assertThat(draft).contains(m.name());
        assertThat(prompts.render(Calls.MISSION_DRAFT, "v1", java.util.Map.of("title", "Bouldering"))).doesNotContain("motif");
        // The interview itself is unchanged: v2 only exists so the task's two prompts share a version.
        String v1 = prompts.render(Calls.INTERVIEW_TURN, "v1", java.util.Map.of("title", "Bouldering"));
        assertThat(prompts.render(Calls.INTERVIEW_TURN, "v2", java.util.Map.of("title", "Bouldering"))).isEqualTo(v1);
    }

    @Test
    void theDiagramPromptStatesTheFramesLimitsAndTheLook() {
        String prompt = prompts.render(Calls.DIAGRAM, "v1", java.util.Map.of("title", "Bouldering"));
        assertThat(prompt).contains("Bouldering", "No internet", "--accent", "laa-button", "44px", "textVersion", "whole")
                .doesNotContain("<!--");
    }

    @Test
    void variantsRenderTheirOwnFileWithVariablesAndWithoutAuthoringComments() {
        String draft = prompts.render(Calls.MISSION_DRAFT, "v1", Map.of("title", "Bouldering"));
        assertThat(draft).contains("**Bouldering**").contains("successLooksLike").doesNotContain("<!--");

        String main = prompts.render(Calls.INTERVIEW_TURN, "v1",
                Map.of("title", "Bouldering", "marker", "[[MISSION_READY]]", "max_answers", "5"));
        assertThat(main).contains("the marker [[MISSION_READY]] exactly as written").doesNotContain("{{");

        assertThat(prompts.render(Calls.TOPIC_GATE, "v1", Map.of())).contains("topic gate");
    }

    @Test
    void aCallWithNoPromptFileNamesTheMissingFile() {
        assertThatThrownBy(() -> prompts.render(new TextCall(Task.TUTOR, "draft", "Not registered"), "v1", Map.of()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("tutor.draft.v1.md");
    }
}
