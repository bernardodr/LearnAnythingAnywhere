package com.laa.safety;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import com.laa.llm.GenerationProfile;

/**
 * LAA's own, deterministic safety layer (BRIEF section 8: "safety is enforced in LAA's own layer, so switching
 * provider never turns it off"). It runs alongside the SAFETY_CHECK classifier, and whichever is stricter wins:
 * <ul>
 *   <li><b>Always declined</b>, for every age: requests for instructions to seriously harm people (making bombs or
 *       weapons of mass harm, self-harm methods), sexual content involving minors, and breaking into other people's
 *       accounts or forging documents. Matching is on intent ("how to make a bomb"), not on mentions ("the history of
 *       bomb disposal"), so ordinary learning about difficult subjects stays possible for adults.</li>
 *   <li><b>Left out by the profile</b>: the {@link Category categories} the profile excludes (all of them in
 *       child-safe mode), plus its free-text extras. Here a mention is enough.</li>
 * </ul>
 */
public final class SafetyRules {

    private record Rule(String reason, Pattern pattern) {}

    private static final String MAKE = "(how (do i |to |can i )?)?(make|making|build|building|construct|constructing|assemble"
            + "|assembling|create|creating|synthes[iz]e|synthes[iz]ing|cook|cooking|produce|producing|3d[- ]print(ing)?)";
    private static final List<Rule> ALWAYS = List.of(
            new Rule("instructions for weapons or explosives", Pattern.compile("(?i)\\b" + MAKE
                    + "\\s+(an?\\s+|my own\\s+|homemade\\s+)?(pipe[- ]?bombs?|bombs?|explosives?|ieds?|grenades?|napalm|molotovs?"
                    + "|nerve agents?|sarin|ricin|anthrax|bioweapons?|biological weapons?|chemical weapons?|dirty bombs?|ghost guns?"
                    + "|untraceable guns?)\\b")),
            new Rule("instructions for making drugs", Pattern.compile("(?i)\\b" + MAKE
                    + "\\s+(meth|methamphetamine|fentanyl|crack|heroin|lsd)\\b")),
            new Rule("self-harm", Pattern.compile("(?i)\\b((ways?|how|methods?|best way) to (kill (myself|yourself)|self[- ]?harm"
                    + "|hurt (myself|yourself)|cut (myself|yourself)|commit suicide|end (my|your) life)|suicide methods?"
                    + "|self[- ]?harm (methods?|techniques?|ways?|tips?))\\b")),
            new Rule("sexual content involving minors", Pattern.compile("(?i)\\b((child|children|minors?|underage|kids?|teens?"
                    + "|\\d{1,2}[- ]year[- ]olds?)\\W+(\\w+\\W+){0,2}(porn\\w*|sex\\w*|nudes?|naked|erotic\\w*)"
                    + "|(porn\\w*|sex\\w*|nudes?|naked|erotic\\w*)\\W+(\\w+\\W+){0,2}(child|children|minors?|underage|kids?))\\b")),
            new Rule("breaking into other people's accounts", Pattern.compile("(?i)\\bhack(ing)?\\s+(into\\s+)?(my\\s+|a\\s+|someone'?s?\\s+"
                    + "|somebody'?s?\\s+|my friend'?s?\\s+|other people'?s?\\s+|their\\s+)(\\w+'?s?\\s+)?(instagram|facebook|tiktok|snapchat"
                    + "|account|email|phone|wi-?fi|password)s?\\b")),
            new Rule("forging documents or money", Pattern.compile("(?i)\\b(make|making|get|getting|buy|buying|forge|forging)\\s+(an?\\s+)?"
                    + "(fake (id|ids|passport|driver'?s licen[cs]e)|counterfeit (money|bills|notes|currency))\\b")));

    private SafetyRules() {}

    /** Why the text must always be declined, if it must. */
    public static Optional<String> alwaysDeclined(String text) {
        if (text == null) return Optional.empty();
        for (Rule r : ALWAYS) if (r.pattern().matcher(text).find()) return Optional.of(r.reason());
        return Optional.empty();
    }

    /** The categories (and free-text extras) this profile leaves out that the text touches. */
    public static List<String> leftOut(String text, GenerationProfile profile) {
        List<String> hits = new ArrayList<>();
        if (text == null || profile == null) return hits;
        for (Category c : excluded(profile)) if (c.matches(text)) hits.add(c.label());
        String lower = text.toLowerCase(Locale.ROOT);
        for (String extra : extras(profile)) {
            String e = extra.toLowerCase(Locale.ROOT).strip();
            if (e.length() >= 3 && Pattern.compile("\\b" + Pattern.quote(e)).matcher(lower).find()) hits.add(extra);
        }
        return hits;
    }

    /** Child-safe mode leaves out every category; otherwise those the profile chose. */
    public static Set<Category> excluded(GenerationProfile profile) {
        if (profile.childSafe()) return EnumSet.allOf(Category.class);
        Set<Category> out = EnumSet.noneOf(Category.class);
        for (String f : profile.filters()) Category.fromKey(f).ifPresent(out::add);
        return out;
    }

    /** The profile's free-text extras: its filters that aren't category keys. */
    public static List<String> extras(GenerationProfile profile) {
        return profile.filters().stream().filter(f -> Category.fromKey(f).isEmpty()).sorted().toList();
    }

    /** Child profiles (under 13, or child-safe) see replies only after they've been checked, never mid-stream. */
    public static boolean strict(GenerationProfile profile) {
        return profile != null && (profile.childSafe() || (profile.age() != null && profile.age() < 13));
    }
}
