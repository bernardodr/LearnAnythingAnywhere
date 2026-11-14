package com.laa.tutor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The text-similarity half of the leak guard (BRIEF section 6.5): does a coaching message restate the hidden
 * answer? Only what the learner hasn't already written counts as hidden, since echoing their own words back
 * to them gives nothing away. A message leaks a secret (a missing rubric point, or the answer outline) if it
 * <ul>
 *   <li>repeats any run of {@value #SHINGLE} words from it that isn't in the learner's answer, or</li>
 *   <li>contains at least {@value #HIDDEN_SHARE} of its hidden content words (when it has at least
 *       {@value #MIN_HIDDEN} of them).</li>
 * </ul>
 * Deliberately simple and conservative; the classifier is the other half.
 */
public final class LeakSimilarity {

    static final int SHINGLE = 5;
    static final double HIDDEN_SHARE = 0.6;
    static final int MIN_HIDDEN = 3;

    private static final Set<String> STOP = Set.of(
            "a", "an", "the", "and", "or", "but", "if", "then", "so", "of", "to", "in", "on", "at", "by", "for", "with",
            "from", "into", "onto", "over", "under", "is", "are", "was", "were", "be", "been", "being", "it", "its",
            "this", "that", "these", "those", "you", "your", "yours", "they", "them", "their", "we", "our", "i", "me",
            "my", "he", "she", "his", "her", "as", "than", "can", "could", "will", "would", "should", "do", "does",
            "did", "not", "no", "yes", "more", "most", "less", "very", "just", "also", "about", "what", "which",
            "when", "where", "why", "how", "who", "there", "here", "all", "any", "some", "each", "one", "two");

    private LeakSimilarity() {}

    /** The reason the message leaks one of the secrets, or empty if it doesn't. */
    public static Optional<String> leaks(String message, List<String> secrets, String learnerAnswer) {
        List<String> msgWords = words(message);
        Set<String> msgSet = new HashSet<>(msgWords);
        Set<String> answerWords = new HashSet<>(words(learnerAnswer));
        Set<String> msgShingles = shingles(msgWords);
        Set<String> answerShingles = shingles(words(learnerAnswer));

        for (String secret : secrets) {
            List<String> secretWords = words(secret);
            for (String s : shingles(secretWords)) {
                if (msgShingles.contains(s) && !answerShingles.contains(s)) {
                    return Optional.of("repeats \"" + s + "\" from the hidden answer");
                }
            }
            Set<String> hidden = new LinkedHashSet<>();
            for (String w : secretWords) {
                if (w.length() > 2 && !STOP.contains(w) && !answerWords.contains(w)) hidden.add(w);
            }
            if (hidden.size() >= MIN_HIDDEN) {
                List<String> shown = new ArrayList<>();
                for (String w : hidden) if (msgSet.contains(w)) shown.add(w);
                if ((double) shown.size() / hidden.size() >= HIDDEN_SHARE) {
                    return Optional.of("uses most of the hidden answer's key words (" + String.join(", ", shown) + ")");
                }
            }
        }
        return Optional.empty();
    }

    static List<String> words(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        for (String w : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (!w.isEmpty()) out.add(stem(w));
        }
        return out;
    }

    /** A light plural/verb-ending fold so "holds" matches "hold" and "pushing" matches "push". */
    private static String stem(String w) {
        if (w.length() > 5 && w.endsWith("ing")) return w.substring(0, w.length() - 3);
        if (w.length() > 4 && w.endsWith("es")) return w.substring(0, w.length() - 2);
        if (w.length() > 3 && w.endsWith("s") && !w.endsWith("ss")) return w.substring(0, w.length() - 1);
        return w;
    }

    private static Set<String> shingles(List<String> words) {
        Set<String> out = new HashSet<>();
        for (int i = 0; i + SHINGLE <= words.size(); i++) out.add(String.join(" ", words.subList(i, i + SHINGLE)));
        return out;
    }
}
