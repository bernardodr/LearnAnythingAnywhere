package com.laa.profile;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.laa.auth.PinLock;
import com.laa.llm.GenerationProfile;
import com.laa.llm.GenerationProfile.LinksPolicy;
import com.laa.profile.ProfileRepository.Stored;
import com.laa.safety.Category;
import com.laa.safety.SafetyRules;
import com.laa.web.ApiException;

/**
 * Reader settings (BRIEF section 8). Every account has one generation profile; it shapes every prompt and every
 * safety check made for it.
 * <ul>
 *   <li>Accounts are for adults. A child uses a parent's account: the parent sets the reader settings for them (any
 *       age from 5, child-safe mode, and so on).</li>
 *   <li>The parent PIN ({@link PinLock}), once set, is needed to change the settings, so the child can't.</li>
 * </ul>
 */
@Service
public class Profiles {

    private static final int MAX_EXTRAS = 10;

    public record CategoryView(String key, String label) {}

    public record ProfileView(String id, String userId, Integer age, String readingLevel, boolean childSafe, List<String> categories,
                              List<String> extras, LinksPolicy linksPolicy, Integer revealAfterAttempts, boolean pinSet,
                              List<CategoryView> allCategories) {}

    /** What a settings form sends. {@code categories} are keys from {@link Category}; {@code extras} free text. */
    public record Settings(Integer age, Boolean childSafe, List<String> categories, List<String> extras, LinksPolicy linksPolicy,
                           Integer revealAfterAttempts) {}

    private final ProfileRepository profiles;
    private final PinLock lock;

    Profiles(ProfileRepository profiles, PinLock lock) {
        this.profiles = profiles;
        this.lock = lock;
    }

    /** The profile every call for this learner uses; an adult default is created on first use. */
    public GenerationProfile forUser(UUID userId) {
        return stored(userId).settings();
    }

    public Stored stored(UUID userId) {
        return profiles.forUserOrCreate(userId, GenerationProfile.ADULT_DEFAULT);
    }

    public ProfileView mine(UUID userId) {
        return view(stored(userId));
    }

    public ProfileView get(UUID viewerId, UUID profileId) {
        return view(own(viewerId, profileId));
    }

    @Transactional
    public ProfileView update(UUID viewerId, UUID profileId, Settings request, String pin) {
        own(viewerId, profileId);
        lock.check(viewerId, pin, "Enter the parent PIN to change these settings.");
        return view(profiles.update(profileId, validated(request)));
    }

    /** Reading level shown on the age slider. */
    public static String readingLevel(Integer age) {
        if (age == null) return "Adult";
        if (age <= 6) return "Early reader (ages 5–6)";
        if (age <= 8) return "Young reader (ages 7–8)";
        if (age <= 10) return "Confident reader (ages 9–10)";
        if (age <= 12) return "Older child (ages 11–12)";
        if (age <= 15) return "Young teen (ages 13–15)";
        return "Older teen (ages 16–18)";
    }

    // ------------------------------------------------------------------ helpers

    /** The viewer's own profile; anyone else's is a 404. */
    private Stored own(UUID viewerId, UUID profileId) {
        Stored s = profiles.find(profileId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No such profile"));
        if (!s.userId().equals(viewerId)) throw new ApiException(HttpStatus.NOT_FOUND, "No such profile");
        return s;
    }

    private static GenerationProfile validated(Settings r) {
        if (r == null) throw new ApiException(HttpStatus.BAD_REQUEST, "No settings sent.");
        Integer age = r.age();
        if (age != null && (age < 5 || age > 18)) throw new ApiException(HttpStatus.BAD_REQUEST, "Choose an age from 5 to 18, or Adult.");
        Integer reveal = r.revealAfterAttempts();
        if (reveal != null && (reveal < 1 || reveal > 10)) throw new ApiException(HttpStatus.BAD_REQUEST, "Reveal after 1 to 10 tries, or never.");
        Set<String> filters = new HashSet<>();
        for (String key : r.categories() == null ? List.<String>of() : r.categories()) {
            filters.add(Category.fromKey(key).map(Category::key)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Unknown category " + key)));
        }
        List<String> extras = r.extras() == null ? List.of() : r.extras().stream().map(e -> e == null ? "" : e.replaceAll("\\s+", " ").strip())
                .filter(e -> !e.isEmpty()).distinct().toList();
        if (extras.size() > MAX_EXTRAS) throw new ApiException(HttpStatus.BAD_REQUEST, "Up to " + MAX_EXTRAS + " extra topics to leave out.");
        for (String e : extras) {
            if (e.length() > 40) throw new ApiException(HttpStatus.BAD_REQUEST, "Keep each extra topic under 40 characters.");
            if (Category.fromKey(e).isEmpty()) filters.add(e);
        }
        return new GenerationProfile(age, Boolean.TRUE.equals(r.childSafe()), filters,
                r.linksPolicy() == null ? LinksPolicy.OFF : r.linksPolicy(), reveal, null);
    }

    private ProfileView view(Stored s) {
        GenerationProfile p = s.settings();
        List<String> categories = p.filters().stream().filter(f -> Category.fromKey(f).isPresent()).sorted().toList();
        List<CategoryView> all = java.util.Arrays.stream(Category.values()).map(c -> new CategoryView(c.key(), c.label())).toList();
        return new ProfileView(s.id().toString(), s.userId().toString(), p.age(), readingLevel(p.age()), p.childSafe(), categories,
                SafetyRules.extras(p), p.linksPolicy(), p.revealAfterAttempts(), lock.isSet(s.userId()), all);
    }
}
