package com.laa.lesson;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.laa.lesson.LessonDraft.Block;
import com.laa.lesson.LessonDraft.ReferenceSheet;
import com.laa.lesson.LessonDraft.Rubric;
import com.laa.lesson.LessonDraft.Type;
import com.laa.lesson.LessonRules.Problem;

/** The rules LESSON_WRITE output must pass before a lesson is stored (BRIEF section 7). */
class LessonRulesTest {

    private static final List<String> GOOD = List.of("Turn your hip to the wall", "Pull harder with both arms", "Keep both feet off the wall");

    private static Block quiz(String id, String style, List<String> options, int answer) {
        return new Block(id, Type.QUIZ, style, "Which move saves your arms?", options, answer, "Hips close to the wall shift weight to your feet.", List.of());
    }

    private static Block block(String id, Type type, String style, String text, List<String> items, List<String> refs) {
        return new Block(id, type, style, text, items, -1, "", refs);
    }

    private static List<Block> lessonBlocks(Block... extra) {
        List<Block> blocks = new ArrayList<>(List.of(extra));
        blocks.add(block("h", Type.HEADING, "", "Where your weight goes", List.of(), List.of()));
        blocks.add(block("t", Type.TEXT, "", "Your **legs** are stronger than your arms.", List.of(), List.of("r1", "r9")));
        blocks.add(block("c", Type.CALLOUT, "watch_out", "Straight-arm hanging with hips away from the wall.", List.of(), List.of()));
        blocks.add(quiz("q", "check", GOOD, 0));
        blocks.add(block("f", Type.FREE_RESPONSE, "", "Explain why turning your hip in helps.",
                List.of("Where is your weight?", "What do your legs do?", "Think about hips and the wall."), List.of()));
        blocks.add(block("p", Type.PRIMARY_SOURCE, "", "Watch after practising.", List.of(), List.of("r1")));
        blocks.add(block("a", Type.ASK_TUTOR, "", "", List.of("Why do forearms pump?"), List.of()));
        return blocks;
    }

    private static LessonDraft draft(List<Block> blocks, List<Rubric> rubrics) {
        return new LessonDraft("Bring the wall to your hips", "Why your forearms give out.", "You’ll be able to…",
                blocks, new ReferenceSheet("Body position", "- Hips in"), rubrics);
    }

    private static final List<Rubric> RUBRIC = List.of(new Rubric("f", List.of("Weight moves to the feet"), "Hips in, weight on legs."));

    @Test
    void aGoodLessonBecomesClientJsonWithRenumberedIdsAndShuffledQuizzes() {
        var result = LessonRules.apply(draft(lessonBlocks(quiz("w", "warmup", GOOD, 1)), RUBRIC), 2, Set.of("r1"), new Random(7), false);

        assertThat(result.ok()).as(result.problems().toString()).isTrue();
        assertThat(result.blocks()).extracting(b -> b.get("id")).containsExactly("b1", "b2", "b3", "b4", "b5", "b6", "b7", "b8");
        assertThat(result.blocks()).extracting(b -> b.get("type")).containsExactly(
                "quiz", "heading", "text", "callout", "quiz", "free_response", "primary_source", "ask_tutor");

        // Whatever the shuffle did, the answer index still points at the right option.
        Map<String, Object> check = result.blocks().get(4);
        @SuppressWarnings("unchecked")
        List<String> options = (List<String>) check.get("options");
        assertThat(options).containsExactlyInAnyOrderElementsOf(GOOD);
        assertThat(options.get((Integer) check.get("answer"))).isEqualTo("Turn your hip to the wall");
        @SuppressWarnings("unchecked")
        List<String> warmupOptions = (List<String>) result.blocks().get(0).get("options");
        assertThat(warmupOptions.get((Integer) result.blocks().get(0).get("answer"))).isEqualTo("Pull harder with both arms");

        // Unknown citations are dropped; known ones kept.
        assertThat(result.blocks().get(2).get("citations")).isEqualTo(List.of("r1"));
        assertThat(result.removed()).anyMatch(r -> r.contains("r9"));

        // Hints and rubric stay server-side, keyed by the new id.
        Map<String, Object> practice = result.blocks().get(5);
        assertThat(practice).containsEntry("hintCount", 3).doesNotContainKeys("hints", "rubric", "points");
        assertThat(result.marking()).containsOnlyKeys("b6");
        assertThat(result.marking().get("b6").points()).containsExactly("Weight moves to the feet");
        assertThat(result.marking().get("b6").hints()).hasSize(3);
    }

