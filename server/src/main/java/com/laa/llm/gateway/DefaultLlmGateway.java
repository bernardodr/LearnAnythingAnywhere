package com.laa.llm.gateway;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.laa.llm.ChatMessage;
import com.laa.llm.GenerationProfile;
import com.laa.llm.LlmGateway;
import com.laa.llm.LlmResult;
import com.laa.llm.LlmUnavailableException;
import com.laa.llm.ModelInfo;
import com.laa.llm.PromptContext;
import com.laa.llm.Provider;
import com.laa.llm.Route;
import com.laa.llm.RunStatus;
import com.laa.llm.StructuredOutputException;
import com.laa.llm.Task;
import com.laa.llm.config.LlmProperties;
import com.laa.llm.prompt.ProfileRules;
import com.laa.llm.prompt.PromptLibrary;
import com.laa.llm.provider.ErrorClassifier;
import com.laa.llm.provider.ErrorKind;
import com.laa.llm.provider.ProviderCallException;
import com.laa.llm.provider.ProviderClient;
import com.laa.llm.provider.ProviderClients;
import com.laa.llm.provider.ProviderReply;
import com.laa.llm.provider.ProviderRequest;
import com.laa.llm.routing.ModelRouter;
import com.laa.llm.routing.RoutingSnapshot;
import com.laa.llm.runs.ModelRunRepository;
import com.laa.llm.runs.Origin;
import com.laa.llm.runs.RunRecord;
import com.laa.llm.structured.StructuredOutputs;
import com.laa.llm.structured.StructuredOutputs.OutputSchema;
import com.laa.llm.structured.StructuredOutputs.Validation;
import com.laa.security.SecretRedactor;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * The gateway. Per call: resolve the route, build the system prompt (task prompt + profile rules), then
 * walk the provider chain (primary, then fallbacks) until one answers. Every provider attempt writes one
 * {@code model_runs} row: ERROR for a failed attempt, OK for a primary success, FALLBACK when a later
 * provider in the chain answered.
 */
@Service
public class DefaultLlmGateway implements LlmGateway {

    private static final Logger log = LoggerFactory.getLogger(DefaultLlmGateway.class);
    private static final String TIMEOUT_MS = "timeoutMs";

    private final ModelRouter router;
    private final ProviderClients clients;
    private final PromptLibrary prompts;
    private final StructuredOutputs outputs;
    private final ModelRunRepository runs;
    private final LlmProperties props;
    private final ExecutorService executor;
    private final Scheduler scheduler;

    public DefaultLlmGateway(ModelRouter router, ProviderClients clients, PromptLibrary prompts,
                             StructuredOutputs outputs, ModelRunRepository runs, LlmProperties props,
                             @Qualifier("llmExecutor") ExecutorService executor) {
        this.router = router;
        this.clients = clients;
        this.prompts = prompts;
        this.outputs = outputs;
        this.runs = runs;
        this.props = props;
        this.executor = executor;
        this.scheduler = Schedulers.fromExecutorService(executor, "llm");
    }

    // ---------------------------------------------------------------- structured

    @Override
    public <T> LlmResult<T> structuredResult(Task task, PromptContext ctx, Class<T> type, GenerationProfile profile) {
        RoutingSnapshot snapshot = router.snapshot();
        Route route = snapshot.resolve(task);
        OutputSchema<T> schema = outputs.forType(type);
        Call call = new Call(task, route, systemPrompt(task, route.promptVersion(), ctx, profile), messages(ctx),
                ctx.userId(), ctx.textbookId(), ctx.origin());

        List<String> failures = new ArrayList<>();
        List<Step> chain = chain(snapshot, route);
        for (int i = 0; i < chain.size(); i++) {
            Step step = chain.get(i);
            if (step.unusableReason() != null) {
                failures.add(step.unusableReason());
                continue;
            }
            Outcome<T> outcome = attemptStructured(call, step, schema, i > 0);
            if (outcome.ok()) {
                return new LlmResult<>(outcome.value(), outcome.runId(), step.provider(), step.model().id(), outcome.status());
            }
            if (outcome.errorKind() == ErrorKind.INVALID_OUTPUT) {
                throw new StructuredOutputException(task + ": " + step.provider() + " (" + step.model().id()
                        + ") returned output that failed validation twice", outcome.validationErrors());
            }
            failures.add(step.provider() + " " + outcome.errorKind() + ": " + outcome.message());
        }
        throw new LlmUnavailableException(task + ": no provider answered. " + String.join("; ", failures));
    }

    // ---------------------------------------------------------------- streaming

