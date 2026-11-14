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

    /** {@code call}: a registered call's id ({@code GRADE}, {@code SAFETY_CHECK.content}, …), from {@code GET /owner/calls}. */
    record CompareRequest(String call, CompareInput input, RouteSpec a, RouteSpec b) {}

    /** A registered call, for the owner console: what Compare can run, and what the run log names. */
    record CallView(String id, Task task, String label, boolean structured) {}

    /**
     * Compare's automatic checks (BRIEF section 5.4). {@code quizRules}: problems found by the lesson quiz rules
     * (LESSON_WRITE only; null when not applicable, empty when they pass). {@code readingAge}: estimated reading age
     * of the output's prose (null when there's too little). {@code moderation}: arrives with the safety layer (M6).
     */
    /** {@code passed} null: there was no output to check, or the check couldn't run ({@code flags} says which). */
    record ModerationCheck(Boolean passed, List<String> flags) {}

    record CompareChecks(Boolean schemaValid, List<String> schemaErrors, List<String> quizRules, Double readingAge,
                         ModerationCheck moderation) {}

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

    record CompareResponse(Task task, String call, String mode, CompareSide a, CompareSide b) {}
}
