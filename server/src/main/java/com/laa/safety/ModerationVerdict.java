package com.laa.safety;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** SAFETY_CHECK "content" output: a verdict per item checked (a lesson block, a reply, a learner's message). */
public record ModerationVerdict(@NotNull @Size(max = 60) List<@NotNull @Valid Item> items) {

    public enum Verdict { PASS, BLOCK }

    public record Item(
            @NotBlank @Size(max = 40) String id,
            @NotNull Verdict verdict,
            /** Which categories it touches, if any. */
            @NotNull @Size(max = 8) List<@NotNull @Size(max = 60) String> categories,
            /** For a block, a few words on why; empty otherwise. */
            @NotNull @Size(max = 300) String reason) {}
}
