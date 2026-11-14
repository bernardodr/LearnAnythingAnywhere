package com.laa.safety;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.laa.llm.GenerationProfile;
import com.laa.llm.LlmGateway;
import com.laa.llm.LlmResult;
import com.laa.llm.LlmStream;
import com.laa.llm.PromptContext;
import com.laa.llm.StructuredCall;
import com.laa.llm.TextCall;
import com.laa.profile.Profiles;
import com.laa.web.ApiException;

/**
 * The safety layer (BRIEF section 8): the one way a learner flow reaches a model, enforced by {@code ArchitectureTest},
 * and the one place that checks both sides of the exchange against the learner's reader settings.
 * <ul>
 *   <li>{@link #admit}: what the learner typed, before any work starts.</li>
 *   <li>{@link #reply}: a reply they'll read (the preface interviewer, the tutor chat). It's written in full and
 *       checked before any of it is sent.</li>
 *   <li>{@link #write}: a structured result with text they'll see (the mission draft, the tutor's note, learning
 *       notes and glossary terms).</li>
 * </ul>
 * What comes back is always safe to show: links follow the reader's view ({@link ReaderView}: their policy, and their
 * own sources as the allowlist), and text that didn't pass is replaced by {@link Moderation#PLACEHOLDER}, with its run
 * logged as BLOCKED.
 * If the check itself can't run, nothing comes back: {@link Moderation.Unavailable} (503, "we couldn't run the safety
 * check"). The learner and textbook come from the {@link PromptContext}; the layer looks up their reader settings.
 */
@Service
public class SafetyLayer {

    /**
     * A structured result after the safety layer. {@code value} is always safe to show; {@code blocked} means its
     * shown text was held back (and replaced by the placeholder), so the caller can fall back to something better.
     */
    public record Checked<T>(T value, boolean blocked, UUID runId) {}

    private final LlmGateway gateway;
    private final Moderation moderation;
    private final Profiles profiles;
    private final ReaderView view;

    SafetyLayer(LlmGateway gateway, Moderation moderation, Profiles profiles, ReaderView view) {
        this.gateway = gateway;
        this.moderation = moderation;
        this.profiles = profiles;
        this.view = view;
    }

    /**
     * Checks what a learner typed. Call it before starting any work (and before opening a stream), so a refusal is an
     * ordinary error response and nothing is saved.
     *
     * @throws ApiException 400 with {@link Moderation#INPUT_REFUSED} if the message doesn't fit the reader settings
     */
    public void admit(UUID userId, UUID textbookId, String typed) {
        if (typed == null || typed.isBlank()) return;
        GenerationProfile profile = profiles.forUser(userId);
        if (!moderation.checkText(typed, profile, Moderation.Kind.INPUT, userId, textbookId).passed()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, Moderation.INPUT_REFUSED);
        }
    }

    /** A reply the learner will read, checked before any of it is sent. See {@link #reply(TextCall, PromptContext, String, UnaryOperator)}. */
    public String reply(TextCall call, PromptContext ctx, String ifEmpty) {
        return reply(call, ctx, ifEmpty, UnaryOperator.identity());
    }

    /**
     * A reply the learner will read: written in full, then the links policy and the safety check, and only then
     * returned for sending and storing.
     *
     * @param ifEmpty what to say if the model's reply is empty
     * @param prepare turns the model's raw text into the text to show (the interviewer strips its end marker here)
     * @throws com.laa.llm.LlmUnavailableException if no provider could write the reply
     */
    public String reply(TextCall call, PromptContext ctx, String ifEmpty, UnaryOperator<String> prepare) {
        GenerationProfile profile = profiles.forUser(ctx.userId());
        LlmStream.Whole whole = gateway.stream(call, ctx, profile).whole();
        String text = view.of(ctx.userId()).apply(prepare.apply(whole.text()).strip()).strip();
        if (text.isEmpty()) text = ifEmpty;
        return passes(text, profile, ctx, whole.runId()) ? text : Moderation.PLACEHOLDER;
    }

    /**
     * A structured result with text the learner will see: the links policy on every shown string, then the safety
     * check on all of it together.
     *
     * @throws com.laa.llm.LlmUnavailableException      if no provider could write it
     * @throws com.laa.llm.StructuredOutputException if it came back in the wrong shape twice
     */
    public <T extends ShownText<T>> Checked<T> write(StructuredCall<T> call, PromptContext ctx) {
        GenerationProfile profile = profiles.forUser(ctx.userId());
        LlmResult<T> result = gateway.structuredResult(call, ctx, profile);
        ReaderView.Reader reader = view.of(ctx.userId());
        T linked = result.value().mapShown(reader::apply);
        List<String> shown = new ArrayList<>();
        linked.mapShown(s -> {
            if (s != null) shown.add(s);
            return s;
        });
        if (passes(String.join("\n", shown), profile, ctx, result.runId())) return new Checked<>(linked, false, result.runId());
        return new Checked<>(linked.mapShown(s -> Moderation.PLACEHOLDER), true, result.runId());
    }

    /** The safety check on text about to be shown; a fail is logged against its run. */
    private boolean passes(String text, GenerationProfile profile, PromptContext ctx, UUID runId) {
        Moderation.Result check = moderation.checkText(text, profile, Moderation.Kind.OUTPUT, ctx.userId(), ctx.textbookId());
        if (check.passed()) return true;
        moderation.markBlocked(runId, check.flags());
        return false;
    }
}
