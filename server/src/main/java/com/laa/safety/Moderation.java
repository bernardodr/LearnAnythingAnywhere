package com.laa.safety;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.laa.llm.Calls;
import com.laa.llm.GenerationProfile;
import com.laa.llm.LlmGateway;
import com.laa.llm.LlmUnavailableException;
import com.laa.llm.PromptContext;
import com.laa.llm.StructuredOutputException;
import com.laa.llm.runs.ModelRunRepository;
import com.laa.llm.runs.Origin;
import com.laa.security.SecretRedactor;
import com.laa.web.ApiException;

/**
 * Moderation of everything generated and everything a learner types (BRIEF section 8), against the learner's
 * profile. Two layers, and the stricter wins: {@link SafetyRules} (LAA's own, deterministic) and the SAFETY_CHECK
 * "content" classifier. It fails closed: if the classifier can't run, {@link #check} throws {@link Unavailable}, so
 * nothing unchecked is saved or sent (callers let it surface as "try again").
 *
 * <p>Callers decide what a block means (regenerate once, then a placeholder; or refuse a learner's message) and use
 * {@link #markBlocked} to log the generation that was held back as {@code BLOCKED} in the run log.
 */
@Service
public class Moderation {

    /** What a learner sees instead of something that didn't pass. */
    public static final String PLACEHOLDER = "This part was left out because it didn’t fit your reader settings.";
    public static final String INPUT_REFUSED =
            "Let’s keep this about learning, in a way that fits your reader settings. Try putting it another way.";
    private static final Logger log = LoggerFactory.getLogger(Moderation.class);

    public enum Kind { OUTPUT, INPUT }

    /**
     * The check couldn't run, so nothing passes. An {@link ApiException} on purpose, not an AI outage: every surface
     * shows its message as it is (a 503 response, a stream's {@code error} event, a lesson job's error), so the
     * learner always learns that it was the safety check, as the guide promises.
     */
    public static class Unavailable extends ApiException {
        Unavailable() {
            super(HttpStatus.SERVICE_UNAVAILABLE,
                    "We couldn’t run the safety check just now, so nothing was saved or sent. Please try again.");
        }
    }

    public record Item(String id, String text) {}

    /** Which items were held back, and why: item id to reason, in the order checked (empty when all passed). */
    public record Result(Map<String, String> reasons) {
        public Result {
            reasons = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(reasons));
        }

        public boolean passed() {
            return reasons.isEmpty();
        }

        public boolean blocks(String id) {
            return reasons.containsKey(id);
        }

        /** {@code id: reason}, one per item held back, for the run log. */
        public List<String> flags() {
            return reasons.entrySet().stream().map(e -> e.getKey() + ": " + e.getValue()).toList();
        }
    }

    private final LlmGateway gateway;
    private final ModelRunRepository runs;

    Moderation(LlmGateway gateway, ModelRunRepository runs) {
        this.gateway = gateway;
        this.runs = runs;
    }

    public Result check(List<Item> items, GenerationProfile profile, Kind kind, UUID userId, UUID textbookId) {
        return check(items, profile, kind, userId, textbookId, Origin.APP);
    }

    /** As {@link #check(List, GenerationProfile, Kind, UUID, UUID)}, logging the classifier's run under {@code origin}. */
    public Result check(List<Item> items, GenerationProfile profile, Kind kind, UUID userId, UUID textbookId, Origin origin) {
        List<Item> present = items.stream().filter(i -> i.text() != null && !i.text().isBlank()).toList();
        Map<String, String> flags = new LinkedHashMap<>();
        for (Item i : present) {
            SafetyRules.alwaysDeclined(i.text()).ifPresent(r -> flags.put(i.id(), r));
            List<String> left = SafetyRules.leftOut(i.text(), profile);
            if (!left.isEmpty()) flags.putIfAbsent(i.id(), "left out by the reader settings: " + String.join(", ", left));
        }
        if (present.isEmpty()) return new Result(Map.of());

        Map<String, String> vars = new HashMap<>();
        vars.put("kind", kind == Kind.INPUT ? "messages a learner typed" : "text written for the learner");
        PromptContext ctx = new PromptContext(vars, List.of(), itemsBlock(present), userId, textbookId, origin);
        try {
            ModerationVerdict v = gateway.structured(Calls.CONTENT_CHECK, ctx, profile);
            // Every verdict for an item counts, and a BLOCK beats a PASS, so a repeated id can't overrule a block.
            Map<String, List<ModerationVerdict.Item>> byId = new HashMap<>();
            for (ModerationVerdict.Item item : v.items()) byId.computeIfAbsent(item.id(), k -> new java.util.ArrayList<>()).add(item);
            for (int n = 0; n < present.size(); n++) {
                Item i = present.get(n);
                List<ModerationVerdict.Item> verdicts = byId.getOrDefault(promptId(n), List.of());
                // An item the classifier didn't answer for is unchecked, so it doesn't pass.
                if (verdicts.isEmpty()) flags.putIfAbsent(i.id(), "not checked");
                for (ModerationVerdict.Item verdict : verdicts) {
                    if (verdict.verdict() != ModerationVerdict.Verdict.BLOCK) continue;
                    String why = verdict.reason().isBlank() ? String.join(", ", verdict.categories()) : verdict.reason();
                    flags.putIfAbsent(i.id(), why.isBlank() ? "flagged" : why);
                }
            }
        } catch (LlmUnavailableException | StructuredOutputException e) {
            log.warn("Moderation unavailable, failing closed: {}", SecretRedactor.redact(e.getMessage()));
            throw new Unavailable();
        }
        return new Result(flags);
    }

    /**
     * The items as the classifier sees them. Items are data: each is numbered by position (so an id can't carry
     * markup), and {@code &}, {@code <} and {@code >} in its text are escaped, so no spelling of a tag, in any case,
     * can end an item early or forge another.
     */
    static String itemsBlock(List<Item> present) {
        StringBuilder list = new StringBuilder();
        for (int n = 0; n < present.size(); n++) {
            String text = present.get(n).text().strip().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            list.append("<item id=\"").append(promptId(n)).append("\">\n").append(text).append("\n</item>\n\n");
        }
        return list.toString().strip();
    }

    private static String promptId(int position) {
        return String.valueOf(position + 1);
    }

    /** One piece of text, e.g. a reply or a learner's message. */
    public Result checkText(String text, GenerationProfile profile, Kind kind, UUID userId, UUID textbookId) {
        return check(List.of(new Item("text", text)), profile, kind, userId, textbookId);
    }

    /** Logs the run whose output was held back as BLOCKED, with the flags. */
    public void markBlocked(UUID runId, List<String> flags) {
        if (runId != null) runs.markBlocked(runId, flags);
    }
}
