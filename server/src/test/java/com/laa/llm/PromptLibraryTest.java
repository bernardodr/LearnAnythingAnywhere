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
    void variantsRenderTheirOwnFileWithVariablesAndWithoutAuthoringComments() {
        String draft = prompts.render(Task.MISSION_INTERVIEW, "draft", "v1", Map.of("title", "Bouldering"));
        assertThat(draft).contains("**Bouldering**").contains("successLooksLike").doesNotContain("<!--");

        String main = prompts.render(Task.MISSION_INTERVIEW, null, "v1",
                Map.of("title", "Bouldering", "marker", "[[MISSION_READY]]", "max_answers", "5"));
        assertThat(main).contains("the marker [[MISSION_READY]] exactly as written").doesNotContain("{{");

        assertThat(prompts.render(Task.SAFETY_CHECK, "topic", "v1", Map.of())).contains("topic gate");
    }

    @Test
    void anUndeclaredVariantIsRejected() {
        assertThatThrownBy(() -> prompts.render(Task.TUTOR, "draft", "v1", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
