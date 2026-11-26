package com.laa.llm.routing;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.laa.llm.ModelInfo;
import com.laa.llm.ModelTier;
import com.laa.llm.Provider;
import com.laa.llm.Task;
import com.laa.llm.prompt.PromptLibrary;
import com.laa.llm.config.LlmProperties;
import com.laa.llm.config.LlmProperties.ModelSettings;
import com.laa.llm.provider.ProviderClients;

/**
 * On startup: sync every provider's model catalogue from config, then fill in anything missing
 * (a default provider, a route per task). Never overwrites the owner's choices. A task's first route uses the newest
 * prompt version that ships every call of the task ({@link PromptLibrary#latest}); after that, the owner decides.
 */
@Component
public class RoutingSeeder implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(RoutingSeeder.class);

    private final RoutingRepository repository;
    private final ProviderClients clients;
    private final LlmProperties props;
    private final ModelRouter router;
    private final TransactionTemplate tx;
    private final PromptLibrary prompts;

    public RoutingSeeder(RoutingRepository repository, ProviderClients clients, LlmProperties props,
                         ModelRouter router, TransactionTemplate tx, PromptLibrary prompts) {
        this.prompts = prompts;
        this.repository = repository;
        this.clients = clients;
        this.props = props;
        this.router = router;
        this.tx = tx;
    }

    @Override
    public void afterSingletonsInstantiated() {
        seed();
    }

    public void seed() {
        tx.executeWithoutResult(status -> {
            for (Provider p : Provider.values()) {
                var settings = props.settings(p);
                List<ModelInfo> models = settings.models().stream().map(ModelSettings::toModelInfo).toList();
                repository.upsertProviderCatalog(p, settings.enabled(), models, summary(models));
            }

            var providers = repository.providers();
            boolean hasDefault = providers.values().stream().anyMatch(ProviderRecord::isDefault);
            if (!hasDefault) {
                Provider wanted = props.defaultProvider();
                repository.setDefault(firstDefault(wanted, wanted != null && clients.isAvailable(wanted) && providers.get(wanted).enabled(),
                        wanted == null ? null : clients.unavailableReason(wanted)));
            }

            var routes = repository.activeRoutes();
            var current = repository.providers();
            for (Task task : Task.values()) {
                if (!routes.containsKey(task)) {
                    int maxTokens = task.defaultTier() == ModelTier.LARGE ? 16_000 : 4_000;
                    repository.insertRoute(new RouteRecord(null, task, searcherFor(task, null, current), task.defaultTier(), null,
                            Map.of("maxTokens", maxTokens), prompts.latest(task), List.of(), null));
                }
            }
            // A route that follows the default for a task that must search the web, where the default can't search:
            // point it at a provider that can, or every call would be refused (sources are never found from memory).
            for (RouteRecord r : repository.activeRoutes().values()) {
                if (!r.task().needs().webSearch() || !r.followsDefault()) continue;
                Provider searcher = searcherFor(r.task(), r.modelId(), current);
                if (searcher != null) {
                    log.info("{} follows the default provider, which can't search the web; routing it to {}", r.task(), searcher);
                    repository.updateRoute(new RouteRecord(r.id(), r.task(), searcher, r.tier(), r.modelId(), r.params(),
                            r.promptVersion(), r.fallbacks(), r.trafficSplit()));
                }
            }
        });
        router.invalidate();
        router.snapshot().allProblems().forEach((task, problems) ->
                problems.forEach(p -> log.warn("Routing problem: {}", p)));
    }

    /**
     * For a task that must search the web: null (follow the default) if the default provider's model for the task's tier
     * can search, else the first enabled, usable provider whose model can; null if none can (the owner console then
     * shows the problem). For any other task: null.
     */
    Provider searcherFor(Task task, String modelId, Map<Provider, ProviderRecord> providers) {
        if (!task.needs().webSearch()) return null;
        ProviderRecord def = providers.values().stream().filter(ProviderRecord::isDefault).findFirst().orElse(null);
        if (def != null && searches(def, task, modelId)) return null;
        return providers.values().stream()
                .filter(p -> p.enabled() && clients.isAvailable(p.id()) && searches(p, task, null))
                .map(ProviderRecord::id).findFirst().orElse(null);
    }

    private static boolean searches(ProviderRecord p, Task task, String modelId) {
        Optional<ModelInfo> model = modelId != null ? p.model(modelId) : p.modelForTier(task.defaultTier());
        return model.map(m -> m.capabilities().searchesTheWeb()).orElse(false);
    }

    /**
     * The default provider for an empty database: the configured one, which must be usable. Never a silent stand-in:
     * a server that would otherwise answer every learner with practice text refuses to start instead.
     */
    static Provider firstDefault(Provider wanted, boolean usable, String reason) {
        if (wanted == null) {
            throw new IllegalStateException("No default AI service: set LAA_DEFAULT_PROVIDER (ANTHROPIC, OPENAI, GOOGLE or OLLAMA) "
                    + "for a new database. After that, the owner console chooses.");
        }
        if (!usable) throw new IllegalStateException("The default AI service " + wanted + " can't be used: " + reason);
        return wanted;
    }

    private static Map<String, Object> summary(List<ModelInfo> models) {
        Map<String, Object> caps = new LinkedHashMap<>();
        caps.put("structuredOutput", models.stream().anyMatch(m -> m.capabilities().structuredOutput()));
        caps.put("toolCalling", models.stream().anyMatch(m -> m.capabilities().toolCalling()));
        caps.put("streaming", models.stream().anyMatch(m -> m.capabilities().streaming()));
        caps.put("webSearch", models.stream().anyMatch(m -> m.capabilities().searchesTheWeb()));
        caps.put("maxContext", models.stream().mapToInt(m -> m.capabilities().maxContext()).max().orElse(0));
        return caps;
    }
}