    @Test
    void lessonOneDropsWarmUpsAndLaterLessonsMustOpenWithOne() {
        var first = LessonRules.apply(draft(lessonBlocks(quiz("w", "warmup", GOOD, 0)), RUBRIC), 1, Set.of("r1"), new Random(1), false);
        assertThat(first.ok()).isTrue();
        assertThat(first.blocks().getFirst().get("type")).isEqualTo("heading");
        assertThat(first.removed()).anyMatch(r -> r.contains("warm-up dropped"));

        var second = LessonRules.apply(draft(lessonBlocks(), RUBRIC), 2, Set.of("r1"), new Random(1), false);
        assertThat(second.problems()).extracting(Problem::message).anyMatch(m -> m.contains("must open with a warm-up"));

        List<Block> late = lessonBlocks(quiz("w0", "warmup", GOOD, 0));
        late.add(3, quiz("w1", "warmup", GOOD, 0));
        var misplaced = LessonRules.apply(draft(late, RUBRIC), 2, Set.of(), new Random(1), false);
        assertThat(misplaced.problems()).extracting(Problem::message).anyMatch(m -> m.contains("must come first"));
    }

    @Test
    void quizRulesCatchGiveawaysAndMalformedQuizzes() {
        assertThat(rules(List.of("Yes", "No", "It depends on several different factors"), 0)).anyMatch(m -> m.contains("lengths"));
        assertThat(rules(List.of("Hips in close", "Arms bent hard now", "Pull with straight arms always"), 0)).anyMatch(m -> m.contains("words"));
        assertThat(rules(List.of("Turn your hip in", "Pull with your arm", "All of the above"), 0)).anyMatch(m -> m.contains("aren't allowed"));
        assertThat(rules(List.of("Hips in", "hips in", "Arms out"), 0)).anyMatch(m -> m.contains("repeat"));
        assertThat(rules(List.of("Hips in", "Arms out"), 0)).anyMatch(m -> m.contains("3 or 4 options"));
        assertThat(rules(GOOD, 3)).anyMatch(m -> m.contains("index"));
        assertThat(rules(List.of("Hips in", " ", "Arms out"), 0)).anyMatch(m -> m.contains("empty"));
        assertThat(rules(GOOD, 2)).isEmpty();
        // Short options get a few characters' slack.
        assertThat(rules(List.of("Hips", "Arms", "Feet!!"), 0)).isEmpty();
    }

    private static List<String> rules(List<String> options, int answer) {
        return LessonRules.quizRules("q", options, answer).stream().map(Problem::message).toList();
    }

    @Test
    void onTheFinalAttemptABrokenQuizIsDroppedIfTheLessonStandsWithoutIt() {
        List<Block> blocks = lessonBlocks();
        blocks.add(quiz("q2", "check", List.of("Yes", "No", "It depends on several different factors"), 0));

        assertThat(LessonRules.apply(draft(blocks, RUBRIC), 1, Set.of(), new Random(1), false).ok()).isFalse();
        var lenient = LessonRules.apply(draft(blocks, RUBRIC), 1, Set.of(), new Random(1), true);
        assertThat(lenient.ok()).isTrue();
        assertThat(lenient.blocks()).filteredOn(b -> "quiz".equals(b.get("type"))).hasSize(1);
        assertThat(lenient.removed()).anyMatch(r -> r.contains("quiz rules"));

        // If dropping it leaves no "check yourself" quiz, the lesson still fails.
        List<Block> onlyBroken = new ArrayList<>(lessonBlocks());
        onlyBroken.removeIf(b -> b.type() == Type.QUIZ);
        onlyBroken.add(quiz("q2", "check", List.of("Yes", "No", "It depends on several different factors"), 0));
        assertThat(LessonRules.apply(draft(onlyBroken, RUBRIC), 1, Set.of(), new Random(1), true).problems())
                .extracting(Problem::message).anyMatch(m -> m.contains("check yourself"));
    }

