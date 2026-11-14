package com.laa.safety;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.laa.llm.Calls;
import com.laa.llm.GenerationProfile;
import com.laa.llm.LlmGateway;
import com.laa.llm.LlmUnavailableException;
import com.laa.llm.PromptContext;
import com.laa.llm.StructuredOutputException;
import com.laa.llm.runs.Origin;
import com.laa.security.SecretRedactor;

/**
 * The tutor's leak guard (BRIEF section 6.5): does a coaching message give away the hidden answer? Text similarity
 * first ({@link LeakSimilarity}), then the SAFETY_CHECK "leak" classifier. It fails closed: a message it can't check
 * counts as a leak, so the caller uses a pre-written hint.
 */
@Component
public class LeakCheck {

    private static final Logger log = LoggerFactory.getLogger(LeakCheck.class);

    private final LlmGateway gateway;

    LeakCheck(LlmGateway gateway) {
        this.gateway = gateway;
    }

    /**
     * Why {@code message} gives the answer away, if it does.
     *
     * @param hidden        what the learner hasn't found yet (missing rubric points, the answer outline)
     * @param learnerAnswer their answer, so saying their own words back isn't a leak
     * @param prompt        the practice question, for the classifier's context
     */
    public Optional<String> leaks(String message, List<String> hidden, String learnerAnswer, String prompt,
                                  UUID userId, UUID textbookId) {
        Optional<String> similar = LeakSimilarity.leaks(message, hidden, learnerAnswer);
        if (similar.isPresent()) return similar;
        Map<String, String> vars = new HashMap<>();
        vars.put("prompt", prompt);
        vars.put("hidden", "- " + String.join("\n- ", hidden));
        vars.put("learner_answer", learnerAnswer);
        PromptContext ctx = new PromptContext(vars, List.of(), "Tutor message to check:\n" + message, userId, textbookId,
                Origin.APP);
        try {
            LeakVerdict v = gateway.structured(Calls.LEAK_CHECK, ctx, GenerationProfile.ADULT_DEFAULT);
            return Boolean.TRUE.equals(v.leaks())
                    ? Optional.of(v.reason().isBlank() ? "the classifier flagged it" : v.reason()) : Optional.empty();
        } catch (LlmUnavailableException | StructuredOutputException e) {
            log.warn("Leak classifier unavailable, failing closed: {}", SecretRedactor.redact(e.getMessage()));
            return Optional.of("couldn’t be checked");
        }
    }
}
