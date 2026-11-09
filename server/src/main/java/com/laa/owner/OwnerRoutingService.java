package com.laa.owner;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.laa.llm.ModelInfo;
import com.laa.llm.ModelTier;
import com.laa.llm.Provider;
import com.laa.llm.Task;
import com.laa.llm.prompt.PromptLibrary;
import com.laa.llm.routing.ModelRouter;
import com.laa.llm.routing.ProviderRecord;
import com.laa.llm.routing.RouteRecord;
import com.laa.llm.routing.RoutingRepository;
import com.laa.llm.routing.RoutingSnapshot;
import com.laa.owner.OwnerViews.ModelView;
import com.laa.owner.OwnerViews.ProviderPatch;
import com.laa.owner.OwnerViews.ProviderView;
import com.laa.owner.OwnerViews.RouteView;
import com.laa.web.ApiException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Owner changes to providers and routes. Every change is applied to an in-memory copy of the routing
 * first and checked; a change that would leave a task on a model without what it needs is rejected
 * (HTTP 400) before anything is written. On success the router cache is cleared, so the next call
 * uses the new routing.
 */
@Service
public class OwnerRoutingService {

    /** Route params the gateway understands. */
    static final Set<String> ALLOWED_PARAMS = Set.of("temperature", "maxTokens", "topP", "timeoutMs");
    private static final Set<String> ROUTE_FIELDS =
            Set.of("providerChoice", "modelTier", "modelId", "params", "promptVersion", "fallbacks");

    private final ModelRouter router;
    private final RoutingRepository repository;
    private final PromptLibrary prompts;
    private final JsonMapper json;
    private final TransactionTemplate tx;

    public OwnerRoutingService(ModelRouter router, RoutingRepository repository, PromptLibrary prompts,
                               JsonMapper json, TransactionTemplate tx) {
        this.router = router;
        this.repository = repository;
        this.prompts = prompts;
        this.json = json;
        this.tx = tx;
    }

    public List<ProviderView> providers() {
        RoutingSnapshot s = router.loadFresh();
        Map<Provider, Integer> counts = s.primaryTaskCounts();
        List<ProviderView> out = new ArrayList<>();
        for (Provider p : Provider.values()) {
            ProviderRecord rec = s.providers().get(p);
            if (rec == null) continue;
            out.add(new ProviderView(p, p.label(), s.isConfigured(p), rec.enabled(), rec.isDefault(), s.usable(p),
                    s.usable(p) ? null : s.unusableReason(p), counts.getOrDefault(p, 0),
                    rec.models().stream().map(OwnerRoutingService::view).toList()));
        }
        return out;
    }