    @Test
    void structuralProblemsAreReported() {
        List<Block> blocks = new ArrayList<>(lessonBlocks());
        blocks.add(block("c2", Type.CALLOUT, "shout", "Loud", List.of(), List.of()));
        blocks.add(block("f2", Type.FREE_RESPONSE, "", "Another prompt", List.of("one hint"), List.of()));
        blocks.add(block("h", Type.HEADING, "", "Duplicate id", List.of(), List.of()));
        var result = LessonRules.apply(draft(blocks, RUBRIC), 1, Set.of(), new Random(1), true);
        assertThat(result.problems()).extracting(Problem::message)
                .anyMatch(m -> m.contains("win, note or watch_out"))
                .anyMatch(m -> m.contains("give 3 hints"))
                .anyMatch(m -> m.contains("no rubric with blockId f2"))
                .anyMatch(m -> m.contains("used more than once"));
        assertThat(result.problems()).noneMatch(Problem::quiz);

        var noPractice = new ArrayList<>(lessonBlocks());
        noPractice.removeIf(b -> b.type() == Type.FREE_RESPONSE);
        assertThat(LessonRules.apply(draft(noPractice, List.of()), 1, Set.of(), new Random(1), true).problems())
                .extracting(Problem::message).anyMatch(m -> m.contains("FREE_RESPONSE"));
    }

    @Test
    void aPrimarySourceWithoutAKnownResourceIsDropped() {
        var result = LessonRules.apply(draft(lessonBlocks(), RUBRIC), 1, Set.of(), new Random(1), false);
        assertThat(result.ok()).isTrue();
        assertThat(result.blocks()).noneMatch(b -> "primary_source".equals(b.get("type")));
        assertThat(result.blocks().get(1).get("citations")).isEqualTo(List.of());
    }

    // ------------------------------------------------------------------ diagrams

    private static Block diagram(String id, String brief, String caption) {
        return new Block(id, Type.DIAGRAM, "", brief, List.of(), -1, caption, List.of());
    }

    @Test
    void aDiagramReachesTheAppWithOnlyItsCaptionAndItsBriefStaysOnTheServer() {
        var result = LessonRules.apply(draft(lessonBlocks(diagram("d", "Tap the hip, then the feet, to see where the weight goes.",
                "Tap each part to see where your weight goes.")), RUBRIC), 1, Set.of("r1"), new Random(7), false);

        assertThat(result.ok()).as(result.problems().toString()).isTrue();
        Map<String, Object> client = result.blocks().getFirst();
        assertThat(client).containsOnlyKeys("id", "type", "caption").containsEntry("type", "diagram")
                .containsEntry("caption", "Tap each part to see where your weight goes.");
        // The brief is kept under the block's new id, for the diagram writer.
        assertThat(result.diagrams()).containsOnlyKeys((String) client.get("id"));
        assertThat(result.diagrams().get(client.get("id")).brief()).startsWith("Tap the hip");
        assertThat(result.diagrams().get(client.get("id")).caption()).isEqualTo("Tap each part to see where your weight goes.");
    }

    @Test
    void aDiagramWithoutABriefOrCaptionIsSentBackForARewrite() {
        var noBrief = LessonRules.apply(draft(lessonBlocks(diagram("d", " ", "A caption.")), RUBRIC), 1, Set.of("r1"), new Random(7), false);
        assertThat(noBrief.problems()).anyMatch(p -> p.message().contains("brief") && !p.quiz());

        var noCaption = LessonRules.apply(draft(lessonBlocks(diagram("d", "A brief.", "")), RUBRIC), 1, Set.of("r1"), new Random(7), false);
        assertThat(noCaption.problems()).anyMatch(p -> p.message().contains("caption") && !p.quiz());
    }

    @Test
    void aThirdDiagramIsDroppedNotSentBack() {
        var result = LessonRules.apply(draft(lessonBlocks(diagram("d1", "One.", "First."), diagram("d2", "Two.", "Second."),
                diagram("d3", "Three.", "Third.")), RUBRIC), 1, Set.of("r1"), new Random(7), false);

        assertThat(result.ok()).as(result.problems().toString()).isTrue();
        assertThat(result.blocks().stream().filter(b -> b.get("type").equals("diagram"))).hasSize(2);
        assertThat(result.diagrams().values()).extracting(LessonRules.DiagramBrief::brief).containsExactly("One.", "Two.");
        assertThat(result.removed()).anyMatch(r -> r.contains("d3") && r.contains("at most 2 diagrams"));
    }

    @Test
    void aLessonWithoutDiagramsHasNoBriefs() {
        var result = LessonRules.apply(draft(lessonBlocks(), RUBRIC), 1, Set.of("r1"), new Random(7), false);
        assertThat(result.diagrams()).isEmpty();
    }
}
