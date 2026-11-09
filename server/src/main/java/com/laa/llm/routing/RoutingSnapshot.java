package com.laa.llm.routing;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import com.laa.llm.LlmUnavailableException;
import com.laa.llm.ModelInfo;
import com.laa.llm.ModelTier;
import com.laa.llm.Provider;
import com.laa.llm.Route;
import com.laa.llm.Task;

/**
 * An immutable view of providers + active routes, plus which providers have a client. Resolves routes
 * and answers "what's wrong with this routing?" (capability checks) — used both for live calls and to
 * validate an owner's change before it is saved.
 */
public final class RoutingSnapshot {

    private final Map<Provider, ProviderRecord> providers;
    private final Map<Task, RouteRecord> routes;
    private final Predicate<Provider> hasClient;
    private final Function<Provider, String> noClientReason;

    public RoutingSnapshot(
            Map<Provider, ProviderRecord> providers,
            Map<Task, RouteRecord> routes,
            Predicate<Provider> hasClient,
            Function<Provider, String> noClientReason) {
        this.providers = new EnumMap<>(Provider.class);
        this.providers.putAll(providers);
        this.routes = new EnumMap<>(Task.class);
        this.routes.putAll(routes);
        this.hasClient = hasClient;
        this.noClientReason = noClientReason;
    }

    public Map<Provider, ProviderRecord> providers() {
        return providers;
    }

    public Map<Task, RouteRecord> routes() {
        return routes;
    }

    public Optional<Provider> defaultProvider() {
        return providers.values().stream().filter(ProviderRecord::isDefault).map(ProviderRecord::id).findFirst();
    }

    public boolean isConfigured(Provider p) {
        return hasClient.test(p);
    }

    /** Callable now: has a client and the owner hasn't disabled it. */
    public boolean usable(Provider p) {
        ProviderRecord rec = providers.get(p);
        return rec != null && rec.enabled() && hasClient.test(p);
    }

    public String unusableReason(Provider p) {
        if (!hasClient.test(p)) return noClientReason.apply(p);
        ProviderRecord rec = providers.get(p);
        if (rec == null) return p + " is not registered";
        if (!rec.enabled()) return p + " is disabled";
        return p + " is usable";
    }

    /** The route's primary provider, with DEFAULT resolved. Empty only if DEFAULT and no default is set. */
    public Optional<Provider> primaryProvider(RouteRecord route) {
        return route.followsDefault() ? defaultProvider() : Optional.of(route.providerChoice());
    }

    /** Model for the primary provider: the override if set, else the tier's default. */
    public Optional<ModelInfo> primaryModel(RouteRecord route) {
        return primaryProvider(route).flatMap(p -> modelFor(p, route.tier(), route.modelId()));
    }

    /** Fallback providers use the route's tier (an override names one provider's model, so it doesn't carry over). */
    public Optional<ModelInfo> modelFor(Provider p, ModelTier tier, String overrideId) {
        ProviderRecord rec = providers.get(p);
        if (rec == null) return Optional.empty();
        return overrideId != null ? rec.model(overrideId) : rec.modelForTier(tier);
    }

    /** Resolve the live route for a task. Throws if the task has no usable primary model. */
    public Route resolve(Task task) {
        RouteRecord r = routes.get(task);
        if (r == null) throw new LlmUnavailableException("No active route for " + task);
        Provider provider = primaryProvider(r)
                .orElseThrow(() -> new LlmUnavailableException("No default provider is set"));
        String model = modelFor(provider, r.tier(), r.modelId()).map(ModelInfo::id)
                .orElseThrow(() -> new LlmUnavailableException(
                        task + ": " + provider + " has no model " + (r.modelId() != null ? r.modelId() : "for tier " + r.tier())));
        return new Route(r.id(), task, provider, model, r.tier(), r.params(), r.promptVersion(), r.fallbacks());
    }

    /** Everything wrong with a task's routing (empty = fine). These are the owner-facing capability checks. */
    public List<String> problems(Task task) {
        RouteRecord r = routes.get(task);
        if (r == null) return List.of(task + " has no active route");
        List<String> problems = new ArrayList<>();
        Optional<Provider> primary = primaryProvider(r);
        if (primary.isEmpty()) {
            problems.add(task + " follows the default provider, but none is set");
        } else {
            checkProvider(task, primary.get(), r.tier(), r.modelId(), "primary", problems);
        }
        for (Provider f : r.fallbacks()) {
            if (primary.isPresent() && f == primary.get()) {
                problems.add(task + ": fallback " + f + " is the same as the primary provider");
                continue;
            }
            checkProvider(task, f, r.tier(), null, "fallback", problems);
        }
        return problems;
    }

    public Map<Task, List<String>> allProblems() {
        Map<Task, List<String>> out = new LinkedHashMap<>();
        for (Task t : Task.values()) {
            List<String> p = problems(t);
            if (!p.isEmpty()) out.put(t, p);
        }
        return out;
    }

    private void checkProvider(Task task, Provider p, ModelTier tier, String overrideId, String role, List<String> problems) {
        if (!usable(p)) {
            problems.add(task + ": " + role + " provider " + unusableReason(p));
            return;
        }
        Optional<ModelInfo> model = modelFor(p, tier, overrideId);
        if (model.isEmpty()) {
            problems.add(task + ": " + role + " provider " + p + " has no "
                    + (overrideId != null ? "model \"" + overrideId + "\"" : tier + " model"));
            return;
        }
        List<String> missing = model.get().capabilities().shortfallsFor(task);
        if (!missing.isEmpty()) {
            problems.add(task + " needs " + String.join(", ", missing) + ", but " + role + " model "
                    + model.get().id() + " (" + p + ") lacks it");
        }
    }

    /** Copy with one provider row replaced. */
    public RoutingSnapshot withProvider(ProviderRecord rec) {
        Map<Provider, ProviderRecord> copy = new EnumMap<>(providers);
        copy.put(rec.id(), rec);
        return new RoutingSnapshot(copy, routes, hasClient, noClientReason);
    }

    /** Copy with a different default provider. */
    public RoutingSnapshot withDefault(Provider newDefault) {
        Map<Provider, ProviderRecord> copy = new EnumMap<>(Provider.class);
        providers.forEach((id, rec) -> copy.put(id, rec.withDefault(id == newDefault)));
        return new RoutingSnapshot(copy, routes, hasClient, noClientReason);
    }

    /** Copy with one route replaced. */
    public RoutingSnapshot withRoute(RouteRecord route) {
        Map<Task, RouteRecord> copy = new EnumMap<>(routes);
        copy.put(route.task(), route);
        return new RoutingSnapshot(providers, copy, hasClient, noClientReason);
    }

    /** How many tasks each provider currently serves as primary. */
    public Map<Provider, Integer> primaryTaskCounts() {
        Map<Provider, Integer> counts = new EnumMap<>(Provider.class);
        for (RouteRecord r : routes.values()) {
            primaryProvider(r).ifPresent(p -> counts.merge(p, 1, Integer::sum));
        }
        return counts;
    }
}
