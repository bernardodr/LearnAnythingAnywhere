package com.laa.tutor;

import java.util.List;
import java.util.function.UnaryOperator;

import com.laa.safety.ShownText;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** GRADE output (BRIEF section 6.5). Grading itself arrives in M4; the type is here so compare can check it. */
public record GradeResult(
        @NotNull Verdict verdict,
        @NotNull List<String> coveredPoints,
        @NotNull List<String> missingPoints,
        @NotNull CoachingMove coachingMove,
        @NotBlank String message) implements ShownText<GradeResult> {

    /** Only the coaching message is the model's words; covered points are shown from the rubric, checked with the lesson. */
    @Override
    public GradeResult mapShown(UnaryOperator<String> f) {
        return new GradeResult(verdict, coveredPoints, missingPoints, coachingMove, f.apply(message));
    }


    public enum Verdict { MET, PARTIAL, NOT_YET }

    public enum CoachingMove { CONFIRM, ASK, HINT }
}
