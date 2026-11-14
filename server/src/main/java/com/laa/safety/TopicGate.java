package com.laa.safety;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.laa.llm.Calls;
import com.laa.llm.GenerationProfile;
import com.laa.llm.LlmGateway;
import com.laa.llm.LlmResult;
import com.laa.llm.LlmUnavailableException;
import com.laa.llm.PromptContext;
import com.laa.llm.StructuredOutputException;
import com.laa.llm.runs.Origin;
import com.laa.safety.TopicVerdict.Verdict;
import com.laa.security.SecretRedactor;

/**
 * The topic gate (BRIEF section 8): every new textbook's topic goes through SAFETY_CHECK before anything is
 * created. It fails closed: if no provider can classify the topic, the textbook isn't created.
 */
@Component
public class TopicGate {

    private static final Logger log = LoggerFactory.getLogger(TopicGate.class);
    private static final String DEFAULT_DECLINE =
            "This isn’t a topic we can write a textbook about. Is there something else you’re curious about?";
    private static final String DEFAULT_REFRAME = "We’ll approach this topic in a way that suits you.";

    /** What the gate decided. {@code title} is what the textbook will be called (empty on DECLINE). */
    public record Decision(Verdict verdict, String title, String message, List<String> categories, UUID runId) {
        public boolean allowsCreation() {
            return verdict != Verdict.DECLINE;
        }

        public Map<String, Object> asJson() {
            return Map.of("verdict", verdict.name(), "title", title, "message", message,
                    "categories", categories, "runId", runId == null ? "" : runId.toString());
        }
    }

    /** The gate couldn't reach a verdict; the caller must not create the textbook. */
    public static class UnavailableException extends RuntimeException {
        public UnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final LlmGateway gateway;

    public TopicGate(LlmGateway gateway) {
        this.gateway = gateway;
    }

    public Decision check(String topic, UUID userId, GenerationProfile profile) {
        PromptContext ctx = new PromptContext(Map.of(), List.of(), "Topic: " + topic, userId, null, Origin.APP);
        LlmResult<TopicVerdict> result;
        try {
            result = gateway.structuredResult(Calls.TOPIC_GATE, ctx, profile);
        } catch (LlmUnavailableException | StructuredOutputException e) {
            log.warn("Topic gate unavailable, refusing to create: {}", SecretRedactor.redact(e.getMessage()));
            throw new UnavailableException("We couldn’t check this topic just now, so nothing was created. Please try again.", e);
        }
        TopicVerdict v = result.value();
        String title = clean(v.title());
        String message = clean(v.message());
        Decision model = switch (v.verdict()) {
            case ALLOW -> new Decision(Verdict.ALLOW, title.isEmpty() ? tidy(topic) : title, "", v.categories(), result.runId());
            // A reframe without a framing can't be honoured safely; treat it as a decline.
            case REFRAME -> title.isEmpty()
                    ? new Decision(Verdict.DECLINE, "", message.isEmpty() ? DEFAULT_DECLINE : message, v.categories(), result.runId())
                    : new Decision(Verdict.REFRAME, title, message.isEmpty() ? DEFAULT_REFRAME : message, v.categories(), result.runId());
            case DECLINE -> new Decision(Verdict.DECLINE, "", message.isEmpty() ? DEFAULT_DECLINE : message, v.categories(), result.runId());
        };
        return withRules(model, topic, profile);
    }

    /**
     * LAA's own layer over the classifier's verdict ({@link SafetyRules}): the stricter wins, so the gate holds
     * whichever AI service is active. A topic that's always declined is declined; one the profile leaves out is
     * declined unless the classifier's reframe steers clear of it.
     */
    static Decision withRules(Decision model, String topic, GenerationProfile profile) {
        if (model.verdict() == Verdict.DECLINE) return model;
        var always = SafetyRules.alwaysDeclined(topic);
        if (always.isPresent()) {
            return new Decision(Verdict.DECLINE, "", DEFAULT_DECLINE, List.of(always.get()), model.runId());
        }
        List<String> leftOut = SafetyRules.leftOut(model.verdict() == Verdict.REFRAME ? model.title() : topic, profile);
        if (!leftOut.isEmpty()) {
            return new Decision(Verdict.DECLINE, "", leftOutMessage(leftOut), leftOut, model.runId());
        }
        return model;
    }

    private static String leftOutMessage(List<String> leftOut) {
        return "That topic touches on " + String.join(" and ", leftOut) + ", which your reader settings leave out. "
                + "Is there something else you’d like to learn? Animals, space, how things work and making things are all great places to start.";
    }

    private static String clean(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").strip();
    }

    /** "bouldering " → "Bouldering". */
    static String tidy(String topic) {
        String t = clean(topic);
        return t.isEmpty() ? t : Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }
}
