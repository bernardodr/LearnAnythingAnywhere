package com.laa.textbook;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.laa.mission.MissionDraft;

public record Textbook(
        UUID id,
        UUID userId,
        String title,
        String topic,
        Status status,
        String coverColor,
        int volume,
        Map<String, Object> topicGate,
        MissionDraft missionDraft,
        String missionHeadline,
        Instant createdAt) {

    public enum Status { INTERVIEW, ACTIVE, ARCHIVED }

    /** Cover cloth colours from BRIEF section 9, assigned in turn as a learner's shelf grows. */
    public static final List<String> COVER_COLORS = List.of("#3E4C5E", "#5A6B4A", "#B4552F", "#23466B");

    public static String coverFor(int volume) {
        return COVER_COLORS.get((volume - 1) % COVER_COLORS.size());
    }
}
