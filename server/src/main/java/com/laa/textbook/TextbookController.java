package com.laa.textbook;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import com.laa.auth.CurrentUser;
import com.laa.profile.ProfileRepository.Stored;
import com.laa.profile.Profiles;
import com.laa.safety.TopicGate;
import com.laa.safety.TopicGate.Decision;
import com.laa.web.ApiException;
import com.laa.workspace.WorkspaceFiles;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The library (BRIEF section 10): list (in the learner's own shelf order), arrange, create through the topic gate,
 * read one, read its workspace files.
 */
@RestController
@RequestMapping("/textbooks")
class TextbookController {

    record CreateRequest(@NotBlank @Size(max = 120) String topic) {}

    record ArrangeRequest(@NotNull @Size(max = 1000) List<@NotNull UUID> ids) {}

    /**
     * {@code gateNote} is the topic gate's message when the topic was reframed, shown at the top of the preface.
     * {@code coverMotif} is always set: the AI's pick, or one matched from the title until the preface ends.
     */
    record TextbookView(String id, String title, String topic, Textbook.Status status, String coverColor, String coverMotif,
                        int volume, String missionHeadline, String gateNote, Instant createdAt) {
        static TextbookView of(Textbook t) {
            Object verdict = t.topicGate().get("verdict");
            String note = "REFRAME".equals(verdict) ? String.valueOf(t.topicGate().getOrDefault("message", "")) : null;
            return new TextbookView(t.id().toString(), t.title(), t.topic(), t.status(), t.coverColor(), t.motif().key(), t.volume(),
                    t.missionHeadline(), note, t.createdAt());
        }
    }

    /** {@code textbook} is null when the gate declined; {@code message} is then the kind note to show. */
    record CreateResponse(String verdict, String message, TextbookView textbook) {}

    record FileView(String path, String content, int version) {}

    private final TextbookRepository textbooks;
    private final TopicGate gate;
    private final WorkspaceFiles workspace;
    private final Profiles profiles;

    TextbookController(TextbookRepository textbooks, TopicGate gate, WorkspaceFiles workspace, Profiles profiles) {
        this.textbooks = textbooks;
        this.gate = gate;
        this.workspace = workspace;
        this.profiles = profiles;
    }

    @GetMapping
    List<TextbookView> library(@AuthenticationPrincipal Jwt jwt) {
        return textbooks.listForUser(CurrentUser.id(jwt)).stream().map(TextbookView::of).toList();
    }

    /** The learner's new shelf order, first to last. */
    @PutMapping("/order")
    ResponseEntity<Void> arrange(@Valid @RequestBody ArrangeRequest request, @AuthenticationPrincipal Jwt jwt) {
        if (request.ids().stream().distinct().count() != request.ids().size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Each book can only be in one place.");
        }
        textbooks.arrange(CurrentUser.id(jwt), request.ids());
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    ResponseEntity<CreateResponse> create(@Valid @RequestBody CreateRequest request, @AuthenticationPrincipal Jwt jwt) {
        UUID userId = CurrentUser.id(jwt);
        String topic = request.topic().replaceAll("\\s+", " ").strip();
        if (topic.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Tell us what you’d like to learn.");
        Stored profile = profiles.stored(userId);
        Decision decision;
        try {
            decision = gate.check(topic, userId, profile.settings());
        } catch (TopicGate.UnavailableException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        }
        if (!decision.allowsCreation()) {
            return ResponseEntity.ok(new CreateResponse(decision.verdict().name(), decision.message(), null));
        }
        Textbook created = textbooks.create(userId, decision.title(), topic, decision.asJson(), profile.id());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreateResponse(decision.verdict().name(), decision.message(), TextbookView.of(created)));
    }

    @GetMapping("/{id}")
    TextbookView one(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return TextbookView.of(require(id, jwt));
    }

    /** {@code GET /textbooks/{id}/files/MISSION.md}, {@code …/files/learning-records/0001-x.md}. */
    @GetMapping("/{id}/files/**")
    FileView file(@PathVariable UUID id, HttpServletRequest request, @AuthenticationPrincipal Jwt jwt) {
        Textbook tb = require(id, jwt);
        String full = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String path = full.substring(full.indexOf("/files/") + "/files/".length());
        if (!WorkspaceFiles.isValidPath(path)) throw new ApiException(HttpStatus.NOT_FOUND, "No such file");
        return workspace.read(tb.id(), path).map(f -> new FileView(f.path(), f.content(), f.version()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No such file"));
    }

    private Textbook require(UUID id, Jwt jwt) {
        return textbooks.requireForUser(id, CurrentUser.id(jwt));
    }
}
