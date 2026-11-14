package com.laa.tutor;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.laa.auth.CurrentUser;
import com.laa.lesson.LessonRepository;
import com.laa.lesson.LessonRepository.Lesson;
import com.laa.profile.Profiles;
import com.laa.safety.SafetyLayer;
import com.laa.textbook.Textbook;
import com.laa.textbook.TextbookRepository;
import com.laa.web.ApiException;
import com.laa.web.Sse;

/**
 * The tutor (BRIEF section 10):
 * <ul>
 *   <li>{@code POST /lessons/{id}/blocks/{blockId}/attempts}: grade a free-response answer; returns the tutor's note</li>
 *   <li>{@code POST /lessons/{id}/blocks/{blockId}/hint}: the next hint on the ladder</li>
 *   <li>{@code POST /textbooks/{id}/tutor/messages}: one tutor-chat turn about lesson {@code lessonSeq}, as server-sent
 *       events: one {@code token} with the whole reply once it has passed the safety check, then {@code done}, or
 *       {@code error}. A question the safety check refuses is a plain 400 before the stream opens.</li>
 *   <li>{@code GET /textbooks/{id}/tutor/messages?lessonSeq=n}: that lesson's chat so far</li>
 * </ul>
 */
@RestController
class TutorController {

    record AttemptRequest(String answer) {}

    record ChatRequest(String content, Integer lessonSeq) {}

    record MessageView(String id, String role, String content) {}

    private final TextbookRepository textbooks;
    private final LessonRepository lessons;
    private final Practice practice;
    private final TutorChat chat;
    private final Profiles profiles;
    private final SafetyLayer safety;
    private final ExecutorService executor;

    TutorController(TextbookRepository textbooks, LessonRepository lessons, Practice practice, TutorChat chat, Profiles profiles,
                    SafetyLayer safety, @Qualifier("llmExecutor") ExecutorService executor) {
        this.textbooks = textbooks;
        this.lessons = lessons;
        this.practice = practice;
        this.chat = chat;
        this.profiles = profiles;
        this.safety = safety;
        this.executor = executor;
    }

    @PostMapping("/lessons/{lessonId}/blocks/{blockId}/attempts")
    Practice.Note attempt(@PathVariable UUID lessonId, @PathVariable String blockId, @RequestBody(required = false) AttemptRequest request,
                          @AuthenticationPrincipal Jwt jwt) {
        UUID userId = CurrentUser.id(jwt);
        Lesson lesson = lesson(lessonId, userId);
        Textbook tb = textbooks.requireForUser(lesson.textbookId(), userId);
        return practice.attempt(tb, lesson, blockId, userId, request == null ? null : request.answer());
    }

    @PostMapping("/lessons/{lessonId}/blocks/{blockId}/hint")
    Practice.HintView hint(@PathVariable UUID lessonId, @PathVariable String blockId, @AuthenticationPrincipal Jwt jwt) {
        UUID userId = CurrentUser.id(jwt);
        return practice.hint(lesson(lessonId, userId), blockId, userId, profiles.forUser(userId));
    }

    @GetMapping("/textbooks/{id}/tutor/messages")
    List<MessageView> thread(@PathVariable UUID id, @RequestParam int lessonSeq, @AuthenticationPrincipal Jwt jwt) {
        Textbook tb = textbooks.requireForUser(id, CurrentUser.id(jwt));
        return chat.thread(lessonBySeq(tb, lessonSeq)).stream()
                .map(m -> new MessageView(m.id().toString(), m.role().name(), m.content())).toList();
    }

    @PostMapping("/textbooks/{id}/tutor/messages")
    SseEmitter message(@PathVariable UUID id, @RequestBody(required = false) ChatRequest request, @AuthenticationPrincipal Jwt jwt) {
        UUID userId = CurrentUser.id(jwt);
        Textbook tb = textbooks.requireForUser(id, userId);
        if (request == null || request.lessonSeq() == null) throw new ApiException(HttpStatus.BAD_REQUEST, "lessonSeq is required");
        Lesson lesson = lessonBySeq(tb, request.lessonSeq());
        chat.check(tb, request.content());
        safety.admit(userId, id, request.content());
        return Sse.stream(executor, "Tutor chat for textbook " + id,
                "Your tutor couldn’t reply just now. Your question wasn’t lost: send it again.",
                sse -> chat.turn(tb, lesson, userId, request.content(), new TutorChat.Events() {
                    @Override
                    public void token(String text) {
                        sse.send("token", Map.of("text", text));
                    }

                    @Override
                    public void done(UUID messageId) {
                        sse.send("done", Map.of("messageId", messageId.toString()));
                    }
                }));
    }

    private Lesson lesson(UUID lessonId, UUID userId) {
        return lessons.findForUser(lessonId, userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No such lesson"));
    }

    private Lesson lessonBySeq(Textbook tb, int seq) {
        return lessons.find(tb.id(), seq).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No such lesson"));
    }
}
