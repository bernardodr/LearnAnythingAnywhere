package com.laa.safety;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

import org.springframework.stereotype.Component;

import com.laa.llm.GenerationProfile.LinksPolicy;
import com.laa.profile.Profiles;
import com.laa.textbook.Textbook;
import com.laa.textbook.TextbookRepository;
import com.laa.workspace.Resources;
import com.laa.workspace.Resources.Resource;
import com.laa.workspace.WorkspaceFiles;
import com.laa.workspace.WorkspaceFiles.WorkspaceFile;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * The reader view (BRIEF section 8: links are enforced on the server): what a learner may see of any text, under their
 * reader settings as they are now. The one place the links rule lives:
 * <ul>
 *   <li>the policy is the learner's current one (Off, Allowlist or Open);</li>
 *   <li>the allowlist is every source the learner has added, in any of their textbooks.</li>
 * </ul>
 * It's applied when text is written ({@link SafetyLayer}, lesson writing) and again whenever stored text is shown: every
 * learner-facing JSON response passes through it on the way out ({@link ReaderViewAdvice}), so a changed policy holds
 * for everything written before, and a new endpoint is covered without doing anything.
 */
@Component
public class ReaderView {

    /** One learner's view of links: their policy and the addresses it allows. */
    public record Reader(LinksPolicy policy, Set<String> allowed) {

        public String apply(String text) {
            return text == null ? null : LinkPolicy.apply(text, policy, allowed);
        }

        /**
         * Every string in {@code node}, at any depth, changed in place; returns the same tree. The links policy only
         * ever changes web addresses, so ids, names and every other value pass through untouched.
         */
        public JsonNode apply(JsonNode node) {
            return policy == LinksPolicy.OPEN ? node : walk(node, this::apply);
        }

        private static JsonNode walk(JsonNode node, UnaryOperator<String> f) {
            if (node == null) return null;
            if (node.isString()) return JsonNodeFactory.instance.stringNode(f.apply(node.asString()));
            if (node instanceof ObjectNode object) {
                for (String name : new ArrayList<>(object.propertyNames())) object.set(name, walk(object.get(name), f));
            } else if (node instanceof ArrayNode array) {
                for (int i = 0; i < array.size(); i++) array.set(i, walk(array.get(i), f));
            }
            return node;
        }
    }

    private final Profiles profiles;
    private final TextbookRepository textbooks;
    private final WorkspaceFiles workspace;

    ReaderView(Profiles profiles, TextbookRepository textbooks, WorkspaceFiles workspace) {
        this.profiles = profiles;
        this.textbooks = textbooks;
        this.workspace = workspace;
    }

    /** This learner's view now. */
    public Reader of(UUID userId) {
        LinksPolicy policy = profiles.forUser(userId).linksPolicy();
        return new Reader(policy, policy == LinksPolicy.ALLOWLIST ? sources(userId) : Set.of());
    }

    /** Every web address among the learner's own sources, across their textbooks. */
    private Set<String> sources(UUID userId) {
        Set<String> urls = new HashSet<>();
        for (Textbook tb : textbooks.listForUser(userId)) {
            String md = workspace.read(tb.id(), "RESOURCES.md").map(WorkspaceFile::content).orElse(null);
            Resources.parse(md).stream().map(Resource::url).filter(Objects::nonNull).forEach(urls::add);
        }
        return urls;
    }
}
