package com.laa.lesson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.laa.llm.Calls;
import com.laa.llm.GenerationProfile;
import com.laa.llm.LlmUnavailableException;
import com.laa.llm.PromptContext;
import com.laa.llm.StructuredOutputException;
import com.laa.llm.config.LlmProperties;
import com.laa.llm.runs.Origin;
import com.laa.safety.SafetyLayer;
import com.laa.textbook.Textbook;
import com.laa.workspace.Resources;
import com.laa.workspace.Resources.Resource;
import com.laa.workspace.WorkspaceFiles;
import com.laa.workspace.WorkspaceFiles.WorkspaceFile;

import tools.jackson.databind.json.JsonMapper;

/**
 * Finds a lesson's sources before it's written (issue #82, BRIEF section 6.3), so every lesson ends with real pages the
 * learner can trust. Used by {@link LessonWriter} straight after the plan.
 *
 * <ol>
 *   <li>{@code Calls.RESEARCH} through the {@link SafetyLayer}: a model that searches the web (a provider that can't
 *       search refuses it) returns up to six candidates; their title, site and note are checked like any text a
 *       learner reads.</li>
 *   <li>Each candidate must be on the owner's list of educational sites when the reader is in child-safe mode. One
 *       the book already has is linked to this lesson as it is; a new one is kept only if it passes
 *       {@link SourceCheck} (it opens, safely, as the page it claims to be). Four at most.</li>
 *   <li>The keepers are added to the book's RESOURCES.md, where they join the learner's own sources: the lesson can
 *       cite them, the back of the book lists them, and the links policy's allowlist includes them.</li>
 * </ol>
 * Finding none never stops a lesson: if the research can't run (no provider that searches, a failed or malformed
 * reply, or the safety check held its words back), the lesson is written without new sources and says so. The one
 * exception is the safety check itself being unavailable, which is never swallowed (unchecked text is never shown).
 * Costs one AI call with up to three web searches per lesson (metered like every call).
 */
@Component
public class SourceFinder {

    private static final Logger log = LoggerFactory.getLogger(SourceFinder.class);
    private static final int MAX_KEPT = 4;

    private final SafetyLayer safety;
    private final SourceCheck check;
    private final WorkspaceFiles workspace;
    private final LlmProperties props;
    private final JsonMapper json;

    SourceFinder(SafetyLayer safety, SourceCheck check, WorkspaceFiles workspace, LlmProperties props, JsonMapper json) {
        this.safety = safety;
        this.check = check;
        this.workspace = workspace;
        this.props = props;
        this.json = json;
    }

    /**
     * Finds, checks and saves sources for the lesson about to be written.
     *
     * @param tb      the book
     * @param userId  the learner (their reader settings decide child-safe filtering; their allowance pays)
     * @param profile their reader settings
     * @param vars    the lesson's teaching variables (title, mission, the book's current sources, …)
     * @param plan    the lesson's plan, which says what to find sources for
     * @return this lesson's sources as RESOURCES.md ids (r1, r2, …): ones the book already had, then the new ones;
     *         empty if none were found
     * @throws com.laa.safety.Moderation.Unavailable if the safety check couldn't run (HTTP 503)
     */
    public List<String> findFor(Textbook tb, UUID userId, GenerationProfile profile, Map<String, String> vars, LessonPlan plan) {
        FoundSources found;
        try {
            PromptContext ctx = new PromptContext(vars, List.of(),
                    "Find sources for this lesson:\n" + json.writeValueAsString(plan), userId, tb.id(), Origin.APP);
            SafetyLayer.Checked<FoundSources> result = safety.write(Calls.RESEARCH, ctx);
            if (result.blocked()) {
                log.info("Sources for textbook {} were held back by the safety check; the lesson has none new", tb.id());
                return List.of();
            }
            found = result.value();
        } catch (LlmUnavailableException | StructuredOutputException e) {
            log.warn("Sources for textbook {} couldn't be found: {}", tb.id(), e.getMessage());
            return List.of();
        }

        String md = workspace.read(tb.id(), "RESOURCES.md").map(WorkspaceFile::content).orElse(null);
        // The book's sources by address: one the search finds again is linked to this lesson, not added twice.
        Map<String, String> known = new HashMap<>();
        Resources.parse(md).stream().filter(r -> r.url() != null).forEach(r -> known.putIfAbsent(key(r.url()), r.id()));
        List<String> linked = new ArrayList<>();
        List<String> added = new ArrayList<>();
        for (FoundSources.Source s : found.sources()) {
            if (linked.size() + added.size() >= MAX_KEPT) break;
            if (profile.childSafe() && !SourceCheck.onSites(s.url(), props.childSafeSearchDomains())) {
                log.info("Source left out (not on the child-safe list): {}", s.url());
                continue;
            }
            String existing = known.get(key(s.url()));
            if (existing != null) {
                if (!linked.contains(existing)) linked.add(existing);
                continue;
            }
            var passed = check.check(s.url(), s.title(), s.site());
            if (passed.isEmpty()) continue;
            String url = passed.get().url();
            if (known.containsKey(key(url))) { // it redirected to one the book has
                if (!linked.contains(known.get(key(url)))) linked.add(known.get(key(url)));
                continue;
            }
            known.put(key(url), "new");
            md = Resources.append(md, tb.title(), s.title(), url, s.note().strip() + " (" + s.site().strip() + ")");
            added.add(url);
        }
        if (!added.isEmpty()) workspace.write(tb.id(), "RESOURCES.md", md);
        // The new entries' ids, by their addresses (ids are positions, so read them back rather than guess).
        List<String> ids = new ArrayList<>(linked);
        Resources.parse(md).stream().filter(r -> r.url() != null && added.contains(r.url())).map(Resource::id).forEach(ids::add);
        return ids;
    }

    /** An address compared loosely: no scheme, no "www.", no trailing slash, any case. */
    private static String key(String url) {
        return url.strip().toLowerCase(Locale.ROOT).replaceFirst("^https?://", "").replaceFirst("^www\\.", "").replaceAll("/+$", "");
    }
}
