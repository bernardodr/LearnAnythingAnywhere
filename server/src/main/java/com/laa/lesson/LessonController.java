package com.laa.lesson;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.laa.auth.CurrentUser;
import com.laa.job.Jobs;
import com.laa.lesson.LessonDraft.ReferenceSheet;
import com.laa.lesson.LessonRepository.Lesson;
import com.laa.llm.GenerationProfile;
import com.laa.mission.MissionDraft;
import com.laa.textbook.Textbook;
import com.laa.textbook.TextbookRepository;
import com.laa.tutor.Practice;
import com.laa.web.ApiException;
import com.laa.workspace.Resources;
import com.laa.workspace.Resources.Resource;
import com.laa.workspace.WorkspaceFiles;
import com.laa.workspace.WorkspaceFiles.WorkspaceFile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Lessons and the contents page (BRIEF section 10):
 * <ul>
 *   <li>{@code POST /textbooks/{id}/lessons/next}: write the next lesson as a job; progress at {@code GET /jobs/{jobId}/events}</li>
 *   <li>{@code GET /textbooks/{id}/contents}: the contents page, plus the back of the book</li>
 *   <li>{@code GET /textbooks/{id}/lessons/{seq}}: one lesson, rubrics and hints removed</li>
 *   <li>{@code POST /lessons/{id}/read}: the learner finished it, which unlocks the next one</li>
 *   <li>{@code POST /textbooks/{id}/resources}: the learner adds a source to RESOURCES.md</li>
 * </ul>
 */
@RestController
class LessonController {

    record NextResponse(String jobId) {}

    record LessonSummary(String id, int seq, String title, String subtitle, boolean read) {}

    /** The next lesson: written after lesson {@code writtenAfter} is finished; {@code jobId} while it's being written. */
    record NextLesson(int seq, int writtenAfter, boolean canWrite, String jobId) {}

    record SheetView(int seq, String lessonTitle, String title, String md) {}

    record RecordView(String path, String title, String body) {}

    record BackOfBook(List<SheetView> referenceSheets, String glossary, List<RecordView> learned, List<Resource> sources) {}

    record ContentsView(String id, String title, Textbook.Status status, String missionHeadline, String missionWhy,
                        List<LessonSummary> lessons, NextLesson next, BackOfBook back) {}

    /** {@code practice}: per free-response block id, the learner's progress (revealed hints, latest note, met). */
    record LessonView(String id, int seq, String kicker, String title, String subtitle, String win, String missionLink,
                      List<Map<String, Object>> blocks, ReferenceSheet referenceSheet, List<Resource> sources,
                      Map<String, Map<String, Object>> practice, Instant readAt, Integer prev, Integer next, int total) {}

    record SourceRequest(@NotBlank @Size(max = 160) String title, @NotBlank @Size(max = 500) String url,
                         @Size(max = 300) String note) {}

    private final TextbookRepository textbooks;
    private final LessonRepository lessons;
    private final LessonWriter writer;
    private final WorkspaceFiles workspace;
    private final Jobs jobs;
    private final Practice practice;

    LessonController(TextbookRepository textbooks, LessonRepository lessons, LessonWriter writer, WorkspaceFiles workspace,
                     Jobs jobs, Practice practice) {
        this.textbooks = textbooks;
        this.lessons = lessons;
        this.writer = writer;
        this.workspace = workspace;
        this.jobs = jobs;
        this.practice = practice;
    }

