package com.laa.owner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.laa.lesson.LessonDraft;
import com.laa.lesson.LessonRules;
import com.laa.llm.GenerationProfile;
import com.laa.llm.ModelInfo;
import com.laa.llm.ModelTier;
import com.laa.llm.PromptContext;
import com.laa.llm.Task;
import com.laa.llm.TaskSchemas;
import com.laa.llm.gateway.DefaultLlmGateway;
import com.laa.llm.gateway.DefaultLlmGateway.Outcome;
import com.laa.llm.prompt.PromptLibrary;
import com.laa.llm.routing.ModelRouter;
import com.laa.llm.routing.ProviderRecord;
import com.laa.llm.routing.RoutingSnapshot;
import com.laa.llm.runs.Origin;
import com.laa.owner.OwnerViews.CompareChecks;
import com.laa.owner.OwnerViews.ModerationCheck;
import com.laa.owner.OwnerViews.CompareRequest;
import com.laa.owner.OwnerViews.CompareResponse;
import com.laa.owner.OwnerViews.CompareSide;
import com.laa.owner.OwnerViews.RouteSpec;
import com.laa.safety.Moderation;
import com.laa.safety.ReadingAge;
import com.laa.web.ApiException;

import tools.jackson.databind.json.JsonMapper;

/**
 * "Compare before switching": the same task and input through two explicit routes (no fallbacks), side
 * by side, with automatic checks: schema validity, the lesson rules (for LESSON_WRITE), reading age, and the safety
 * layer's moderation of the output (as for an adult learner with the default reader settings, which is what the
 * outputs were written for).
 */
@Service
public class CompareService {

    private final DefaultLlmGateway gateway;
    private final ModelRouter router;
    private final PromptLibrary prompts;
    private final OwnerRoutingService routing;
    private final ExecutorService executor;
    private final JsonMapper json;
    private final Moderation moderation;

    public CompareService(DefaultLlmGateway gateway, ModelRouter router, PromptLibrary prompts,
                          OwnerRoutingService routing, @Qualifier("llmExecutor") ExecutorService executor, JsonMapper json,
                          Moderation moderation) {
        this.gateway = gateway;
        this.router = router;
        this.prompts = prompts;
        this.routing = routing;
        this.executor = executor;
        this.json = json;
        this.moderation = moderation;
    }