    @Override
    public Flux<String> stream(Task task, PromptContext ctx, GenerationProfile profile) {
        return Flux.defer(() -> {
            RoutingSnapshot snapshot = router.snapshot();
            Route route = snapshot.resolve(task);
            Call call = new Call(task, route, systemPrompt(task, route.promptVersion(), ctx, profile), messages(ctx),
                    ctx.userId(), ctx.textbookId(), ctx.origin());
            return streamFrom(call, chain(snapshot, route), 0, new ArrayList<>());
        });
    }

    private Flux<String> streamFrom(Call call, List<Step> chain, int index, List<String> failures) {
        if (index >= chain.size()) {
            return Flux.error(new LlmUnavailableException(
                    call.task() + ": no provider answered. " + String.join("; ", failures)));
        }
        Step step = chain.get(index);
        if (step.unusableReason() != null) {
            failures.add(step.unusableReason());
            return streamFrom(call, chain, index + 1, failures);
        }
        return Flux.defer(() -> {
            long started = System.nanoTime();
            AtomicBoolean emitted = new AtomicBoolean();
            AtomicBoolean recorded = new AtomicBoolean();
            AtomicReference<Integer> tokensIn = new AtomicReference<>();
            AtomicReference<Integer> tokensOut = new AtomicReference<>();
            Duration idle = timeout(call.route().params());
            ProviderRequest request = new ProviderRequest(call.task(), step.model().id(), call.system(),
                    call.messages(), call.route().params(), null);

            Runnable onComplete = () -> {
                if (recorded.compareAndSet(false, true)) {
                    record(call, step, index > 0 ? RunStatus.FALLBACK : RunStatus.OK, null, null,
                            tokensIn.get(), tokensOut.get(), started);
                }
            };
            // Some adapters do blocking work when the stream is subscribed; do that on a virtual thread so the
            // first-token timeout below is armed immediately and can't be held up by it.
            return Flux.defer(() -> step.client().stream(request))
                    .subscribeOn(scheduler)
                    .timeout(Mono.delay(props.firstTokenTimeout()), chunk -> Mono.delay(idle))
                    .publishOn(scheduler) // run-log writes block; keep them off provider/timer threads
                    .doOnNext(chunk -> {
                        if (!chunk.text().isEmpty()) emitted.set(true);
                        if (chunk.tokensIn() != null) tokensIn.set(chunk.tokensIn());
                        if (chunk.tokensOut() != null) tokensOut.set(chunk.tokensOut());
                    })
                    .map(ProviderReply::text)
                    .filter(text -> !text.isEmpty())
                    .doOnComplete(onComplete)
                    .doOnCancel(() -> {
                        if (recorded.compareAndSet(false, true)) {
                            record(call, step, RunStatus.ERROR, ErrorKind.CANCELLED, "Client cancelled the stream",
                                    tokensIn.get(), tokensOut.get(), started);
                        }
                    })
                    .onErrorResume(error -> {
                        ErrorKind kind = ErrorClassifier.classify(error);
                        String message = kind == ErrorKind.TIMEOUT && error instanceof TimeoutException
                                ? step.provider() + " stopped streaming (timeout)" : describe(error);
                        if (recorded.compareAndSet(false, true)) {
                            record(call, step, RunStatus.ERROR, kind, message, tokensIn.get(), tokensOut.get(), started);
                        }
                        log.warn("{} stream via {} failed: {} {}", call.task(), step.provider(), kind, message);
                        if (!emitted.get()) {
                            failures.add(step.provider() + " " + kind + ": " + message);
                            return streamFrom(call, chain, index + 1, failures);
                        }
                        return Flux.error(new LlmUnavailableException(
                                call.task() + ": " + step.provider() + " failed mid-stream (" + kind + ")"));
                    });
        });
    }

    // ---------------------------------------------------------------- single attempt (owner compare)

    /** What one attempt produced. {@code value} is set only for a valid structured result. */
    public record Outcome<T>(
            T value,
            String raw,
            UUID runId,
            RunStatus status,
            ErrorKind errorKind,
            String message,
            List<String> validationErrors,
            Integer tokensIn,
            Integer tokensOut,
            BigDecimal costEstimate,
            long latencyMs) {

        public boolean ok() {
            return status == RunStatus.OK || status == RunStatus.FALLBACK;
        }
    }

