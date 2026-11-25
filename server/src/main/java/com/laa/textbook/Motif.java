package com.laa.textbook;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A book cover's subject motif (`docs/DESIGN.md`, "Books"): one of a fixed, hand-drawn set the app draws
 * (`app/src/components/Motif.tsx`, which lists the same names in lower case; {@code MotifTest} checks they match).
 *
 * <p>The AI picks one as part of the preface's mission draft ({@code MissionDraft.motif}), and it's saved with the
 * book when the learner confirms. Until then (a preface still in progress, or a book from before motifs existed),
 * {@link #guess} matches words in the title and topic, so every cover has a motif. {@link #ORNAMENT} is the neutral
 * one.
 *
 * <p>Adding a motif means three edits together: this enum (with words for {@link #guess}), the drawing in
 * {@code Motif.tsx}, and the list in the mission draft prompt.
 */
public enum Motif {
    STARS, MOON, LEAF, WHEAT, MOUNTAIN, WAVE, GEAR, DOTS, SHAPES, BRUSH, NOTE, FLASK, ATOM, GLOBE, QUILL, COLUMN,
    BARS, PULSE, PAWN, SPEECH, ORNAMENT;

    /**
     * Words that suggest each motif, checked in this order (the first match wins), against the title and topic in
     * lower case. A word matches the start of a word ("astronom" matches "astronomy"); one ending in a space must be
     * the whole word ("art " matches "art" but not "artificial").
     */
    private static final List<Map.Entry<Motif, List<String>>> WORDS = List.of(
            Map.entry(STARS, List.of("star ", "stars", "night sky", "astronom", "constellation", "galax", "cosmo", "telescope", "planet")),
            Map.entry(MOON, List.of("moon", "lunar", "sleep", "dream", "night")),
            Map.entry(WHEAT, List.of("bread", "sourdough", "bak", "cook", "recipe", "kitchen", "food", "farm", "wheat", "brew", "coffee")),
            Map.entry(LEAF, List.of("plant", "garden", "botan", "tree", "leaf", "flower", "ecolog", "biolog", "nature", "forest", "herb")),
            Map.entry(MOUNTAIN, List.of("boulder", "climb", "mountain", "hik", "geograph", "geolog", "ski ", "trek", "outdoor", "camp")),
            Map.entry(WAVE, List.of("ocean", "sea ", "surf", "swim", "sail", "water ", "river", "wave", "physics of sound")),
            Map.entry(GEAR, List.of("engineer", "mechanic", "machine", "car ", "bike", "bicycle", "repair", "robot", "electronic", "3d print", "woodwork")),
            Map.entry(DOTS, List.of("python", "javascript", "program", "coding", "code ", "software", "computer", "data", "algorithm", "web ", "machine learning", "ai ")),
            Map.entry(BRUSH, List.of("paint", "watercolo", "drawing", "draw", "sketch", "illustrat", "calligraph")),
            Map.entry(SHAPES, List.of("art ", "design", "architect", "photograph", "craft", "pottery", "knit", "sew", "fashion")),
            Map.entry(NOTE, List.of("music", "guitar", "piano", "sing ", "song", "violin", "drum", "compos", "jazz", "choir")),
            Map.entry(FLASK, List.of("chemi", "lab ", "experiment", "brewing science")),
            Map.entry(ATOM, List.of("physic", "quantum", "atom", "particle", "nuclear", "relativity", "science")),
            Map.entry(SPEECH, List.of("spanish", "french", "german", "italian", "japanese", "chinese", "mandarin", "language", "speak", "conversation", "grammar", "public speaking")),
            Map.entry(GLOBE, List.of("travel", "world", "countr", "culture", "geopolit", "map ")),
            Map.entry(QUILL, List.of("writ", "poet", "poem", "novel", "literat", "story", "essay", "journal", "books ", "reading ")),
            Map.entry(COLUMN, List.of("histor", "ancient", "roman", "greek", "philosoph", "law ", "politic", "civilisation", "civilization")),
            Map.entry(BARS, List.of("money", "financ", "invest", "econom", "business", "budget", "stock", "market", "account", "statistic", "math", "algebra", "calcul")),
            Map.entry(PULSE, List.of("health", "fitness", "run ", "marathon", "medic", "anatomy", "nutrition", "yoga", "exercise", "strength", "first aid", "nurs")),
            Map.entry(PAWN, List.of("chess", "game", "strategy", "poker", "go ", "puzzle", "bridge")));

    /** The name the app and the database use: lower case, e.g. "stars". */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * The motif named by a stored key, or null for none (or a name this server doesn't know any more).
     *
     * @param key a stored lower-case key, or null
     */
    public static Motif fromKey(String key) {
        if (key == null) return null;
        for (Motif m : values()) if (m.key().equals(key)) return m;
        return null;
    }

    /**
     * A motif from the words of a book's title and topic, for a book the AI hasn't picked one for. Matches the start
     * of whole words, so "art" doesn't match "start", and falls back to {@link #ORNAMENT}.
     *
     * @param text the title and topic together; null or blank gives the ornament
     */
    public static Motif guess(String text) {
        if (text == null || text.isBlank()) return ORNAMENT;
        String words = " " + text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").strip() + " ";
        for (var entry : WORDS) {
            for (String word : entry.getValue()) {
                if (words.contains(" " + word)) return entry.getKey();
            }
        }
        return ORNAMENT;
    }
}