    @PostMapping("/textbooks/{id}/lessons/next")
    ResponseEntity<NextResponse> next(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        UUID userId = CurrentUser.id(jwt);
        Textbook tb = textbooks.requireForUser(id, userId);
        if (tb.status() != Textbook.Status.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "Confirm your mission before the first lesson is written.");
        }
        var running = jobs.running(tb.id());
        if (running.isPresent()) return ResponseEntity.accepted().body(new NextResponse(running.get().toString()));
        List<Lesson> written = lessons.list(tb.id());
        if (!written.isEmpty() && written.getLast().readAt() == null) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Finish lesson " + written.getLast().seq() + " first: the next lesson is written from how it went.");
        }
        // Profiles are stored per learner from M6; until then everyone gets the adult defaults.
        UUID jobId = jobs.start(userId, tb.id(), progress -> writer.writeNext(tb, userId, GenerationProfile.ADULT_DEFAULT, progress));
        return ResponseEntity.accepted().body(new NextResponse(jobId.toString()));
    }

    @GetMapping("/textbooks/{id}/contents")
    ContentsView contents(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        Textbook tb = textbooks.requireForUser(id, CurrentUser.id(jwt));
        List<Lesson> written = lessons.list(tb.id());
        List<LessonSummary> summaries = written.stream()
                .map(l -> new LessonSummary(l.id().toString(), l.seq(), l.title(), l.subtitle(), l.readAt() != null)).toList();
        boolean lastRead = written.isEmpty() || written.getLast().readAt() != null;
        String jobId = jobs.running(tb.id()).map(UUID::toString).orElse(null);
        NextLesson next = new NextLesson(written.size() + 1, written.size(),
                tb.status() == Textbook.Status.ACTIVE && lastRead && jobId == null, jobId);

        List<SheetView> sheets = written.stream().filter(l -> l.referenceSheet() != null)
                .map(l -> new SheetView(l.seq(), l.title(), l.referenceSheet().title(), l.referenceSheet().md())).toList();
        List<RecordView> learned = new ArrayList<>();
        for (String path : workspace.list(tb.id())) {
            if (!path.startsWith("learning-records/")) continue;
            String content = workspace.read(tb.id(), path).map(WorkspaceFile::content).orElse("");
            String[] parts = content.strip().split("\n", 2);
            learned.add(new RecordView(path, parts[0].replaceFirst("^#\\s*", ""), parts.length > 1 ? parts[1].strip() : ""));
        }
        String glossary = workspace.read(tb.id(), "GLOSSARY.md").map(WorkspaceFile::content).orElse(null);
        MissionDraft mission = tb.missionDraft();
        return new ContentsView(tb.id().toString(), tb.title(), tb.status(), tb.missionHeadline(),
                mission == null ? null : mission.why(), summaries, next,
                new BackOfBook(sheets, glossary, learned, sources(tb.id())));
    }

    @GetMapping("/textbooks/{id}/lessons/{seq}")
    LessonView lesson(@PathVariable UUID id, @PathVariable int seq, @AuthenticationPrincipal Jwt jwt) {
        UUID userId = CurrentUser.id(jwt);
        Textbook tb = textbooks.requireForUser(id, userId);
        Lesson l = lessons.find(tb.id(), seq).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No such lesson"));
        int total = lessons.list(tb.id()).size();
        Map<String, Map<String, Object>> progress = new java.util.LinkedHashMap<>();
        for (String blockId : l.marking().keySet()) progress.put(blockId, practice.state(l, blockId, userId));
        return new LessonView(l.id().toString(), l.seq(), "Lesson " + l.seq() + " · " + tb.title(), l.title(), l.subtitle(),
                l.win(), l.missionLink(), l.blocks(), l.referenceSheet(), sources(tb.id()), progress, l.readAt(),
                l.seq() > 1 ? l.seq() - 1 : null, l.seq() < total ? l.seq() + 1 : null, total);
    }

    @PostMapping("/lessons/{lessonId}/read")
    Map<String, Object> read(@PathVariable UUID lessonId, @AuthenticationPrincipal Jwt jwt) {
        Lesson l = lessons.findForUser(lessonId, CurrentUser.id(jwt))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No such lesson"));
        return Map.of("seq", l.seq(), "readAt", lessons.markRead(l.id()).toString());
    }

    @PostMapping("/textbooks/{id}/resources")
    ResponseEntity<List<Resource>> addSource(@PathVariable UUID id, @Valid @RequestBody SourceRequest request,
                                             @AuthenticationPrincipal Jwt jwt) {
        Textbook tb = textbooks.requireForUser(id, CurrentUser.id(jwt));
        if (!Resources.isWebUrl(request.url())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "That doesn’t look like a web address (it should start with https://).");
        }
        String current = workspace.read(tb.id(), "RESOURCES.md").map(WorkspaceFile::content).orElse(null);
        workspace.write(tb.id(), "RESOURCES.md", Resources.append(current, tb.title(), request.title(), request.url(), request.note()));
        return ResponseEntity.status(HttpStatus.CREATED).body(sources(tb.id()));
    }

    private List<Resource> sources(UUID textbookId) {
        return Resources.parse(workspace.read(textbookId, "RESOURCES.md").map(WorkspaceFile::content).orElse(null));
    }
}
