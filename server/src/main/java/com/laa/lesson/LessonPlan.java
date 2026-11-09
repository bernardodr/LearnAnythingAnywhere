package com.laa.lesson;

import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * LESSON_PLAN output (BRIEF section 6.4): one tightly scoped skill in the learner's zone of proximal
 * development, tied to the mission. Stored with the lesson (server-only) and handed to LESSON_WRITE.
 */
public record LessonPlan(
        @NotBlank @Size(max = 90) String title,
        /** The one skill this lesson builds, stated as something the learner will be able to do. */
        @NotBlank @Size(max = 240) String skill,
        /** "You'll be able to…": the tangible win, in the learner's terms. */
        @NotBlank @Size(max = 320) String win,
        /** Why this is the right next step for this learner and mission. */
        @NotBlank @Size(max = 500) String whyNow,
        /** Only the knowledge the skill needs, as short points. */
        @NotNull @Size(min = 1, max = 6) List<@NotBlank @Size(max = 300) String> knowledge,
        /** What the learner will practise, with immediate feedback. */
        @NotBlank @Size(max = 400) String practice,
        /** The earlier lesson the warm-up question comes from; 0 for none (lesson 1). */
        @NotNull @Min(0) Integer warmupFromLesson) {}
