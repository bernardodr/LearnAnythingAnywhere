package com.laa.safety;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.laa.llm.GenerationProfile.LinksPolicy;

/**
 * The links policy, enforced on the server (BRIEF section 8), not just asked for in the prompt:
 * <ul>
 *   <li>{@code OFF}: every link goes. A Markdown link keeps its text; a bare web address is removed.</li>
 *   <li>{@code ALLOWLIST}: only links to the learner's own sources (RESOURCES.md) stay.</li>
 *   <li>{@code OPEN}: links stay.</li>
 * </ul>
 */
public final class LinkPolicy {

    private static final Pattern MD_LINK = Pattern.compile("\\[([^\\]]*)]\\(\\s*<?(https?://[^)\\s>]+)>?\\s*\\)");
    /** A web address, without the sentence punctuation that may follow it. */
    private static final Pattern BARE = Pattern.compile("(?i)\\b(https?://|www\\.)[^\\s)\\]>\"']*[^\\s)\\]>\"'.,;:!?]");

    private LinkPolicy() {}

    public static String apply(String text, LinksPolicy policy, Set<String> allowed) {
        if (text == null || policy == LinksPolicy.OPEN) return text;
        Set<String> ok = policy == LinksPolicy.ALLOWLIST ? normalised(allowed) : Set.of();
        Matcher m = MD_LINK.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String keep = ok.contains(norm(m.group(2))) ? m.group() : m.group(1);
            m.appendReplacement(out, Matcher.quoteReplacement(keep));
        }
        m.appendTail(out);
        // Bare addresses: keep the allowlisted Markdown links kept above, and drop every other address together with
        // the space before it (so "see https://x." reads "see."). Nothing else in the text is touched.
        String kept = out.toString();
        Matcher b = BARE.matcher(kept);
        StringBuilder result = new StringBuilder();
        while (b.find()) {
            String url = b.group();
            boolean insideKeptLink = b.start() >= 2 && kept.charAt(b.start() - 1) == '(' && kept.charAt(b.start() - 2) == ']';
            if (insideKeptLink || ok.contains(norm(url))) {
                b.appendReplacement(result, Matcher.quoteReplacement(url));
                continue;
            }
            b.appendReplacement(result, "");
            boolean gapAfter = b.end() >= kept.length() || " \t\n.,;:!?".indexOf(kept.charAt(b.end())) >= 0;
            int last = result.length() - 1;
            if (gapAfter && last >= 0 && (result.charAt(last) == ' ' || result.charAt(last) == '\t')) result.setLength(last);
        }
        b.appendTail(result);
        return result.toString();
    }

    /**
     * The policy over stored JSON (lesson blocks): every string value, at any depth, except identifiers. Used when a
     * lesson is shown, so a policy changed after the lesson was written still holds.
     */
    @SuppressWarnings("unchecked")
    public static Object applyAll(Object node, LinksPolicy policy, Set<String> allowed) {
        if (policy == LinksPolicy.OPEN) return node;
        if (node instanceof String text) return apply(text, policy, allowed);
        if (node instanceof java.util.List<?> list) return list.stream().map(v -> applyAll(v, policy, allowed)).toList();
        if (node instanceof java.util.Map<?, ?> map) {
            java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
            ((java.util.Map<String, Object>) map).forEach((k, v) ->
                    out.put(k, IDENTIFIERS.contains(k) ? v : applyAll(v, policy, allowed)));
            return out;
        }
        return node;
    }

    private static final Set<String> IDENTIFIERS = Set.of("id", "type", "kind", "variant", "resource", "citations");

    private static Set<String> normalised(Set<String> urls) {
        return urls == null ? Set.of() : urls.stream().map(LinkPolicy::norm).collect(java.util.stream.Collectors.toSet());
    }

    private static String norm(String url) {
        return url.strip().replaceAll("/+$", "").toLowerCase(java.util.Locale.ROOT);
    }
}
