package com.laa.job;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.laa.safety.Dashes;
import com.laa.security.SecretRedactor;
import com.laa.web.ApiException;

/**
 * Background jobs with progress over SSE (BRIEF section 10: {@code POST …/lessons/next} returns a job id,
 * {@code GET /jobs/{id}/events} streams it). Every event is kept, so a subscriber that arrives late, or
 * reconnects after a reload, gets the whole history replayed before anything new. One running job per
 * textbook: starting another returns the one already running. Jobs live in memory (prototype): a restart
 * loses running jobs, and nothing is saved until a job finishes, so the learner simply starts again.
 */
@Component
public class Jobs {

    private static final Logger log = LoggerFactory.getLogger(Jobs.class);
    private static final Duration KEEP_FINISHED = Duration.ofMinutes(15);
    private static final long SSE_TIMEOUT_MS = Duration.ofMinutes(10).toMillis();

    public record Event(String name, Object data) {}

    /** Reports progress from inside a job. */
    public interface Progress {
        void stage(String stage, String message);
    }

    /** The work: report progress, return the final {@code done} payload, or throw (the message is shown if it's an ApiException). */
    public interface Work {
        Object run(Progress progress);
    }

    private static final class Job {
        final UUID id = UUID.randomUUID();
        final UUID userId;
        final UUID scope;
        final List<Event> events = new ArrayList<>();
        final List<SseEmitter> subscribers = new ArrayList<>();
        Instant finishedAt;

        Job(UUID userId, UUID scope) {
            this.userId = userId;
            this.scope = scope;
        }
    }

    private final Map<UUID, Job> jobs = new ConcurrentHashMap<>();
    private final ExecutorService executor;

    Jobs(@Qualifier("llmExecutor") ExecutorService executor) {
        this.executor = executor;
    }

    /** Starts {@code work} for {@code scope} (a textbook), or returns the job already running for it. */
    public synchronized UUID start(UUID userId, UUID scope, Work work) {
        forgetOldJobs();
        Optional<Job> running = jobs.values().stream().filter(j -> j.scope.equals(scope) && j.finishedAt == null).findFirst();
        if (running.isPresent()) return running.get().id;
        Job job = new Job(userId, scope);
        jobs.put(job.id, job);
        executor.execute(() -> {
            try {
                // Progress can name the lesson being written (the AI's title), so it gets the em dash filter too (issue #80).
                Object result = work.run((stage, message) -> publish(job, new Event("progress",
                        Map.of("stage", stage, "message", Dashes.replace(message)))));
                publish(job, new Event("done", result));
            } catch (ApiException e) {
                publish(job, new Event("error", Map.of("message", e.getMessage())));
            } catch (RuntimeException e) {
                log.error("Job {} failed: {}", job.id, SecretRedactor.redact(String.valueOf(e.getMessage())), e);
                publish(job, new Event("error", Map.of("message", "Something went wrong while writing. Please try again.")));
            } finally {
                finish(job);
            }
        });
        return job.id;
    }

    public Optional<UUID> running(UUID scope) {
        return jobs.values().stream().filter(j -> j.scope.equals(scope) && j.finishedAt == null).map(j -> j.id).findFirst();
    }

    /** Replays the job's events so far, then streams the rest. Another user's job is a 404. */
    public SseEmitter subscribe(UUID jobId, UUID userId) {
        Job job = jobs.get(jobId);
        if (job == null || !job.userId.equals(userId)) throw new ApiException(HttpStatus.NOT_FOUND, "No such job");
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        synchronized (job) {
            for (Event e : job.events) {
                if (!send(emitter, e)) return emitter;
            }
            if (job.finishedAt != null) emitter.complete();
            else job.subscribers.add(emitter);
        }
        Runnable drop = () -> {
            synchronized (job) {
                job.subscribers.remove(emitter);
            }
        };
        emitter.onCompletion(drop);
        emitter.onTimeout(drop);
        emitter.onError(t -> drop.run());
        return emitter;
    }

    private void publish(Job job, Event event) {
        synchronized (job) {
            job.events.add(event);
            job.subscribers.removeIf(s -> !send(s, event));
        }
    }

    private void finish(Job job) {
        synchronized (job) {
            job.finishedAt = Instant.now();
            job.subscribers.forEach(SseEmitter::complete);
            job.subscribers.clear();
        }
    }

    private static boolean send(SseEmitter emitter, Event event) {
        try {
            emitter.send(SseEmitter.event().name(event.name()).data(event.data(), MediaType.APPLICATION_JSON));
            return true;
        } catch (IOException | IllegalStateException e) {
            return false; // the client went away; the job carries on and stays replayable
        }
    }

    private void forgetOldJobs() {
        Instant cutoff = Instant.now().minus(KEEP_FINISHED);
        jobs.values().removeIf(j -> j.finishedAt != null && j.finishedAt.isBefore(cutoff));
    }
}
