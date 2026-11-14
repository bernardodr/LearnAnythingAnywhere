package com.laa.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.laa.IntegrationTest;
import com.laa.lesson.LessonPlan;

/**
 * The call registry: every model call the app makes is one descriptor (task, prompt variant, what comes back). The
 * app refuses to start if a registered call doesn't fit; this checks the same rules, and that they catch a misfit.
 */
class CallsTest extends IntegrationTest {

    @Autowired
    CallsCheck check;

    @Test
    void everyRegisteredCallFits() {
        assertThat(check.problems(Calls.all())).isEmpty();
        assertThat(Calls.all()).extracting(Call::id).doesNotHaveDuplicates()
                .contains("MISSION_INTERVIEW", "MISSION_INTERVIEW.draft", "SAFETY_CHECK.topic", "SAFETY_CHECK.content",
                        "SAFETY_CHECK.leak", "GRADE", "TUTOR");
    }

    @Test
    void aCallThatDoesntFitIsCaught() {
        List<String> problems = check.problems(List.of(
                new TextCall(Task.GRADE, null, "Grading as text"),                          // GRADE isn't streamed
                new StructuredCall<>(Task.TUTOR, null, LessonPlan.class, "Tutor as JSON"),   // TUTOR has no structured output
                new StructuredCall<>(Task.SAFETY_CHECK, "nope", LessonPlan.class, "No prompt")));
        assertThat(problems).hasSize(3);
        assertThat(problems.get(0)).contains("GRADE", "streaming");
        assertThat(problems.get(1)).contains("TUTOR", "structured output");
        assertThat(problems.get(2)).contains("safety_check.nope.v1.md");
    }

    @Test
    void eachCallIsFoundByItsId() {
        assertThat(Calls.byId("SAFETY_CHECK.content")).contains(Calls.CONTENT_CHECK);
        assertThat(Calls.byId("GRADE")).contains(Calls.GRADE);
        assertThat(Calls.byId("SAFETY_CHECK.nope")).isEmpty();
        assertThat(Calls.forTask(Task.SAFETY_CHECK)).containsExactlyInAnyOrder(Calls.TOPIC_GATE, Calls.CONTENT_CHECK, Calls.LEAK_CHECK);
    }
}