    public CompareResponse compare(CompareRequest request, UUID ownerId) {
        if (request == null || request.task() == null || request.a() == null || request.b() == null
                || request.input() == null || request.input().message() == null || request.input().message().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Send task, input.message, a and b");
        }
        Task task = request.task();
        RoutingSnapshot snapshot = router.snapshot();
        List<String> errors = new ArrayList<>();
        Resolved a = resolve(task, request.a(), snapshot, "a", errors);
        Resolved b = resolve(task, request.b(), snapshot, "b", errors);
        if (!errors.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Compare rejected", errors);

        Class<?> type = TaskSchemas.typeFor(task).orElse(null);
        PromptContext ctx = new PromptContext(request.input().variables(), List.of(), request.input().message(),
                ownerId, null, Origin.COMPARE).withVariant(TaskSchemas.variantFor(task).orElse(null));
        Future<Outcome<?>> fa = executor.submit(() -> run(task, a, ctx, type));
        Future<Outcome<?>> fb = executor.submit(() -> run(task, b, ctx, type));
        return new CompareResponse(task, type == null ? "text" : "structured:" + type.getSimpleName(),
                side(a, join(fa), type, ownerId), side(b, join(fb), type, ownerId));
    }

    private Outcome<?> run(Task task, Resolved r, PromptContext ctx, Class<?> type) {
        return gateway.runOnce(task, r.spec().provider(), r.model(), r.params(), r.promptVersion(), ctx, type,
                GenerationProfile.ADULT_DEFAULT);
    }

    private CompareSide side(Resolved r, Outcome<?> o, Class<?> type, UUID ownerId) {
        // Only an output can have the wrong shape: a call that failed outright has no verdict.
        Boolean schemaValid = type == null ? null : o.ok() ? Boolean.TRUE
                : o.errorKind() == com.laa.llm.provider.ErrorKind.INVALID_OUTPUT ? Boolean.FALSE : null;
        List<String> schemaErrors = type == null ? List.of() : o.validationErrors();
        tools.jackson.databind.JsonNode parsed = o.value() == null || type == null ? null : json.valueToTree(o.value());
        List<String> quizRules = o.value() instanceof LessonDraft draft ? quizRuleProblems(draft) : null;
        Double readingAge = parsed != null ? ReadingAge.ofJson(parsed) : o.ok() ? ReadingAge.of(o.raw()) : null;
        return new CompareSide(r.spec().provider(), r.model().id(), o.ok(), o.raw(), parsed, o.errorKind(), o.message(),
                o.latencyMs(), o.tokensIn(), o.tokensOut(), o.costEstimate(), o.runId(),
                new CompareChecks(schemaValid, schemaErrors, quizRules, readingAge, moderate(o, parsed, ownerId)));
    }

    /** The output's text (every string in a structured output) through the safety layer. */
    private ModerationCheck moderate(Outcome<?> o, tools.jackson.databind.JsonNode parsed, UUID ownerId) {
        if (!o.ok()) return new ModerationCheck(null, List.of());
        List<String> parts = new ArrayList<>();
        if (parsed != null) strings(parsed, parts);
        else parts.add(o.raw());
        try {
            Moderation.Result r = moderation.check(List.of(new Moderation.Item("output", String.join("\n", parts))),
                    GenerationProfile.ADULT_DEFAULT, Moderation.Kind.OUTPUT, ownerId, null, Origin.COMPARE);
            return new ModerationCheck(r.passed(), r.flags());
        } catch (Moderation.Unavailable e) {
            return new ModerationCheck(null, List.of("The safety check couldn’t run."));
        }
    }

    private static void strings(tools.jackson.databind.JsonNode node, List<String> out) {
        if (node.isString()) out.add(node.asString());
        else if (node.isArray() || node.isObject()) node.values().forEach(v -> strings(v, out));
    }

    /** The lesson rules a stored lesson must pass, as if it were lesson 2 with no sources (warm-up required). */
    private static List<String> quizRuleProblems(LessonDraft draft) {
        return LessonRules.apply(draft, 2, Set.of(), new Random(0), false).problems().stream()
                .map(LessonRules.Problem::message).toList();
    }

    private record Resolved(RouteSpec spec, ModelInfo model, Map<String, Object> params, String promptVersion) {}

    private Resolved resolve(Task task, RouteSpec spec, RoutingSnapshot s, String label, List<String> errors) {
        if (spec.provider() == null) {
            errors.add(label + ": provider is required");
            return null;
        }
        if (!s.usable(spec.provider())) {
            errors.add(label + ": " + s.unusableReason(spec.provider()));
            return null;
        }
        ProviderRecord rec = s.providers().get(spec.provider());
        ModelTier tier = spec.modelTier() == null ? task.defaultTier() : spec.modelTier();
        ModelInfo model = (spec.modelId() != null ? rec.model(spec.modelId()) : rec.modelForTier(tier)).orElse(null);
        if (model == null) {
            errors.add(label + ": " + spec.provider() + " has no " + (spec.modelId() != null ? "model " + spec.modelId() : tier + " model"));
            return null;
        }
        List<String> missing = model.capabilities().shortfallsFor(task);
        if (!missing.isEmpty()) {
            errors.add(label + ": " + task + " needs " + String.join(", ", missing) + ", but " + model.id() + " lacks it");
        }
        Map<String, Object> params = spec.params() == null ? Map.of()
                : routing.parseParams(json.valueToTree(spec.params()), errors);
        String version = spec.promptVersion() == null ? "v1" : spec.promptVersion();
        if (!prompts.exists(task, version)) errors.add(label + ": no prompt version " + version + " for " + task);
        return new Resolved(spec, model, params, version);
    }

    private static Outcome<?> join(Future<Outcome<?>> f) {
        try {
            return f.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Compare interrupted");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException re) throw re;
            throw new IllegalStateException(e.getCause());
        }
    }
}
