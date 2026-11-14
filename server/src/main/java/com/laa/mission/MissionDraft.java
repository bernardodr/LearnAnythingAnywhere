package com.laa.mission;

import java.util.List;
import java.util.function.UnaryOperator;

import com.laa.safety.ShownText;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The mission the interview ends with (BRIEF section 6.2): the four sections of /teach's MISSION.md,
 * plus a one-line headline for the shelf. The learner can edit it before confirming.
 */
public record MissionDraft(
        /** One line for the library shelf, in the learner's terms, e.g. "Climb a V3 with my friends by spring". */
        @NotBlank @Size(max = 90) String headline,
        /** 1–3 sentences: the concrete real-world outcome they're chasing. */
        @NotBlank @Size(max = 600) String why,
        @NotNull @Size(min = 1, max = 6) List<@NotBlank @Size(max = 200) String> successLooksLike,
        @NotNull @Size(max = 6) List<@NotBlank @Size(max = 200) String> constraints,
        @NotNull @Size(max = 6) List<@NotBlank @Size(max = 200) String> outOfScope) implements ShownText<MissionDraft> {

    /** All of it is shown: on the mission card, and in MISSION.md. */
    @Override
    public MissionDraft mapShown(UnaryOperator<String> f) {
        return new MissionDraft(f.apply(headline), f.apply(why), map(successLooksLike, f), map(constraints, f), map(outOfScope, f));
    }

    private static List<String> map(List<String> items, UnaryOperator<String> f) {
        return items == null ? null : items.stream().map(f).toList();
    }
}
