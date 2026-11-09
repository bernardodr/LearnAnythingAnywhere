package com.laa.owner;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.laa.auth.CurrentUser;
import com.laa.llm.Provider;
import com.laa.llm.Task;
import com.laa.llm.runs.ModelRun;
import com.laa.llm.runs.ModelRunRepository;
import com.laa.owner.OwnerViews.CompareRequest;
import com.laa.owner.OwnerViews.CompareResponse;
import com.laa.owner.OwnerViews.ProviderPatch;
import com.laa.owner.OwnerViews.ProviderView;
import com.laa.owner.OwnerViews.RouteView;
import com.laa.web.ApiException;

import tools.jackson.databind.JsonNode;

/** Owner API (BRIEF section 5.4). OWNER role enforced in SecurityConfig. */
@RestController
@RequestMapping("/owner")
class OwnerController {

    private final OwnerRoutingService routing;
    private final CompareService compare;
    private final ModelRunRepository runs;

    OwnerController(OwnerRoutingService routing, CompareService compare, ModelRunRepository runs) {
        this.routing = routing;
        this.compare = compare;
        this.runs = runs;
    }

    @GetMapping("/providers")
    List<ProviderView> providers() {
        return routing.providers();
    }

    @PatchMapping("/providers/{id}")
    ProviderView patchProvider(@PathVariable String id, @RequestBody(required = false) ProviderPatch patch) {
        return routing.patchProvider(parse(Provider.class, id), patch);
    }

    @GetMapping("/routes")
    List<RouteView> routes() {
        return routing.routes();
    }

    @PatchMapping("/routes/{task}")
    RouteView patchRoute(@PathVariable String task, @RequestBody(required = false) JsonNode body) {
        return routing.patchRoute(parse(Task.class, task), body);
    }

    @GetMapping("/runs")
    List<ModelRun> runs(@RequestParam(required = false) String task,
                        @RequestParam(required = false) String provider,
                        @RequestParam(defaultValue = "50") int limit) {
        return runs.recent(task == null || task.isBlank() ? null : parse(Task.class, task),
                provider == null || provider.isBlank() ? null : parse(Provider.class, provider),
                Math.max(1, Math.min(limit, 500)));
    }

    @PostMapping("/compare")
    CompareResponse compare(@RequestBody(required = false) CompareRequest request, @AuthenticationPrincipal Jwt jwt) {
        return compare.compare(request, CurrentUser.id(jwt));
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Unknown " + type.getSimpleName().toLowerCase() + " " + value);
        }
    }
}
