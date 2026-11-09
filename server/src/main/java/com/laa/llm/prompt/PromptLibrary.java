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

    public boolean exists(Task task, String version) {
        return load(task, version).isPresent();
    }

    public String render(Task task, String version, Map<String, String> variables) {
        String template = load(task, version).orElseThrow(() ->
                new IllegalStateException("Missing prompt prompts/" + task.promptStem() + "." + version + ".md"));
        Matcher m = PLACEHOLDER.matcher(COMMENT.matcher(template).replaceAll(""));
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(variables.getOrDefault(m.group(1), "")));
        }
        m.appendTail(out);
        return out.toString().strip();
    }

    private Optional<String> load(Task task, String version) {
        if (version == null || !VERSION.matcher(version).matches()) return Optional.empty();
        return cache.computeIfAbsent(task.promptStem() + "." + version, key -> {
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
