package com.laa.llm;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import com.laa.lesson.LessonDraft;
import com.laa.lesson.LessonPlan;
import com.laa.mission.MissionDraft;
import com.laa.safety.TopicVerdict;
import com.laa.tutor.GradeResult;
import com.laa.tutor.RecordUpdate;

/**
 * The structured output type each task produces, where one exists yet, and the prompt variant that produces it.
 * Tasks register here as their milestone lands (M2: MISSION_INTERVIEW draft, SAFETY_CHECK topic gate;
 * M3: LESSON_PLAN, LESSON_WRITE; …). Compare uses it to decide between a schema-checked and a plain-text run.
 */
public final class TaskSchemas {

    private record Entry(Class<?> type, String variant) {}

    private static final Map<Task, Entry> TYPES = new EnumMap<>(Task.class);

    static {
        TYPES.put(Task.MISSION_INTERVIEW, new Entry(MissionDraft.class, "draft")); // interview turns are streamed text
        TYPES.put(Task.LESSON_PLAN, new Entry(LessonPlan.class, null));
        TYPES.put(Task.LESSON_WRITE, new Entry(LessonDraft.class, null));
        TYPES.put(Task.GRADE, new Entry(GradeResult.class, null));
        TYPES.put(Task.RECORD_UPDATE, new Entry(RecordUpdate.class, null));
        // Compare runs the topic gate; the "content" and "leak" variants are run by the safety layer and the tutor.
        TYPES.put(Task.SAFETY_CHECK, new Entry(TopicVerdict.class, "topic"));
    }

    private TaskSchemas() {}

    public static Optional<Class<?>> typeFor(Task task) {
        return Optional.ofNullable(TYPES.get(task)).map(Entry::type);
    }

    /** The prompt variant that produces {@link #typeFor}'s type (empty: the task's main prompt). */
    public static Optional<String> variantFor(Task task) {
        return Optional.ofNullable(TYPES.get(task)).map(Entry::variant);
    }
}
