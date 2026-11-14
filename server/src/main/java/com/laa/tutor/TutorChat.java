package com.laa.tutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.laa.chat.ChatMessageRepository;
import com.laa.chat.ChatMessageRepository.StoredMessage;
import com.laa.lesson.LessonRepository.Lesson;
import com.laa.llm.ChatMessage;
import com.laa.llm.GenerationProfile;
import com.laa.llm.LlmGateway;
import com.laa.llm.PromptContext;
import com.laa.llm.Task;
import com.laa.llm.runs.Origin;
import com.laa.safety.ReplyGuard;
import com.laa.textbook.Textbook;
import com.laa.web.ApiException;
import com.laa.workspace.Resources.Resource;
import com.laa.workspace.TeachingContext;

/**
 * Tutor chat inside a lesson (BRIEF section 6.5): explains knowledge freely, coaches practice with questions and
 * never solves it outright. The chat tutor sees the lesson as the learner does (and the workspace), but never the
 * rubrics or hints, so it has no hidden answer to leak; the grader, which does see them, is what the leak guard
 * watches. A turn is stored only once the reply finishes, and only as the safety layer let it through
 * ({@link ReplyGuard}: held back until checked for a child, otherwise checked and replaced if needed).
 */
@Service
public class TutorChat {

    public static final int MAX_MESSAGE_CHARS = 2_000;
    /** How much of the thread goes back to the model. */
    static final int HISTORY = 20;

    public interface Events extends ReplyGuard.Out {
        void done(UUID messageId);
    }

    private final LlmGateway gateway;
    private final ChatMessageRepository chats;
    private final TeachingContext teaching;
    private final ReplyGuard guard;

    TutorChat(LlmGateway gateway, ChatMessageRepository chats, TeachingContext teaching, ReplyGuard guard) {
        this.gateway = gateway;
        this.chats = chats;
        this.teaching = teaching;
        this.guard = guard;
    }

    public List<StoredMessage> thread(Lesson lesson) {
        return chats.lessonThread(lesson.id());
    }

    public void check(Textbook tb, String message) {
        if (tb.status() != Textbook.Status.ACTIVE) throw new ApiException(HttpStatus.CONFLICT, "This textbook has no lessons yet.");
        if (message == null || message.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "Ask your tutor something first.");
        if (message.length() > MAX_MESSAGE_CHARS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please keep messages under " + MAX_MESSAGE_CHARS + " characters.");
        }
    }

    /** One turn, streamed. Blocks until done; call {@link #check} first. */
    public void turn(Textbook tb, Lesson lesson, UUID userId, String message, GenerationProfile profile, Events events) {
        List<StoredMessage> thread = chats.lessonThread(lesson.id());
        List<ChatMessage> history = new ArrayList<>();
        for (StoredMessage m : thread.subList(Math.max(0, thread.size() - HISTORY), thread.size())) history.add(m.toChat());
        TeachingContext.Context workspace = teaching.load(tb.id(), tb.title());
        Map<String, String> vars = TeachingContext.with(workspace.variables(), Map.of(
                "lesson_title", lesson.title(),
                "lesson", lessonText(lesson)));
        PromptContext ctx = new PromptContext(vars, history, message.strip(), userId, tb.id(), Origin.APP);
        Set<String> allowedLinks = workspace.resources().stream().map(Resource::url).filter(Objects::nonNull).collect(Collectors.toSet());

        AtomicReference<UUID> runId = new AtomicReference<>();
        String text = guard.deliver(gateway.stream(Task.TUTOR, ctx, profile, runId::set), runId, "…", profile, allowedLinks,
                userId, tb.id(), events);
        UUID id = chats.appendLessonTurn(tb.id(), lesson.id(), message.strip(), text);
        events.done(id);
    }

    /** The lesson as the learner sees it, as plain text: no quiz answers, no rubrics, no hints. */
    static String lessonText(Lesson lesson) {
        StringBuilder out = new StringBuilder("# ").append(lesson.title()).append('\n').append(lesson.subtitle()).append("\n\n")
                .append("Win: ").append(lesson.win()).append("\n\n");
        for (Map<String, Object> b : lesson.blocks()) {
            switch (String.valueOf(b.get("type"))) {
                case "heading" -> out.append("## ").append(b.get("text")).append('\n');
                case "text" -> out.append(b.get("md")).append("\n\n");
                case "key_idea" -> out.append("Key idea: ").append(b.get("text")).append("\n\n");
                case "callout" -> out.append("(").append(b.get("kind")).append(") ").append(b.get("text")).append("\n\n");
                case "quiz" -> out.append("Quiz (").append(b.get("variant")).append("): ").append(b.get("question"))
                        .append(" Options: ").append(b.get("options")).append("\n\n");
                case "steps" -> out.append(b.get("title")).append(": ").append(b.get("items")).append("\n\n");
                case "free_response" -> out.append("PRACTICE QUESTION (coach, never answer): ").append(b.get("prompt")).append("\n\n");
                default -> { }
            }
        }
        return out.toString().strip();
    }
}