    /**
     * Run a task once on an explicit provider and model, with no fallbacks (used by the owner's compare).
     * {@code type} null means a plain-text call. Recorded with origin COMPARE.
     */
    public Outcome<?> runOnce(Task task, Provider provider, ModelInfo model, Map<String, Object> params,
                              String promptVersion, PromptContext ctx, Class<?> type, GenerationProfile profile) {
        ProviderClient client = clients.client(provider)
                .orElseThrow(() -> new LlmUnavailableException(clients.unavailableReason(provider)));
        Route route = new Route(null, task, provider, model.id(), model.tier(), params, promptVersion, List.of());
        Call call = new Call(task, route, systemPrompt(task, promptVersion, ctx, profile), messages(ctx),
                ctx.userId(), ctx.textbookId(), Origin.COMPARE);
        Step step = new Step(provider, model, client, null, 1);
        return type == null ? attemptText(call, step) : attemptStructured(call, step, outputs.forType(type), false);
    }

    private Outcome<String> attemptText(Call call, Step step) {
        long started = System.nanoTime();
        try {
            ProviderReply reply = callWithTimeout(step.client(), new ProviderRequest(call.task(), step.model().id(),
                    call.system(), call.messages(), call.route().params(), null), timeout(call.route().params()));
            UUID runId = record(call, step, RunStatus.OK, null, null, reply.tokensIn(), reply.tokensOut(), started);
            return new Outcome<>(reply.text(), reply.text(), runId, RunStatus.OK, null, null, List.of(),
                    reply.tokensIn(), reply.tokensOut(), cost(step.model(), reply.tokensIn(), reply.tokensOut()), elapsed(started));
        } catch (RuntimeException e) {
            return failed(call, step, e, started, null, null);
        }
    }

    // ---------------------------------------------------------------- internals

    private <T> Outcome<T> attemptStructured(Call call, Step step, OutputSchema<T> schema, boolean isFallback) {
        long started = System.nanoTime();
        Integer in = null;
        Integer out = null;
        // Models without native JSON-schema support get the schema as instructions instead.
        String system = step.model().capabilities().structuredOutput()
                ? call.system() : call.system() + "\n\n" + schema.formatInstructions();
        Duration timeout = timeout(call.route().params());
        try {
            ProviderReply reply = callWithTimeout(step.client(), new ProviderRequest(call.task(), step.model().id(),
                    system, call.messages(), call.route().params(), schema.json()), timeout);
            in = add(in, reply.tokensIn());
            out = add(out, reply.tokensOut());
            Validation<T> validation = outputs.validate(schema, reply.text());

            if (!validation.ok()) {
                // One corrective retry with the validation errors appended.
                List<ChatMessage> retry = new ArrayList<>(call.messages());
                retry.add(ChatMessage.assistant(reply.text()));
                retry.add(ChatMessage.user(correction(validation.errors())));
                reply = callWithTimeout(step.client(), new ProviderRequest(call.task(), step.model().id(),
                        system, retry, call.route().params(), schema.json()), timeout);
                in = add(in, reply.tokensIn());
                out = add(out, reply.tokensOut());
                validation = outputs.validate(schema, reply.text());
                if (!validation.ok()) {
                    String message = "Output failed validation after one retry: " + String.join("; ", validation.errors());
                    UUID runId = record(call, step, RunStatus.ERROR, ErrorKind.INVALID_OUTPUT, message, in, out, started);
                    log.warn("{} via {} ({}): {}", call.task(), step.provider(), step.model().id(), message);
                    return new Outcome<>(null, reply.text(), runId, RunStatus.ERROR, ErrorKind.INVALID_OUTPUT, message,
                            validation.errors(), in, out, cost(step.model(), in, out), elapsed(started));
                }
            }
            RunStatus status = isFallback ? RunStatus.FALLBACK : RunStatus.OK;
            UUID runId = record(call, step, status, null, null, in, out, started);
            return new Outcome<>(validation.value(), reply.text(), runId, status, null, null, List.of(),
                    in, out, cost(step.model(), in, out), elapsed(started));
        } catch (RuntimeException e) {
            return failed(call, step, e, started, in, out);
        }
    }

    private <T> Outcome<T> failed(Call call, Step step, RuntimeException e, long started, Integer in, Integer out) {
        ErrorKind kind = ErrorClassifier.classify(e);
        String message = describe(e);
        UUID runId = record(call, step, RunStatus.ERROR, kind, message, in, out, started);
        log.warn("{} via {} ({}) failed: {} {}", call.task(), step.provider(), step.model().id(), kind, message);
        return new Outcome<>(null, null, runId, RunStatus.ERROR, kind, message, List.of(), in, out,
                cost(step.model(), in, out), elapsed(started));
    }

