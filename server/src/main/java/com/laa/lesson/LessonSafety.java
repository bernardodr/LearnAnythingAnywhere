package com.laa.lesson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.laa.lesson.LessonDraft.Block;
import com.laa.lesson.LessonDraft.ReferenceSheet;
import com.laa.lesson.LessonDraft.Rubric;
import com.laa.lesson.LessonDraft.Type;
import com.laa.llm.GenerationProfile;
import com.laa.safety.LinkPolicy;
import com.laa.safety.Moderation;

/**
 * The safety layer's part in writing a lesson (BRIEF section 8): the links policy on every piece of text, and
 * moderation of the header, each block (with a practice block's rubric and hints, which the learner sees in
 * part) and the reference sheet. A block that doesn't pass is rewritten once by {@link LessonWriter}, then
 * {@link #withPlaceholders replaced} by a note saying it was left out.
 */
final class LessonSafety {

    static final String HEADER = "header";
    static final String SHEET = "sheet";
    private static final String BLOCK = "block-";

    private LessonSafety() {}

    /** The draft with the profile's links policy applied; allowed links are the learner's own sources. */
    static LessonDraft links(LessonDraft d, GenerationProfile profile, Set<String> allowed) {
        var policy = profile.linksPolicy();
        List<Block> blocks = d.blocks().stream().map(b -> new Block(b.id(), b.type(), b.style(),
                LinkPolicy.apply(b.text(), policy, allowed),
                b.items().stream().map(i -> LinkPolicy.apply(i, policy, allowed)).toList(),
                b.answer(), LinkPolicy.apply(b.explanation(), policy, allowed), b.refs())).toList();
        ReferenceSheet sheet = new ReferenceSheet(d.referenceSheet().title(),
                LinkPolicy.apply(d.referenceSheet().md(), policy, allowed));
        List<Rubric> rubrics = d.rubrics().stream().map(r -> new Rubric(r.blockId(),
                r.points().stream().map(p -> LinkPolicy.apply(p, policy, allowed)).toList(),
                LinkPolicy.apply(r.answerOutline(), policy, allowed))).toList();
        return new LessonDraft(LinkPolicy.apply(d.title(), policy, allowed), LinkPolicy.apply(d.subtitle(), policy, allowed),
                LinkPolicy.apply(d.win(), policy, allowed), blocks, sheet, rubrics);
    }

    static Moderation.Result check(Moderation moderation, LessonDraft d, GenerationProfile profile, UUID userId, UUID textbookId) {
        Map<String, Rubric> rubrics = new HashMap<>();
        for (Rubric r : d.rubrics()) rubrics.put(r.blockId(), r);
        List<Moderation.Item> items = new ArrayList<>();
        items.add(new Moderation.Item(HEADER, String.join("\n", d.title(), d.subtitle(), d.win())));
        for (Block b : d.blocks()) {
            List<String> parts = new ArrayList<>();
            parts.add(b.text());
            parts.addAll(b.items());
            parts.add(b.explanation());
            Rubric r = rubrics.get(b.id());
            if (b.type() == Type.FREE_RESPONSE && r != null) {
                parts.addAll(r.points());
                parts.add(r.answerOutline());
            }
            items.add(new Moderation.Item(BLOCK + b.id(),
                    String.join("\n", parts.stream().filter(p -> p != null && !p.isBlank()).toList())));
        }
        items.add(new Moderation.Item(SHEET, d.referenceSheet().title() + "\n" + d.referenceSheet().md()));
        return moderation.check(items, profile, Moderation.Kind.OUTPUT, userId, textbookId);
    }

    /** What to tell the writer when it rewrites, one line per part that didn't pass. */
    static List<String> problems(Moderation.Result check) {
        List<String> out = new ArrayList<>();
        for (String flag : check.flags()) {
            String id = flag.substring(0, flag.indexOf(':'));
            String why = flag.substring(flag.indexOf(':') + 1).strip();
            String what = switch (id) {
                case HEADER -> "The title, subtitle or win";
                case SHEET -> "The reference sheet";
                default -> "Block " + id.substring(BLOCK.length());
            };
            out.add(what + " doesn’t fit this learner’s profile (" + why + "). Rewrite it so it follows the profile rules.");
        }
        return out;
    }

    /** True if the lesson can't stand with placeholders: its header or reference sheet didn't pass. */
    static boolean unrecoverable(Moderation.Result check) {
        return check.blocks(HEADER) || check.blocks(SHEET);
    }

    /** The draft with every block that didn't pass replaced by a note saying it was left out. */
    static LessonDraft withPlaceholders(LessonDraft d, Moderation.Result check, List<String> removed) {
        List<Block> blocks = new ArrayList<>();
        for (Block b : d.blocks()) {
            if (check.blocks(BLOCK + b.id())) {
                removed.add("Block " + b.id() + " (" + b.type() + "): replaced with a placeholder, it didn’t fit the reader settings");
                blocks.add(new Block(b.id(), Type.CALLOUT, "note", Moderation.PLACEHOLDER, List.of(), -1, "", List.of()));
            } else {
                blocks.add(b);
            }
        }
        return new LessonDraft(d.title(), d.subtitle(), d.win(), blocks, d.referenceSheet(), d.rubrics());
    }
}
