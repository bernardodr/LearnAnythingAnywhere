package com.laa.llm.provider.fake;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.laa.llm.ChatMessage;
import com.laa.llm.Task;
import com.laa.llm.provider.ProviderRequest;
import com.laa.mission.MissionInterview;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Task-aware replies that make the offline demo read like the real thing, where schema-generated JSON
 * ("[FAKE] title") would not: the topic gate allows the topic as typed, the mission draft is built from the
 * learner's own answers, and the preface interview follows a short script. Matched on task and schema shape,
 * so other calls for the same task (the owner's dev ping) still get schema-generated JSON.
 */
final class FakeScripts {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Set<String> TOPIC_VERDICT = Set.of("verdict", "title", "message", "categories");
    private static final Set<String> MISSION_DRAFT = Set.of("headline", "why", "successLooksLike", "constraints", "outOfScope");

    private FakeScripts() {}

    /** A scripted structured reply, or null to fall back to schema-generated JSON. */
    static String structured(ProviderRequest request, String tag) {
        Set<String> props = propertyNames(request.outputSchema());
        if (request.task() == Task.SAFETY_CHECK && props.equals(TOPIC_VERDICT)) {
            // Empty title: the gate then uses the learner's topic, tidied.
            return json(Map.of("verdict", "ALLOW", "title", "", "message", "", "categories", List.of()));
        }
        if (request.task() == Task.MISSION_INTERVIEW && props.equals(MISSION_DRAFT)) {
            return missionDraft(request, tag);
        }
        return null;
    }

    /** The preface: an opening question, one follow-up, then the end-of-interview marker after two answers. */
    static String interviewTurn(ProviderRequest request, String tag) {
        long userTurns = request.messages().stream().filter(m -> m.role() == ChatMessage.Role.USER).count();
        // The first user turn is the server's kickoff, so learner answers = user turns - 1.
        if (userTurns <= 1) return tag + "Hello! What’s drawing you to this, and what would you love to be able to do?";
        if (userTurns == 2) return tag + "That’s a great start. What would success look like a few months from now?";
        return tag + "Thank you, I have what I need to draft your mission. " + MissionInterview.MARKER;
    }

    private static String missionDraft(ProviderRequest request, String tag) {
        List<String> answers = new ArrayList<>();
        for (ChatMessage m : request.messages()) {
            if (m.role() != ChatMessage.Role.USER) continue;
            for (String line : m.content().split("\n")) {
                if (line.startsWith("LEARNER: ")) answers.add(line.substring("LEARNER: ".length()).strip());
            }
        }
        String first = answers.isEmpty() ? "learn this well" : answers.getFirst();
        String second = answers.size() > 1 ? answers.get(1) : first;
        return json(Map.of(
                "headline", tag + clip(words(first, 8), 80),
                "why", tag + clip(first, 500),
                "successLooksLike", List.of(tag + clip(second, 180)),
                "constraints", List.of(),
                "outOfScope", List.of()));
    }

    private static Set<String> propertyNames(String schemaJson) {
        Set<String> names = new HashSet<>();
        if (schemaJson == null) return names;
        JsonNode props = MAPPER.readTree(schemaJson).path("properties");
        props.propertyNames().forEach(names::add);
        return names;
    }

    private static String words(String s, int n) {
        String[] w = s.split("\\s+");
        return String.join(" ", java.util.Arrays.copyOf(w, Math.min(n, w.length)));
    }

    private static String clip(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max).strip();
    }

    private static String json(Object value) {
        return MAPPER.writeValueAsString(value);
    }
}
