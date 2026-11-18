package com.laa.llm;

import java.util.List;
import java.util.Optional;

import com.laa.lesson.DiagramDraft;
import com.laa.lesson.LessonDraft;
import com.laa.lesson.LessonPlan;
import com.laa.mission.MissionDraft;
import com.laa.safety.LeakVerdict;
import com.laa.safety.ModerationVerdict;
import com.laa.safety.TopicVerdict;
import com.laa.tutor.GradeResult;
import com.laa.tutor.RecordUpdate;

/**
 * Every model call the app makes, registered once (BRIEF section 5). Adding a call means one constant here and its
 * prompt file. The app refuses to start if a registered call doesn't fit ({@link CallsCheck}); the practice provider
 * replies per call; the owner's Compare can run any of them.
 */
public final class Calls {

    // Preface (BRIEF 6.2)
    public static final TextCall INTERVIEW_TURN = new TextCall(Task.MISSION_INTERVIEW, null, "Preface reply");
    public static final StructuredCall<MissionDraft> MISSION_DRAFT =
            new StructuredCall<>(Task.MISSION_INTERVIEW, "draft", MissionDraft.class, "Mission draft");

    // Lessons (BRIEF 6.4)
    public static final StructuredCall<LessonPlan> LESSON_PLAN =
            new StructuredCall<>(Task.LESSON_PLAN, null, LessonPlan.class, "Lesson plan");
    public static final StructuredCall<LessonDraft> LESSON_WRITE =
            new StructuredCall<>(Task.LESSON_WRITE, null, LessonDraft.class, "Lesson writing");
    /** One interactive diagram for a lesson, from the brief the lesson writer left in a DIAGRAM block. */
    public static final StructuredCall<DiagramDraft> DIAGRAM =
            new StructuredCall<>(Task.DIAGRAM, null, DiagramDraft.class, "Diagram");

    // Tutor (BRIEF 6.5)
    public static final StructuredCall<GradeResult> GRADE = new StructuredCall<>(Task.GRADE, null, GradeResult.class, "Grading");
    public static final StructuredCall<RecordUpdate> RECORD_UPDATE =
            new StructuredCall<>(Task.RECORD_UPDATE, null, RecordUpdate.class, "Learning notes");
    public static final TextCall TUTOR_REPLY = new TextCall(Task.TUTOR, null, "Tutor reply");

    // Safety (BRIEF 8)
    public static final StructuredCall<TopicVerdict> TOPIC_GATE =
            new StructuredCall<>(Task.SAFETY_CHECK, "topic", TopicVerdict.class, "Topic gate");
    public static final StructuredCall<ModerationVerdict> CONTENT_CHECK =
            new StructuredCall<>(Task.SAFETY_CHECK, "content", ModerationVerdict.class, "Content check");
    public static final StructuredCall<LeakVerdict> LEAK_CHECK =
            new StructuredCall<>(Task.SAFETY_CHECK, "leak", LeakVerdict.class, "Leak check");

    // Not called by the app yet (BRIEF 6.3); registered so it can be routed and compared.
    public static final TextCall RESEARCH = new TextCall(Task.RESEARCH, null, "Research (not used yet)");

    private static final List<Call> ALL = List.of(INTERVIEW_TURN, MISSION_DRAFT, LESSON_PLAN, LESSON_WRITE, DIAGRAM, GRADE,
            RECORD_UPDATE, TUTOR_REPLY, TOPIC_GATE, CONTENT_CHECK, LEAK_CHECK, RESEARCH);

    private Calls() {}

    public static List<Call> all() {
        return ALL;
    }

    public static Optional<Call> byId(String id) {
        return ALL.stream().filter(c -> c.id().equals(id)).findFirst();
    }

    public static List<Call> forTask(Task task) {
        return ALL.stream().filter(c -> c.task() == task).toList();
    }
}
