package com.laa.llm;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A resolved route: which provider and model handle {@code task} right now.
 * {@code provider} is already resolved from DEFAULT; {@code model} from the tier or override.
 */
public record Route(
        UUID routeId,
        Task task,
        Provider provider,
        String model,
        ModelTier tier,
        Map<String, Object> params,
        String promptVersion,
        List<Provider> fallbacks) {}
