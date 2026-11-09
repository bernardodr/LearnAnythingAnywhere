package com.laa.owner;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.laa.llm.Capabilities;
import com.laa.llm.ModelTier;
import com.laa.llm.Provider;
import com.laa.llm.Task;
import com.laa.llm.provider.ErrorKind;

/** Request and response shapes for the owner API. */
final class OwnerViews {

    private OwnerViews() {}

    record ModelView(String id, String label, ModelTier tier, Capabilities capabilities,
                     BigDecimal inputPerMTok, BigDecimal outputPerMTok) {}

    record ProviderView(
            Provider id,
            String label,
            boolean configured,
            boolean enabled,
            boolean isDefault,
            boolean usable,
            String unavailableReason,
            int tasksServed,
            List<ModelView> models) {}

    record ProviderPatch(Boolean enabled, Boolean isDefault) {}

    record RouteView(
            Task task,
            Task.Needs needs,
            String providerChoice,
            Provider resolvedProvider,
            ModelTier modelTier,
            String modelId,
            String resolvedModel,
            Map<String, Object> params,
            String promptVersion,
            List<Provider> fallbacks,
            Map<String, Object> trafficSplit,
            List<String> problems) {}

    record CompareInput(String message, Map<String, String> variables) {}

    record RouteSpec(Provider provider, ModelTier modelTier, String modelId, Map<String, Object> params, String promptVersion) {}

    record CompareRequest(Task task, CompareInput input, RouteSpec a, RouteSpec b) {}

    record CompareChecks(Boolean schemaValid, List<String> schemaErrors, List<String> notYetAvailable) {}

    record CompareSide(
            Provider provider,
            String model,
            boolean ok,
            String output,
            Object parsed,
            ErrorKind errorKind,
            String error,
            long latencyMs,
            Integer tokensIn,
            Integer tokensOut,
            BigDecimal costEstimate,
            UUID runId,
            CompareChecks checks) {}

    record CompareResponse(Task task, String mode, CompareSide a, CompareSide b) {}
}
