package com.laa.textbook;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.laa.mission.MissionDraft;

/**
 * A learner's textbook, as stored. {@code coverMotif} is the subject motif the AI picked when the preface ended
 * (null before then, or for a book from before motifs existed: {@link #motif()} fills those in from the title).
 */
public record Textbook(
        UUID id,
        UUID userId,
        String title,
        String topic,
        Status status,
        String coverColor,
        Motif coverMotif,
        int volume,
        Map<String, Object> topicGate,
        MissionDraft missionDraft,
        String missionHeadline,
        Instant createdAt) {

    public enum Status { INTERVIEW, ACTIVE, ARCHIVED }

    /**
     * Cover cloths from docs/DESIGN.md (forest, ochre, dusty blue, terracotta), given in turn as a learner's library
     * grows. The app picks an ink or ivory title for each, so they stay readable.
     */
    public static final List<String> COVER_COLORS = List.of("#2F4A3B", "#D4A548", "#46668A", "#A9533A");

    /** The cloth for a learner's {@code volume}th book. */
    public static String coverFor(int volume) {
        return COVER_COLORS.get((volume - 1) % COVER_COLORS.size());
    }

    /** The motif to draw: the one the AI picked, or one matched from the title and topic until there is one. */
    public Motif motif() {
        return coverMotif != null ? coverMotif : Motif.guess(title + " " + topic);
    }
}
