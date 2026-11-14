package com.laa.safety;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The content a reader profile can leave out (BRIEF section 8: violence and weapons, mature themes, drugs and
 * alcohol, gambling, scary content; plus a free list kept as plain strings). Child-safe mode leaves all of them out.
 * Each has a word list used by {@link SafetyRules}, LAA's own deterministic layer, so the categories hold whichever
 * AI service is active. The lists are kept to words that are rarely innocent in a children's lesson ("carbon dating",
 * "the naked eye", "bath bombs" and "glue guns" stay allowed); the classifier judges everything subtler.
 */
public enum Category {
    VIOLENCE_WEAPONS("violence_weapons", "violence and weapons",
            "weapons?|(?<!glue |water |nerf |staple |spray |heat |squirt |grease |caulk )guns?|rifles?|snipers?|pistols?"
                    + "|shotguns?|firearms?|ammo|ammunition|(?<!bath |seed |photo )bombs?|explosives?|grenades?"
                    + "|knife fight(s|ing)?|street fight(ing|s)?|fight(ing)? to hurt|war crimes?|massacres?|murder(s|ing)?"
                    + "|assault(s|ing)?|tortur(e|ed|ing)|warfare|beat (someone|people) up"),
    MATURE_THEMES("mature_themes", "mature themes",
            "sex|sexual(ly)?|porn\\w*|nudes?|nudity|naked (people|bodies|photos|pictures|selfies)|erotic\\w*|dating apps?"
                    + "|online dating|hook ?ups?|strip clubs?|onlyfans|fetish\\w*"),
    DRUGS_ALCOHOL("drugs_alcohol", "drugs and alcohol",
            "drugs?|alcohol(ic)?|(?<!root |ginger )beers?|wines?|vodka|whiske?y|cannabis|marijuana|cocaine|heroin|meth"
                    + "|vap(e|es|ing)|cigarettes?|tobacco|nicotine|drunk|getting high|lsd|ecstasy|mdma"),
    GAMBLING("gambling", "gambling",
            "gambl\\w*|poker|casinos?|betting|blackjack|roulette|slot machines?|lotter(y|ies)|sportsbooks?|card counting"),
    SCARY_CONTENT("scary_content", "scary content",
            "horror|gore|gory|ghost stor(y|ies)|haunted|demons?|zombies?|serial killers?|creepypastas?|jump scares?"
                    + "|slasher\\w*|possessed by");

    private final String key;
    private final String label;
    private final Pattern words;

    Category(String key, String label, String words) {
        this.key = key;
        this.label = label;
        this.words = Pattern.compile("(?i)\\b(" + words + ")\\b");
    }

    /** How profiles store it, e.g. {@code violence_weapons}. */
    public String key() {
        return key;
    }

    /** How people read it, e.g. "violence and weapons". */
    public String label() {
        return label;
    }

    boolean matches(String text) {
        return text != null && words.matcher(text).find();
    }

    public static Optional<Category> fromKey(String key) {
        for (Category c : values()) if (c.key.equals(key)) return Optional.of(c);
        return Optional.empty();
    }
}
