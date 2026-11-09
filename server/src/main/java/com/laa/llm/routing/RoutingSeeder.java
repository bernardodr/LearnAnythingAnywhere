package com.laa.llm.routing;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.laa.llm.ModelInfo;
import com.laa.llm.ModelTier;
import com.laa.llm.Provider;
import com.laa.llm.Task;
import com.laa.llm.config.LlmProperties;
import com.laa.llm.config.LlmProperties.ModelSettings;
import com.laa.llm.provider.ProviderClients;

/**
 * On startup: sync every provider's model catalogue from config, then fill in anything missing
 * (a default provider, a route per task). Never overwrites the owner's choices.
 */
@Component
public class RoutingSeeder implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(RoutingSeeder.class);

    private final RoutingRepository repository;
    private final ProviderClients clients;
    private final LlmProperties props;
    private final ModelRouter router;
    private final TransactionTemplate tx;

    public RoutingSeeder(RoutingRepository repository, ProviderClients clients, LlmProperties props,
                         ModelRouter router, TransactionTemplate tx) {
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
                boolean usable = clients.isAvailable(wanted) && providers.get(wanted).enabled();
                Provider chosen = usable ? wanted : Provider.FAKE;
                if (!usable) {
                    log.warn("Configured default provider {} is not usable ({}); seeding FAKE as default",
                            wanted, clients.unavailableReason(wanted));
                }
                repository.setDefault(chosen);
            }

            var routes = repository.activeRoutes();
            for (Task task : Task.values()) {
                if (!routes.containsKey(task)) {
                    int maxTokens = task.defaultTier() == ModelTier.LARGE ? 16_000 : 4_000;
                    repository.insertRoute(new RouteRecord(null, task, null, task.defaultTier(), null,
                            Map.of("maxTokens", maxTokens), "v1", List.of(), null));
                }
            }
        });
        router.invalidate();
        router.snapshot().allProblems().forEach((task, problems) ->
                problems.forEach(p -> log.warn("Routing problem: {}", p)));
    }

    private static Map<String, Object> summary(List<ModelInfo> models) {
        Map<String, Object> caps = new LinkedHashMap<>();
        caps.put("structuredOutput", models.stream().anyMatch(m -> m.capabilities().structuredOutput()));
        caps.put("toolCalling", models.stream().anyMatch(m -> m.capabilities().toolCalling()));
        caps.put("streaming", models.stream().anyMatch(m -> m.capabilities().streaming()));
        caps.put("maxContext", models.stream().mapToInt(m -> m.capabilities().maxContext()).max().orElse(0));
        return caps;
    }
}