    public ProviderView patchProvider(Provider id, ProviderPatch patch) {
        RoutingSnapshot current = router.loadFresh();
        ProviderRecord rec = current.providers().get(id);
        if (rec == null) throw new ApiException(HttpStatus.NOT_FOUND, "Unknown provider " + id);
        if (patch == null || (patch.enabled() == null && patch.isDefault() == null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Nothing to change: send enabled and/or isDefault");
        }

        RoutingSnapshot candidate = current;
        List<String> errors = new ArrayList<>();
        if (patch.enabled() != null) {
            candidate = candidate.withProvider(candidate.providers().get(id).withEnabled(patch.enabled()));
            if (!patch.enabled() && rec.isDefault() && !Boolean.TRUE.equals(patch.isDefault())) {
                errors.add(id + " is the default provider; make another provider the default before disabling it");
            }
        }
        if (patch.isDefault() != null) {
            if (!patch.isDefault()) {
                errors.add("To change the default, set isDefault=true on the provider that should become the default");
            } else {
                candidate = candidate.withDefault(id);
                if (!candidate.usable(id)) errors.add("Cannot make " + id + " the default: " + candidate.unusableReason(id));
            }
        }
        errors.addAll(introducedProblems(current, candidate));
        if (!errors.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Provider change rejected", errors);

        tx.executeWithoutResult(status -> {
            if (patch.enabled() != null) repository.setEnabled(id, patch.enabled());
            if (Boolean.TRUE.equals(patch.isDefault())) repository.setDefault(id);
        });
        router.invalidate(); // after commit, so nobody can re-cache the old routing

        return providers().stream().filter(v -> v.id() == id).findFirst().orElseThrow();
    }

    public List<RouteView> routes() {
        RoutingSnapshot s = router.loadFresh();
        List<RouteView> out = new ArrayList<>();
        for (Task t : Task.values()) {
            RouteRecord r = s.routes().get(t);
            if (r != null) out.add(view(s, r));
        }
        return out;
    }

    /**
     * Partial update. Fields: providerChoice ("DEFAULT" or a provider), modelTier, modelId (null clears the
     * override; setting modelTier alone also clears it), params, promptVersion, fallbacks.
     */
    public RouteView patchRoute(Task task, JsonNode body) {
        if (body == null || !body.isObject() || body.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Send a JSON object with the fields to change");
        }
        List<String> errors = new ArrayList<>();
        for (Iterator<String> it = body.propertyNames().iterator(); it.hasNext(); ) {
            String field = it.next();
            if (!ROUTE_FIELDS.contains(field)) errors.add("Unknown field \"" + field + "\"; allowed: " + ROUTE_FIELDS);
        }
        if (!errors.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Route change rejected", errors);

        RoutingSnapshot current = router.loadFresh();
        RouteRecord r = current.routes().get(task);
        if (r == null) throw new ApiException(HttpStatus.NOT_FOUND, "No active route for " + task);

        Provider choice = r.providerChoice();
        if (body.has("providerChoice")) {
            String v = body.get("providerChoice").asString("");
            choice = "DEFAULT".equals(v) ? null : parseProvider(v, "providerChoice", errors);
        }
        ModelTier tier = r.tier();
        String modelId = r.modelId();
        if (body.has("modelTier")) {
            tier = parseEnum(ModelTier.class, body.get("modelTier").asString(""), "modelTier", errors, tier);
            modelId = null;
        }
        if (body.has("providerChoice") && !body.has("modelId")) modelId = null; // an override names one provider's model
        if (body.has("modelId")) {
            JsonNode m = body.get("modelId");
            modelId = m.isNull() || m.asString("").isBlank() ? null : m.asString();
        }
        Map<String, Object> params = r.params();
        if (body.has("params")) params = parseParams(body.get("params"), errors);
        String promptVersion = r.promptVersion();
        if (body.has("promptVersion")) {
            promptVersion = body.get("promptVersion").asString("");
            if (!prompts.exists(task, promptVersion)) {
                errors.add("No prompt file prompts/" + task.promptStem() + "." + promptVersion + ".md");
            }
        }
        List<Provider> fallbacks = r.fallbacks();
        if (body.has("fallbacks")) {
            JsonNode f = body.get("fallbacks");
            if (!f.isArray()) {
                errors.add("fallbacks must be an array of provider ids");
            } else {
                Set<Provider> ordered = new LinkedHashSet<>();
                for (JsonNode p : f) {
                    Provider parsed = parseProvider(p.asString(""), "fallbacks", errors);
                    if (parsed != null) ordered.add(parsed);
                }
                fallbacks = List.copyOf(ordered);
            }
        }
        if (!errors.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Route change rejected", errors);

        RouteRecord updated = new RouteRecord(r.id(), task, choice, tier, modelId, params, promptVersion, fallbacks, r.trafficSplit());
        RoutingSnapshot candidate = current.withRoute(updated);
        List<String> problems = candidate.problems(task);
        if (!problems.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Route change rejected", problems);

        tx.executeWithoutResult(status -> repository.updateRoute(updated));
        router.invalidate(); // after commit, so nobody can re-cache the old routing
        return view(router.loadFresh(), updated);
    }

    /** Problems the candidate has that the current routing doesn't (pre-existing ones don't block unrelated changes). */
    static List<String> introducedProblems(RoutingSnapshot current, RoutingSnapshot candidate) {
        List<String> introduced = new ArrayList<>();
        for (Task t : Task.values()) {
            Set<String> before = new LinkedHashSet<>(current.problems(t));
            for (String p : candidate.problems(t)) if (!before.contains(p)) introduced.add(p);
        }
        return introduced;
    }

    Map<String, Object> parseParams(JsonNode node, List<String> errors) {
        if (!node.isObject()) {
            errors.add("params must be an object");
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Iterator<String> it = node.propertyNames().iterator(); it.hasNext(); ) {
            String key = it.next();
            JsonNode v = node.get(key);
            if (!ALLOWED_PARAMS.contains(key)) {
                errors.add("Unknown param \"" + key + "\"; allowed: " + ALLOWED_PARAMS);
            } else if (!v.isNumber() || v.asDouble() < 0) {
                errors.add("param " + key + " must be a non-negative number");
            } else {
                out.put(key, json.treeToValue(v, Object.class));
            }
        }
        return out;
    }

    private static Provider parseProvider(String v, String field, List<String> errors) {
        return parseEnum(Provider.class, v, field, errors, null);
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String v, String field, List<String> errors, E fallback) {
        try {
            return Enum.valueOf(type, v);
        } catch (IllegalArgumentException e) {
            errors.add(field + ": unknown value \"" + v + "\"");
            return fallback;
        }
    }

    private static RouteView view(RoutingSnapshot s, RouteRecord r) {
        Provider resolved = s.primaryProvider(r).orElse(null);
        String model = s.primaryModel(r).map(ModelInfo::id).orElse(null);
        return new RouteView(r.task(), r.task().needs(), r.followsDefault() ? "DEFAULT" : r.providerChoice().name(),
                resolved, r.tier(), r.modelId(), model, r.params(), r.promptVersion(), r.fallbacks(), r.trafficSplit(),
                s.problems(r.task()));
    }

    private static ModelView view(ModelInfo m) {
        return new ModelView(m.id(), m.label(), m.tier(), m.capabilities(), m.inputPerMTok(), m.outputPerMTok());
    }
}
