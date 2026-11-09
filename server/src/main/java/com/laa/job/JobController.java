package com.laa.job;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.laa.auth.CurrentUser;

/** {@code GET /jobs/{id}/events}: a job's progress as server-sent events ({@code progress}, then {@code done} or {@code error}). */
@RestController
class JobController {

    private final Jobs jobs;

    JobController(Jobs jobs) {
        this.jobs = jobs;
    }

    @GetMapping("/jobs/{id}/events")
    SseEmitter events(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return jobs.subscribe(id, CurrentUser.id(jwt));
    }
}
