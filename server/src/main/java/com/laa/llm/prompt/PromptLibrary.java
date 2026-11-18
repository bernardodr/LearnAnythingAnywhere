package com.laa.llm.prompt;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.laa.llm.Call;
import com.laa.llm.Calls;
import com.laa.llm.Task;

/**
 * Provider-neutral, versioned system prompts: {@code classpath:prompts/<task>.<version>.md}.
 * {@code {{name}}} placeholders are filled from the call's variables; unknown ones render empty.
 */
@Component
public class PromptLibrary {

    private static final Pattern VERSION = Pattern.compile("v\\d{1,3}");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*}}");
    /** Authoring notes in prompt files; never sent to a model. */
    private static final Pattern COMMENT = Pattern.compile("(?s)<!--.*?-->");

    private final Map<String, Optional<String>> cache = new ConcurrentHashMap<>();

    /**
     * True when this version ships the task's main prompt and the prompt of every call registered for the task
     * ({@link Calls}), so a route can't be moved to a version missing one.
     */
    public boolean exists(Task task, String version) {
        if (load(task, null, version).isEmpty()) return false;
        return Calls.forTask(task).stream().allMatch(c -> has(c, version));
    }

    /**
     * The newest version (v1, v2, …) that ships the task's main prompt and every registered call's prompt; a new route
     * starts there. Versions are numbered without gaps, so the search stops at the first one missing.
     */
    public String latest(Task task) {
        String newest = "v1";
        for (int n = 2; n < 1000 && load(task, null, "v" + n).isPresent(); n++) {
            if (exists(task, "v" + n)) newest = "v" + n;
        }
        return newest;
    }

    /** True when this version ships the call's prompt. */
    public boolean has(Call call, String version) {
        return load(call.task(), call.variant(), version).isPresent();
    }

    /** The prompt file a call uses at a version, e.g. {@code safety_check.content.v1.md}. */
    public static String fileName(Call call, String version) {
        return fileStem(call.task(), call.variant()) + "." + version + ".md";
    }

    /** The call's prompt at this version, its placeholders filled. */
    public String render(Call call, String version, Map<String, String> variables) {
        Task task = call.task();
        String variant = call.variant();
        String template = load(task, variant, version).orElseThrow(() ->
                new IllegalStateException("Missing prompt prompts/" + fileName(call, version)));
        Matcher m = PLACEHOLDER.matcher(COMMENT.matcher(template).replaceAll(""));
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(variables.getOrDefault(m.group(1), "")));
        }
        m.appendTail(out);
        return out.toString().strip();
    }

    private static String fileStem(Task task, String variant) {
        return variant == null ? task.promptStem() : task.promptStem() + "." + variant;
    }

    private Optional<String> load(Task task, String variant, String version) {
        if (version == null || !VERSION.matcher(version).matches()) return Optional.empty();
        return cache.computeIfAbsent(fileStem(task, variant) + "." + version, key -> {
            var resource = new ClassPathResource("prompts/" + key + ".md");
            if (!resource.exists()) return Optional.empty();
            try {
                return Optional.of(resource.getContentAsString(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}
