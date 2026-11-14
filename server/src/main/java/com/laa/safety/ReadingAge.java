package com.laa.safety;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import tools.jackson.databind.JsonNode;

/**
 * Estimated reading age of a text (BRIEF section 5.4, compare's automatic checks): the Flesch–Kincaid grade level
 * plus five, the usual mapping from a US school grade to an age. A rough guide, not a judgement: good enough to
 * see that one model writes for 9-year-olds and another for undergraduates.
 */
public final class ReadingAge {

    /** Shorter texts give meaningless scores. */
    static final int MIN_WORDS = 20;

    private ReadingAge() {}

    /** The estimated reading age, rounded to one decimal, or null if there's too little text to judge. */
    public static Double of(String text) {
        if (text == null) return null;
        String clean = text.replaceAll("```[\\s\\S]*?```", " ").replaceAll("[#*_`>\\[\\]()|-]", " ");
        List<String> words = new ArrayList<>();
        for (String w : clean.split("\\s+")) {
            String letters = w.replaceAll("[^\\p{L}']", "");
            if (!letters.isEmpty()) words.add(letters);
        }
        if (words.size() < MIN_WORDS) return null;
        int sentences = Math.max(1, clean.split("[.!?]+(\\s|$)").length);
        int syllables = 0;
        for (String w : words) syllables += syllables(w);
        double grade = 0.39 * ((double) words.size() / sentences) + 11.8 * ((double) syllables / words.size()) - 15.59;
        double age = Math.max(5, grade + 5);
        return Math.round(age * 10) / 10.0;
    }

    /** All the prose in a structured output: every string value with at least one space in it. */
    public static Double ofJson(JsonNode node) {
        if (node == null) return null;
        StringBuilder text = new StringBuilder();
        collect(node, text);
        return of(text.toString());
    }

    private static void collect(JsonNode node, StringBuilder out) {
        if (node.isString()) {
            String s = node.asString().strip();
            if (s.contains(" ")) out.append(s).append(s.matches(".*[.!?]$") ? " " : ". ");
        } else if (node.isArray() || node.isObject()) {
            for (JsonNode child : node) collect(child, out);
        }
    }

    /** Vowel groups, ignoring a silent final "e"; at least one. */
    static int syllables(String word) {
        String w = word.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        if (w.isEmpty()) return 1;
        if (w.length() > 2 && w.endsWith("e") && !w.endsWith("le")) w = w.substring(0, w.length() - 1);
        int count = 0;
        boolean inVowel = false;
        for (char c : w.toCharArray()) {
            boolean vowel = "aeiouy".indexOf(c) >= 0;
            if (vowel && !inVowel) count++;
            inVowel = vowel;
        }
        return Math.max(1, count);
    }
}