    /** Runs the blocking provider call on a virtual thread so the gateway can enforce its own timeout. */
    private ProviderReply callWithTimeout(ProviderClient client, ProviderRequest request, Duration timeout) {
        Future<ProviderReply> future = executor.submit(() -> client.call(request));
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new ProviderCallException(ErrorKind.TIMEOUT,
                    client.provider() + " did not answer within " + timeout.toMillis() + " ms");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new ProviderCallException(ErrorClassifier.classify(cause), describe(cause), ErrorClassifier.statusOf(cause));
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new ProviderCallException(ErrorKind.CANCELLED, "Interrupted while waiting for " + client.provider());
        }
    }

    /** Primary first, then fallbacks (deduplicated). Unusable providers stay in the chain with a reason, and are skipped. */
    private List<Step> chain(RoutingSnapshot snapshot, Route route) {
        List<Step> chain = new ArrayList<>();
        List<Provider> order = new ArrayList<>();
        order.add(route.provider());
        for (Provider f : route.fallbacks()) if (!order.contains(f)) order.add(f);
        for (int i = 0; i < order.size(); i++) {
            Provider p = order.get(i);
            ModelInfo model = (i == 0
                    ? snapshot.modelFor(p, route.tier(), route.model())
                    : snapshot.modelFor(p, route.tier(), null)).orElse(null);
            String reason = null;
            if (!snapshot.usable(p)) reason = snapshot.unusableReason(p);
            else if (model == null) reason = p + " has no " + route.tier() + " model";
            ProviderClient client = clients.client(p).orElse(null);
            chain.add(new Step(p, model, client, reason, i + 1));
        }
        return chain;
    }

    private String systemPrompt(Task task, String version, PromptContext ctx, GenerationProfile profile) {
        GenerationProfile p = profile == null ? GenerationProfile.ADULT_DEFAULT : profile;
        return prompts.render(task, ctx.promptVariant(), version, ctx.variables()) + "\n\n" + ProfileRules.render(p);
    }

    private static List<ChatMessage> messages(PromptContext ctx) {
        List<ChatMessage> messages = new ArrayList<>(ctx.history());
        if (ctx.userMessage() != null && !ctx.userMessage().isBlank()) messages.add(ChatMessage.user(ctx.userMessage()));
        return messages;
    }

    private static String correction(List<String> errors) {
        return "Your previous reply did not match the required JSON schema:\n- " + String.join("\n- ", errors)
                + "\nReply again with only the corrected JSON object.";
    }

    private Duration timeout(Map<String, Object> params) {
        return params.get(TIMEOUT_MS) instanceof Number n && n.longValue() > 0
                ? Duration.ofMillis(n.longValue()) : props.callTimeout();
    }

    private UUID record(Call call, Step step, RunStatus status, ErrorKind kind, String message,
                        Integer in, Integer out, long started) {
        return runs.insert(new RunRecord(call.task(), step.provider(), step.model().id(), call.route().promptVersion(),
                call.route().routeId(), call.origin(), step.attempt(), in, out, cost(step.model(), in, out),
                elapsed(started), status, kind, message, call.userId(), call.textbookId()));
    }

    static BigDecimal cost(ModelInfo model, Integer in, Integer out) {
        if (model.inputPerMTok() == null && model.outputPerMTok() == null) return null;
        if (in == null && out == null) return null;
        BigDecimal million = BigDecimal.valueOf(1_000_000);
        BigDecimal total = BigDecimal.ZERO;
        if (in != null && model.inputPerMTok() != null) total = total.add(model.inputPerMTok().multiply(BigDecimal.valueOf(in)));
        if (out != null && model.outputPerMTok() != null) total = total.add(model.outputPerMTok().multiply(BigDecimal.valueOf(out)));
        return total.divide(million, MathContext.DECIMAL64).setScale(6, java.math.RoundingMode.HALF_UP);
    }

    private static Integer add(Integer a, Integer b) {
        if (a == null) return b;
        if (b == null) return a;
        return a + b;
    }

    private static long elapsed(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    /** Short, redacted description of a failure, safe for logs, the DB and API responses. */
    static String describe(Throwable e) {
        if (e instanceof ProviderCallException p) return SecretRedactor.redact(p.getMessage());
        String message = e.getMessage() == null ? "" : e.getMessage();
        int nl = message.indexOf('\n');
        if (nl >= 0) message = message.substring(0, nl);
        if (message.length() > 300) message = message.substring(0, 299) + "…";
        return SecretRedactor.redact(e.getClass().getSimpleName() + (message.isEmpty() ? "" : ": " + message));
    }

    private record Call(Task task, Route route, String system, List<ChatMessage> messages,
                        UUID userId, UUID textbookId, Origin origin) {}

    private record Step(Provider provider, ModelInfo model, ProviderClient client, String unusableReason, int attempt) {}
}
