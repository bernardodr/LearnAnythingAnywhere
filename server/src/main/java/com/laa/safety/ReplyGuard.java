package com.laa.safety;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

import com.laa.llm.GenerationProfile;

import reactor.core.publisher.Flux;

/**
 * Delivers a streamed reply (interviewer, tutor) through the safety layer (BRIEF section 8):
 * <ul>
 *   <li>For a child's profile ({@link SafetyRules#strict}), nothing is shown until the whole reply has been
 *       checked; then it's sent in one piece.</li>
 *   <li>For everyone else it streams as it's written, and is checked as soon as it's complete. If the check fails,
 *       or the links policy changes the text, a {@code replace} swaps what was shown before anything is saved.</li>
 * </ul>
 * Either way, what's returned (and stored) is the checked text: links handled by the profile's policy, and a reply
 * that didn't pass replaced by {@link Moderation#PLACEHOLDER}, with its run logged as BLOCKED.
 */
@Component
public class ReplyGuard {

    /** Where the reply goes as it's delivered. */
    public interface Out {
        void token(String text);

        /** Replace everything shown so far with this. */
        void replace(String text);
    }

    private final Moderation moderation;

    ReplyGuard(Moderation moderation) {
        this.moderation = moderation;
    }

    /**
     * @param chunks   the reply as display text, in order
     * @param runId    filled in with the producing run's id once the stream completes
     * @param fallback what to send if the reply comes back empty
     */
    public String deliver(Flux<String> chunks, AtomicReference<UUID> runId, String fallback, GenerationProfile profile,
                          Set<String> allowedLinks, UUID userId, UUID textbookId, Out out) {
        boolean strict = SafetyRules.strict(profile);
        StringBuilder shown = new StringBuilder();
        chunks.doOnNext(c -> {
            shown.append(c);
            if (!strict) out.token(c);
        }).blockLast();

        String streamed = shown.toString();
        String text = LinkPolicy.apply(streamed.strip(), profile.linksPolicy(), allowedLinks).strip();
        if (text.isEmpty()) text = fallback;
        Moderation.Result check = moderation.checkText(text, profile, Moderation.Kind.OUTPUT, userId, textbookId);
        if (!check.passed()) {
            moderation.markBlocked(runId.get(), check.flags());
            text = Moderation.PLACEHOLDER;
        }
        if (strict) out.token(text);
        else if (!text.equals(streamed.strip())) out.replace(text);
        return text;
    }
}
