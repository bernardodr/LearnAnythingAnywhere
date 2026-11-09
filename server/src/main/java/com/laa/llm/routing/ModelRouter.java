package com.laa.llm.routing;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.laa.llm.Route;
import com.laa.llm.Task;
import com.laa.llm.config.LlmProperties;
import com.laa.llm.provider.ProviderClients;

/**
 * Resolves the route for each call from {@code model_routes} + {@code providers}, cached briefly.
 * Owner changes call {@link #invalidate()}, so the very next call sees them; the TTL only matters
 * for changes made behind the API's back (or on another instance).
 */
@Component
public class ModelRouter {

    private static final String KEY = "routing";

    private final RoutingRepository repository;
    private final ProviderClients clients;
    private final Cache<String, RoutingSnapshot> cache;

    public ModelRouter(RoutingRepository repository, ProviderClients clients, LlmProperties props) {
        this.repository = repository;
        this.clients = clients;
        this.cache = Caffeine.newBuilder().expireAfterWrite(props.routeCacheTtl()).maximumSize(1).build();
    }

    public Route resolve(Task task) {
        return snapshot().resolve(task);
    }

    public RoutingSnapshot snapshot() {
        return cache.get(KEY, k -> loadFresh());
    }

    /** Straight from the database, bypassing the cache (used to validate owner changes). */
    public RoutingSnapshot loadFresh() {
        return new RoutingSnapshot(repository.providers(), repository.activeRoutes(),
                clients::isAvailable, clients::unavailableReason);
    }

    public void invalidate() {
        cache.invalidateAll();
    }
}
