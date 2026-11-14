package com.laa.safety;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** SAFETY_CHECK "leak" output: does a tutor's coaching message give away the hidden answer? */
public record LeakVerdict(
        @NotNull Boolean leaks,
        /** What it gives away, in a few words (empty when it doesn't). */
        @NotNull @Size(max = 300) String reason) {}
