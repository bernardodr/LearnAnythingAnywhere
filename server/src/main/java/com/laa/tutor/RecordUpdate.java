package com.laa.tutor;

import java.util.List;
import java.util.function.UnaryOperator;

import com.laa.safety.ShownText;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * RECORD_UPDATE output (BRIEF section 6.5): after the learner meets a practice question, a learning record
 * (/teach LEARNING-RECORD-FORMAT.md) and glossary terms they used correctly (GLOSSARY-FORMAT.md). Covering
 * material is not evidence; only what the answer demonstrates is.
 */
public record RecordUpdate(
        /** False when the answer shows nothing non-trivial beyond the existing records. */
        @NotNull Boolean writeRecord,
        /** dash-case, a few words: becomes learning-records/000N-{slug}.md. */
        @NotNull @Size(max = 60) String slug,
        @NotNull @Size(max = 120) String title,
        /** 1–3 sentences: what is now known, and why it matters for what to teach next. */
        @NotNull @Size(max = 800) String body,
        /** How the learner showed it, in one sentence. */
        @NotNull @Size(max = 300) String evidence,
        @NotNull @Size(max = 5) List<@NotNull @Valid Term> glossary) implements ShownText<RecordUpdate> {

    /** Shown at the back of the book (What you've learned, the glossary). The slug is only a file name. */
    @Override
    public RecordUpdate mapShown(UnaryOperator<String> f) {
        return new RecordUpdate(writeRecord, slug, f.apply(title), f.apply(body), f.apply(evidence),
                glossary == null ? null : glossary.stream()
                        .map(t -> new Term(f.apply(t.term()), f.apply(t.definition()), t.avoid().stream().map(f).toList()))
                        .toList());
    }


    public record Term(
            @NotBlank @Size(max = 60) String term,
            /** One or two sentences: what the term is. */
            @NotBlank @Size(max = 300) String definition,
            /** Words to avoid in favour of this term. */
            @NotNull @Size(max = 4) List<@NotBlank @Size(max = 40) String> avoid) {}
}
