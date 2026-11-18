package com.laa.llm.provider.fake;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.laa.llm.Call;
import com.laa.llm.Calls;
import com.laa.llm.ChatMessage;
import com.laa.llm.TextCall;
import com.laa.llm.provider.ProviderRequest;
import com.laa.mission.MissionInterview;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Task-aware replies that make the offline demo read like the real thing, where schema-generated JSON
 * ("[FAKE] title") would not: the topic gate allows the topic as typed, the mission draft is built from the
 * learner's own answers, and the preface interview follows a short script. Matched on the call ({@link Calls}), so
 * an unregistered call for the same task (the dev ping) still gets schema-generated JSON.
 */
final class FakeScripts {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /** The structured calls with a scripted reply; every other structured call gets schema-generated JSON. */
    private static final Set<Call> SCRIPTED = Set.of(Calls.TOPIC_GATE, Calls.MISSION_DRAFT, Calls.LESSON_PLAN, Calls.LESSON_WRITE, Calls.DIAGRAM,
            Calls.GRADE, Calls.LEAK_CHECK, Calls.CONTENT_CHECK, Calls.RECORD_UPDATE);

    private FakeScripts() {}

    /** True if practice mode has a reply written for this call (text calls always do: a script or a fixed sentence). */
    static boolean scripted(Call call) {
        return call instanceof TextCall || SCRIPTED.contains(call);
    }

    /** A scripted structured reply, or null to fall back to schema-generated JSON. */
    static String structured(ProviderRequest request, String tag) {
        Call call = request.call();
        if (call.equals(Calls.TOPIC_GATE)) {
            // Empty title: the gate then uses the learner's topic, tidied.
            return json(Map.of("verdict", "ALLOW", "title", "", "message", "", "categories", List.of()));
        }
        if (call.equals(Calls.MISSION_DRAFT)) {
            return missionDraft(request, tag);
        }
        if (call.equals(Calls.LESSON_PLAN)) {
            return json(Map.of(
                    "title", tag + "Your next small step",
                    "skill", tag + "Do one small, concrete thing from your mission with confidence",
                    "win", "You’ll be able to take one small, concrete step towards your mission and explain why it works.",
                    "whyNow", tag + "It is the smallest useful step that builds on what you already know.",
                    "knowledge", List.of(tag + "The one idea this step depends on", tag + "The most common mistake"),
                    "practice", tag + "Describe how you would take the step, in your own words",
                    "warmupFromLesson", 0));
        }
        if (call.equals(Calls.LESSON_WRITE)) {
            return lesson(request, tag);
        }
        if (call.equals(Calls.DIAGRAM)) {
            return diagram(tag);
        }
        if (call.equals(Calls.GRADE)) {
            return grade(request, tag);
        }
        if (call.equals(Calls.LEAK_CHECK)) {
            return json(Map.of("leaks", false, "reason", ""));
        }
        if (call.equals(Calls.CONTENT_CHECK)) {
            return moderation(request);
        }
        if (call.equals(Calls.RECORD_UPDATE)) {
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


    /**
     * Content moderation: every item ({@code <item id="…">…</item>}) passes, except one containing
     * {@link FakeBehaviour#BLOCK_MARKER}.
     * LAA's own deterministic rules run as well, so the fake still declines what they catch.
     */
    private static String moderation(ProviderRequest request) {
        String message = request.messages().isEmpty() ? "" : request.messages().getLast().content();
        List<Map<String, Object>> items = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?s)<item id=\"([^\"]+)\">(.*?)</item>").matcher(message);
        while (m.find()) {
            boolean block = m.group(2).contains(FakeBehaviour.BLOCK_MARKER);
            items.add(Map.of("id", m.group(1), "verdict", block ? "BLOCK" : "PASS",
                    "categories", block ? List.of("scary_content") : List.of(), "reason", block ? "test block" : ""));
        }
        return json(Map.of("items", items));
    }

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
        blocks.add(block("d1", "DIAGRAM", "", "A step-through of the small-step loop: pick the step, try it once, notice what "
                + "happened, then repeat. Back and Next move between the four stages, highlighting each on a loop.",
                List.of(), -1, tag + "Press Next to walk round the loop one stage at a time.", List.of()));
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

    /**
     * Practice mode's diagram: a working step-through of the small-step loop, so the whole diagram path (the page
     * rules, the safety check, the sealed frame in the app) can be tried without a real AI service. It follows the
     * diagram prompt's own rules: LAA's CSS variables, real buttons, whole strings, no outside anything.
     */
    private static String diagram(String tag) {
        String html = """
                <style>
                  .loop { display: block; width: 100%; max-width: 360px; margin: 0 auto; }
                  .stage circle { fill: var(--note); stroke: var(--rule); stroke-width: 2; transition: fill 200ms; }
                  .stage text { font: 600 13px var(--sans); fill: var(--ink); text-anchor: middle; }
                  .stage.on circle { fill: var(--accent); stroke: var(--accent); }
                  .stage.on text { fill: var(--paper); }
                  .panel { background: var(--note); border-radius: 8px; padding: 12px 14px; margin: 12px 0; min-height: 3.2em;
                           font: 16px/1.5 var(--serif); color: var(--ink); }
                  .count { font: 13px var(--sans); color: var(--muted); }
                  .controls { display: flex; gap: 8px; justify-content: space-between; align-items: center; }
                  @media (prefers-reduced-motion: reduce) { .stage circle { transition: none; } }
                </style>
                <svg class="loop" viewBox="0 0 300 300" role="img" aria-label="A loop of four stages: pick, try, notice, repeat">
                  <circle cx="150" cy="150" r="95" fill="none" stroke="var(--rule)" stroke-width="3"/>
                  <g class="stage" data-i="0"><circle cx="150" cy="55" r="38"/><text x="150" y="60">Pick</text></g>
                  <g class="stage" data-i="1"><circle cx="245" cy="150" r="38"/><text x="245" y="155">Try</text></g>
                  <g class="stage" data-i="2"><circle cx="150" cy="245" r="38"/><text x="150" y="250">Notice</text></g>
                  <g class="stage" data-i="3"><circle cx="55" cy="150" r="38"/><text x="55" y="155">Repeat</text></g>
                </svg>
                <div class="panel" aria-live="polite" id="panel"></div>
                <div class="controls">
                  <button class="laa-button" id="back">Back</button>
                  <span class="count" id="count"></span>
                  <button class="laa-button" id="next">Next</button>
                </div>
                <script>
                  const stages = [
                    "Pick one small step you can do in ten minutes.",
                    "Try it once, without worrying about doing it well.",
                    "Notice what happened: what worked, and what felt hard?",
                    "Repeat the step, changing one thing you noticed."
                  ];
                  let current = 0;
                  function show() {
                    document.querySelectorAll(".stage").forEach((g, i) => g.classList.toggle("on", i === current));
                    document.getElementById("panel").textContent = stages[current];
                    document.getElementById("count").textContent = (current + 1) + " of " + stages.length;
                  }
                  document.getElementById("back").addEventListener("click", () => { current = (current + stages.length - 1) % stages.length; show(); });
                  document.getElementById("next").addEventListener("click", () => { current = (current + 1) % stages.length; show(); });
                  show();
                </script>
                """;
        return json(Map.of(
                "title", tag + "The small-step loop",
                "html", html,
                "textVersion", "1. **Pick** one small step you can do in ten minutes.\n2. **Try** it once, without worrying about doing it well.\n"
                        + "3. **Notice** what happened: what worked, and what felt hard?\n4. **Repeat** the step, changing one thing you noticed."));
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
