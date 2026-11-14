package com.laa.dev;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.laa.auth.CurrentUser;
import com.laa.llm.GenerationProfile;
import com.laa.llm.LlmGateway;
import com.laa.llm.LlmResult;
import com.laa.llm.PromptContext;
import com.laa.llm.Provider;
import com.laa.llm.StructuredCall;
import com.laa.llm.Task;
import com.laa.llm.TextCall;
import com.laa.llm.provider.fake.FailureMode;
import com.laa.llm.provider.fake.FakeBehaviour;
import com.laa.llm.runs.ModelRun;
import com.laa.llm.runs.ModelRunRepository;
import com.laa.llm.runs.Origin;
import com.laa.web.ApiException;

import jakarta.validation.constraints.NotBlank;

/**
 * Dev-only smoke endpoints (OWNER role; off unless laa.dev.endpoints-enabled=true):
 * call the gateway for any task, and make a fake provider fail on purpose.
 */
@RestController
@RequestMapping("/dev")
@ConditionalOnProperty(name = "laa.dev.endpoints-enabled", havingValue = "true")
class DevController {

    /** Minimal structured output for smoke calls. */
    record PingReply(@NotBlank String message) {}

    record PingRequest(Task task, String message, Boolean stream) {}

    record PingResponse(Task task, String mode, Provider provider, String model, String status, UUID runId, Object output) {}

    record FailureRequest(FailureMode mode, Long hangMs) {}

    private final LlmGateway gateway;
    private final FakeBehaviour fakes;
    private final ModelRunRepository runs;

    DevController(LlmGateway gateway, FakeBehaviour fakes, ModelRunRepository runs) {
        this.gateway = gateway;
        this.fakes = fakes;
        this.runs = runs;
    }

    @PostMapping("/llm/ping")
    PingResponse ping(@RequestBody PingRequest request, @AuthenticationPrincipal Jwt jwt) {
        if (request == null || request.task() == null) throw new ApiException(HttpStatus.BAD_REQUEST, "task is required");
        String message = request.message() == null || request.message().isBlank()
                ? "Reply with a one-sentence greeting." : request.message();
        PromptContext ctx = PromptContext.of(message).forUser(CurrentUser.id(jwt)).withOrigin(Origin.DEV);

        // A dev-only call, deliberately not registered in Calls: any task's main prompt, a generic reply.
        if (Boolean.TRUE.equals(request.stream())) {
            TextCall call = new TextCall(request.task(), null, "Dev ping");
            List<String> chunks = gateway.stream(call, ctx, GenerationProfile.ADULT_DEFAULT).text()
                    .collectList().block(Duration.ofMinutes(5));
            // Streams don't return run metadata; the newest run for this task is this call's final attempt.
            ModelRun run = runs.recent(request.task(), null, 1).stream().findFirst().orElseThrow();
            return new PingResponse(request.task(), "stream", Provider.valueOf(run.provider()), run.model(),
                    run.status(), run.id(), String.join("", chunks == null ? List.of() : chunks));
        }
        StructuredCall<PingReply> call = new StructuredCall<>(request.task(), null, PingReply.class, "Dev ping");
        LlmResult<PingReply> result = gateway.structuredResult(call, ctx, GenerationProfile.ADULT_DEFAULT);
        return new PingResponse(request.task(), "structured", result.provider(), result.model(),
                result.status().name(), result.runId(), result.value());
    }

    @GetMapping("/fake")
    Map<Provider, FailureMode> fakeModes() {
        Map<Provider, FailureMode> out = new EnumMap<>(Provider.class);
        out.put(Provider.FAKE, fakes.mode(Provider.FAKE));
        out.put(Provider.FAKE_B, fakes.mode(Provider.FAKE_B));
        return out;
    }

    @PutMapping("/fake/{provider}/failure")
    Map<Provider, FailureMode> setFailure(@PathVariable String provider, @RequestBody FailureRequest request) {
        Provider p;
        try {
            p = Provider.valueOf(provider.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Unknown provider " + provider);
        }
        if (!p.isFake()) throw new ApiException(HttpStatus.BAD_REQUEST, p + " is not a fake provider");
        if (request == null || request.mode() == null) throw new ApiException(HttpStatus.BAD_REQUEST, "mode is required");
        fakes.set(p, request.mode());
        if (request.hangMs() != null) fakes.setHang(Duration.ofMillis(request.hangMs()));
        return fakeModes();
    }
}
