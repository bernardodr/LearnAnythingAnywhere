package com.laa.mission;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.laa.auth.CurrentUser;
import com.laa.llm.GenerationProfile;
import com.laa.llm.LlmUnavailableException;
import com.laa.security.SecretRedactor;
import com.laa.textbook.Textbook;
import com.laa.textbook.TextbookRepository;
import com.laa.web.ApiException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * The preface (BRIEF section 10): {@code POST /textbooks/{id}/mission/messages} streams one interview turn as
 * server-sent events:
 * <ul>
 *   <li>{@code token} {@code {"text": "…"}}: part of the interviewer's reply, in order</li>
 *   <li>{@code draft} {@link MissionDraft}: the interview is complete; show the mission card</li>
 *   <li>{@code draft_failed} {@code {"message": "…"}}: complete, but the draft needs a retry (POST …/mission/draft)</li>
 *   <li>{@code done} {@code {"messageId": "…", "interviewComplete": bool}}: the turn was saved</li>
 *   <li>{@code error} {@code {"message": "…"}}: nothing was saved; the learner can send the same answer again</li>
 * </ul>
 * Problems found before streaming starts (wrong state, empty answer) are ordinary JSON error responses.
 */
@RestController
@RequestMapping("/textbooks/{id}/mission")
class MissionController {

    private static final Logger log = LoggerFactory.getLogger(MissionController.class);
    private static final long SSE_TIMEOUT_MS = Duration.ofMinutes(5).toMillis();

    record TurnRequest(String content) {}

    record MessageView(String id, String role, String content) {}

    record MissionView(Textbook.Status status, String title, String gateNote, List<MessageView> messages, int answers,
                       int maxAnswers, MissionDraft draft) {}

    record ConfirmRequest(@NotNull @Valid MissionDraft draft) {}

    record ConfirmResponse(String path, int version, String content) {}

    /** The client went away mid-stream; stop generating. */
    private static final class ClientGone extends RuntimeException {
        ClientGone(Throwable cause) {
            super(cause);
        }
    }

    private final TextbookRepository textbooks;
    private final MissionInterview interview;
    private final ExecutorService executor;

    MissionController(TextbookRepository textbooks, MissionInterview interview,
                      @Qualifier("llmExecutor") ExecutorService executor) {
        this.textbooks = textbooks;
        this.interview = interview;
        this.executor = executor;
    }

    @GetMapping
    MissionView get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        Textbook tb = textbooks.requireForUser(id, CurrentUser.id(jwt));
        var transcript = interview.transcript(tb);
        Object verdict = tb.topicGate().get("verdict");
        String gateNote = "REFRAME".equals(verdict) ? String.valueOf(tb.topicGate().getOrDefault("message", "")) : null;
        return new MissionView(tb.status(), tb.title(), gateNote,
                transcript.messages().stream().map(m -> new MessageView(m.id().toString(), m.role().name(), m.content())).toList(),
                transcript.answers(), MissionInterview.MAX_ANSWERS, tb.missionDraft());
    }

    @PostMapping("/messages")
    SseEmitter message(@PathVariable UUID id, @RequestBody(required = false) TurnRequest request,
                       @AuthenticationPrincipal Jwt jwt) {
        UUID userId = CurrentUser.id(jwt);
        Textbook tb = textbooks.requireForUser(id, userId);
        String answer = request == null ? null : request.content();
        interview.checkTurn(tb, answer);

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        executor.execute(() -> {
            try {
                // Profiles are stored per learner from M6; until then everyone gets the adult defaults.
                interview.turn(tb, userId, answer, GenerationProfile.ADULT_DEFAULT, new MissionInterview.Events() {
                    @Override
                    public void token(String text) {
                        send(emitter, "token", Map.of("text", text));
                    }

                    @Override
                    public void draft(MissionDraft draft) {
                        send(emitter, "draft", draft);
                    }

                    @Override
                    public void draftFailed(String message) {
                        send(emitter, "draft_failed", Map.of("message", message));
                    }

                    @Override
                    public void done(UUID messageId, boolean interviewComplete) {
                        send(emitter, "done", Map.of("messageId", messageId.toString(), "interviewComplete", interviewComplete));
                    }
                });
                emitter.complete();
            } catch (ClientGone gone) {
                emitter.complete();
            } catch (LlmUnavailableException e) {
                log.warn("Mission turn failed for textbook {}: {}", id, SecretRedactor.redact(e.getMessage()));
                fail(emitter, "The interviewer couldn’t reply just now. Your answer wasn’t lost: send it again.");
            } catch (RuntimeException e) {
                if (causedByClientGone(e)) {
                    emitter.complete();
                    return;
                }
                log.error("Mission turn failed for textbook {}: {}", id, SecretRedactor.redact(String.valueOf(e.getMessage())), e);
                fail(emitter, "Something went wrong. Your answer wasn’t lost: send it again.");
            }
        });
        return emitter;
    }

    /** Regenerates the draft from the transcript (after a {@code draft_failed}, or to try again). */
    @PostMapping("/draft")
    MissionDraft draft(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        UUID userId = CurrentUser.id(jwt);
        Textbook tb = textbooks.requireForUser(id, userId);
        if (tb.status() != Textbook.Status.INTERVIEW) {
            throw new ApiException(HttpStatus.CONFLICT, "This textbook’s mission is already confirmed.");
        }
        return interview.writeDraft(tb, userId, GenerationProfile.ADULT_DEFAULT);
    }

    /** "Write my textbook": the draft as shown (or as the learner edited it) becomes MISSION.md. */
    @PostMapping("/confirm")
    ConfirmResponse confirm(@PathVariable UUID id, @Valid @RequestBody ConfirmRequest request,
                            @AuthenticationPrincipal Jwt jwt) {
        Textbook tb = textbooks.requireForUser(id, CurrentUser.id(jwt));
        var file = interview.confirm(tb, request.draft());
        return new ConfirmResponse(file.path(), file.version(), file.content());
    }

    private static void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            throw new ClientGone(e);
        }
    }

    private static void fail(SseEmitter emitter, String message) {
        try {
            send(emitter, "error", Map.of("message", message));
            emitter.complete();
        } catch (ClientGone ignored) {
            emitter.complete();
        }
    }

    private static boolean causedByClientGone(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof ClientGone) return true;
        }
        return false;
    }
}
