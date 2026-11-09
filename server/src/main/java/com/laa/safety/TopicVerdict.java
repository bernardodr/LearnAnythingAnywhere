package com.laa.safety;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * SAFETY_CHECK output for the topic gate (BRIEF section 8). Every field is required by the schema, so
 * strict structured-output modes accept it; fields that don't apply to a verdict come back empty.
 */
public record TopicVerdict(
        @NotNull Verdict verdict,
        /** ALLOW: a tidy title for the topic. REFRAME: the age-appropriate framing. DECLINE: empty. */
        @NotNull @Size(max = 120) String title,
        /** REFRAME and DECLINE: a short, kind note to the learner. ALLOW: empty. */
        @NotNull @Size(max = 600) String message,
        /** Which filter categories the topic touches, if any. */
        @NotNull @Size(max = 10) List<@NotNull @Size(max = 60) String> categories) {

    public enum Verdict { ALLOW, REFRAME, DECLINE }
}
