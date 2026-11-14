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
        if (request.task() == Task.LESSON_PLAN && props.equals(LESSON_PLAN)) {
            return json(Map.of(
                    "title", tag + "Your next small step",
                    "skill", tag + "Do one small, concrete thing from your mission with confidence",
                    "win", "You’ll be able to take one small, concrete step towards your mission and explain why it works.",
                    "whyNow", tag + "It is the smallest useful step that builds on what you already know.",
                    "knowledge", List.of(tag + "The one idea this step depends on", tag + "The most common mistake"),
                    "practice", tag + "Describe how you would take the step, in your own words",
                    "warmupFromLesson", 0));
        }
        if (request.task() == Task.LESSON_WRITE && props.equals(LESSON_DRAFT)) {
            return lesson(request, tag);
        }
        if (request.task() == Task.GRADE && props.equals(GRADE_RESULT)) {
            return grade(request, tag);
        }
        if (request.task() == Task.SAFETY_CHECK && props.equals(LEAK_VERDICT)) {
            return json(Map.of("leaks", false, "reason", ""));
        }
        if (request.task() == Task.RECORD_UPDATE && props.equals(RECORD_UPDATE)) {
            return json(Map.of(
                    "writeRecord", true,
                    "slug", "explains-why-small-steps-work",
                    "title", tag + "Explains why small steps work",
                    "body", tag + "Can name a concrete small step and explain the quick feedback it gives. Next lessons can build on repeating it.",
                    "evidence", tag + "Named a step and said why it helps.",
                    "glossary", List.of(Map.of("term", "Small step",
                            "definition", tag + "One concrete action you can repeat and check quickly.", "avoid", List.of("baby step")))));
        }
        return null;
    }

    private static final Set<String> GRADE_RESULT = Set.of("verdict", "coveredPoints", "missingPoints", "coachingMove", "message");
    private static final Set<String> LEAK_VERDICT = Set.of("leaks", "reason");
    private static final Set<String> RECORD_UPDATE = Set.of("writeRecord", "slug", "title", "body", "evidence", "glossary");

    /**
     * Grades by a simple rule so the demo can show both outcomes: an answer that explains why (contains "because")
     * covers every rubric point; otherwise only the first, with a question that doesn't give the rest away.
     * Rubric point ids are read from the "- p1: …" lines of the grading prompt.
     */
    private static String grade(ProviderRequest request, String tag) {
        List<String> ids = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?m)^- (p\\d+): ").matcher(request.system() == null ? "" : request.system());
        while (m.find()) ids.add(m.group(1));
        String answer = request.messages().isEmpty() ? "" : request.messages().getLast().content();
        boolean explainsWhy = answer.toLowerCase(java.util.Locale.ROOT).contains("because");
        if (ids.isEmpty() || explainsWhy) {
            return json(Map.of("verdict", "MET", "coveredPoints", ids, "missingPoints", List.of(), "coachingMove", "CONFIRM",
                    "message", tag + "That’s it: you named your step and explained why it works. Try it at your next session."));
        }
        return json(Map.of("verdict", "PARTIAL", "coveredPoints", List.of(ids.getFirst()), "missingPoints", ids.subList(1, ids.size()),
                "coachingMove", "ASK",
                "message", tag + "Good start: you picked a clear step. What happens straight after you try it once?"));
    }

    private static final Set<String> LESSON_PLAN = Set.of("title", "skill", "win", "whyNow", "knowledge", "practice", "warmupFromLesson");
    private static final Set<String> LESSON_DRAFT = Set.of("title", "subtitle", "win", "blocks", "referenceSheet", "rubrics");

    /**
     * A lesson with every block type that passes the server's rules: options of equal length and word count,
     * three hints and a rubric for the practice, a warm-up first (the server drops it from lesson 1), and
     * citations only when the learner has added sources (r1 appears in the prompt).
     */
    private static String lesson(ProviderRequest request, String tag) {
        String message = request.messages().isEmpty() ? "" : request.messages().getLast().content();
        java.util.regex.Matcher title = java.util.regex.Pattern.compile("\"title\"\\s*:\\s*\"([^\"]+)\"").matcher(message);
        boolean hasSource = request.system() != null && request.system().contains("\n- r1: ");
        List<String> cite = hasSource ? List.of("r1") : List.of();
        List<Map<String, Object>> blocks = new ArrayList<>();
        blocks.add(block("w1", "QUIZ", "warmup", "Which idea from the last lesson matters most here?",
                List.of("The first idea we practised", "The second idea we skipped", "The third idea we ignored"), 0,
                "The first idea is the one this lesson builds on.", List.of()));
        blocks.add(block("h1", "HEADING", "", "Where to begin", List.of(), -1, "", List.of()));
        blocks.add(block("t1", "TEXT", "", tag + "Start with **one small step** you can repeat. Small steps are easy to "
                + "practise and easy to check, so you get feedback quickly.\n\n- Pick the step\n- Try it once\n- Notice what happened",
                List.of(), -1, "", cite));
        blocks.add(block("k1", "KEY_IDEA", "", tag + "Small, repeated steps beat one big leap.", List.of(), -1, "", List.of()));
        blocks.add(block("c1", "CALLOUT", "watch_out", tag + "Trying to do everything at once. Pick one step and stay with it.",
                List.of(), -1, "", List.of()));
        blocks.add(block("q1", "QUIZ", "check", "What is the best way to start?",
                List.of("Practise it in small steps", "Read about it once more", "Skip it and hope for luck"), 0,
                "Small steps give you quick feedback, which is how a skill sticks.", List.of()));
        blocks.add(block("s1", "STEPS", "", "Try it today", List.of(tag + "Choose one small step", tag + "Do it three times",
                tag + "Write down what changed"), -1, "", List.of()));
        blocks.add(block("f1", "FREE_RESPONSE", "", tag + "In two or three sentences, describe the small step you’ll take and why it helps.",
                List.of("Think about what you could do in ten minutes.", "Which part of your mission does it move forward?",
                        "Name the step, then say what feedback it gives you."), -1, "", List.of()));
        if (hasSource) {
            blocks.add(block("p1", "PRIMARY_SOURCE", "", tag + "Read this before your next session.", List.of(), -1, "", List.of("r1")));
        }
        blocks.add(block("a1", "ASK_TUTOR", "", "", List.of("Why do small steps work better?", "What if my step is too easy?"), -1, "", List.of()));
        return json(Map.of(
                "title", title.find() ? title.group(1) : tag + "Your next small step",
                "subtitle", tag + "Why the smallest useful step is the fastest way forward.",
                "win", "You’ll be able to take one small, concrete step towards your mission and explain why it works.",
                "blocks", blocks,
                "referenceSheet", Map.of("title", "Small steps", "md", "- Pick **one** step\n- Repeat it\n- Check what changed"),
                "rubrics", List.of(Map.of("blockId", "f1",
                        "points", List.of("Names one concrete step", "Explains how it gives quick feedback"),
                        "answerOutline", "A specific small action, plus why repeating it gives quick feedback."))));
    }

    private static Map<String, Object> block(String id, String type, String style, String text, List<String> items,
                                             int answer, String explanation, List<String> refs) {
        Map<String, Object> b = new java.util.LinkedHashMap<>();
        b.put("id", id);
        b.put("type", type);
        b.put("style", style);
        b.put("text", text);
        b.put("items", items);
        b.put("answer", answer);
        b.put("explanation", explanation);
        b.put("refs", refs);
        return b;
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
