package com.laa.llm;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import com.laa.tutor.GradeResult;

/**
 * The structured output type each task produces, where one exists yet. Tasks register here as their
 * milestone lands (M2: MISSION_INTERVIEW draft, SAFETY_CHECK; M3: LESSON_PLAN, LESSON_WRITE; …).
 * Compare uses it to decide between a schema-checked and a plain-text run.
 */
public final class TaskSchemas {

    private static final Map<Task, Class<?>> TYPES = new EnumMap<>(Task.class);

    static {
        TYPES.put(Task.GRADE, GradeResult.class);
    }

    private TaskSchemas() {}

    public static Optional<Class<?>> typeFor(Task task) {
        return Optional.ofNullable(TYPES.get(task));
    }
}
