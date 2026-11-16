package com.laa.profile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.laa.auth.Accounts;
import com.laa.auth.AppUser;
import com.laa.auth.Role;
import com.laa.auth.Throttle;
import com.laa.auth.UserRepository;
import com.laa.llm.GenerationProfile;
import com.laa.llm.GenerationProfile.LinksPolicy;
import com.laa.profile.ProfileRepository.Child;
import com.laa.profile.ProfileRepository.Stored;
import com.laa.safety.Category;
import com.laa.safety.SafetyRules;
import com.laa.web.ApiException;

/**
 * Reader settings (BRIEF section 8). Every learner has a generation profile; it shapes every prompt and every safety
 * check made for them.
 * <ul>
 *   <li>An adult manages their own profile, but can't make it a child's: ages under 13 are only for profiles a
 *       parent creates.</li>
 *   <li>A child's profile is managed by their parent. The child can see it but not change it.</li>
 *   <li>A parent gives a child their account by creating it, with a password they choose, and can reset that
 *       password later.</li>
 *   <li>A parent can set a PIN (the prototype's parent lock). Once set, every change to a child's settings or password
 *       needs it. Wrong PINs are limited like passwords ({@link Throttle}).</li>
 * </ul>
 */
@Service
public class Profiles {

    public static final int CHILD_AGE = 13;
    private static final int MAX_EXTRAS = 10;

    public record CategoryView(String key, String label) {}

    public record ProfileView(String id, String userId, Integer age, String readingLevel, boolean childSafe, List<String> categories,
                              List<String> extras, LinksPolicy linksPolicy, Integer revealAfterAttempts, boolean managedByParent,
                              boolean canEdit, List<CategoryView> allCategories) {}

    public record ChildView(String userId, String email, String displayName, ProfileView profile) {}

    /** What a settings form sends. {@code categories} are keys from {@link Category}; {@code extras} free text. */
    public record Settings(Integer age, Boolean childSafe, List<String> categories, List<String> extras, LinksPolicy linksPolicy,
                           Integer revealAfterAttempts) {}

    private final ProfileRepository profiles;
    private final UserRepository users;
    private final Accounts accounts;
    private final Throttle throttle;
    private final BCryptPasswordEncoder pins = new BCryptPasswordEncoder();

    Profiles(ProfileRepository profiles, UserRepository users, Accounts accounts, Throttle throttle) {
        this.profiles = profiles;
        this.users = users;
        this.accounts = accounts;
        this.throttle = throttle;
    }

    /** The profile every call for this learner uses; an adult default is created on first use. */
    public GenerationProfile forUser(UUID userId) {
        return stored(userId).settings();
    }

    public Stored stored(UUID userId) {
        return profiles.forUser(userId).orElseGet(() -> profiles.create(userId, userId, GenerationProfile.ADULT_DEFAULT));
    }

    public ProfileView mine(UUID userId) {
        return view(stored(userId), userId);
    }

    public ProfileView get(UUID viewerId, UUID profileId) {
        Stored s = profiles.find(profileId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No such profile"));
        if (!s.userId().equals(viewerId) && !s.ownerUserId().equals(viewerId)) throw new ApiException(HttpStatus.NOT_FOUND, "No such profile");
        return view(s, viewerId);
    }

    @Transactional
    public ProfileView update(UUID viewerId, UUID profileId, Settings request, String pin) {
        Stored s = profiles.find(profileId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No such profile"));
        boolean child = profiles.parentOf(s.userId()).isPresent();
        if (child && s.userId().equals(viewerId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Your parent manages these settings.");
        }
        if (!s.ownerUserId().equals(viewerId)) throw new ApiException(HttpStatus.NOT_FOUND, "No such profile");
        if (child) checkPin(viewerId, pin, "Enter your parent PIN to change these settings.");
        return view(profiles.update(profileId, validated(request, child)), viewerId);
    }

    public List<ChildView> children(UUID parentId) {
        requireParent(parentId);
        List<ChildView> out = new ArrayList<>();
        for (Child c : profiles.children(parentId)) {
            out.add(new ChildView(c.userId().toString(), c.email(), c.displayName(), c.profile() == null ? null : view(c.profile(), parentId)));
        }
        return out;
    }

    /** A parent adds a child: a learner account under them, with a password they choose and a profile they manage. */
    @Transactional
    public ChildView addChild(UUID parentId, String email, String displayName, Integer age, Boolean childSafe, String password) {
        requireParent(parentId);
        String mail = email == null ? "" : email.strip().toLowerCase();
        if (!mail.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw new ApiException(HttpStatus.BAD_REQUEST, "That doesn’t look like an email address.");
        if (users.findByEmail(mail).isPresent()) throw new ApiException(HttpStatus.CONFLICT, "Someone already signs in with that email.");
        if (age == null || age < 5 || age > 18) throw new ApiException(HttpStatus.BAD_REQUEST, "Choose an age from 5 to 18.");
        String hash = accounts.newPasswordHash(password, mail);
        UUID childId = profiles.createChildUser(parentId, mail, displayName == null || displayName.isBlank() ? null : displayName.strip(), hash);
        boolean safe = childSafe == null ? age < CHILD_AGE : childSafe;
        Stored s = profiles.create(childId, parentId, new GenerationProfile(age, safe, Set.of(), LinksPolicy.OFF, null, null));
        return new ChildView(childId.toString(), mail, displayName, view(s, parentId));
    }

    /** A parent sets a new password for their child, which ends the child's sign-ins. Needs the PIN once one is set. */
    public void resetChildPassword(UUID parentId, UUID childId, String password, String pin) {
        requireParent(parentId);
        if (!profiles.parentOf(childId).map(parentId::equals).orElse(false)) throw new ApiException(HttpStatus.NOT_FOUND, "No such child");
        checkPin(parentId, pin, "Enter your parent PIN to change your child’s password.");
        accounts.resetPassword(childId, password);
    }

    public void setPin(UUID parentId, String pin, String currentPin) {
        requireParent(parentId);
        if (pin == null || !pin.matches("\\d{4,8}")) throw new ApiException(HttpStatus.BAD_REQUEST, "A PIN is 4 to 8 digits.");
        checkPin(parentId, currentPin, "Enter your current PIN to change it.");
        profiles.setPinHash(parentId, pins.encode(pin));
    }

    public boolean hasPin(UUID parentId) {
        return profiles.pinHash(parentId).isPresent();
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

    /** Passes if no PIN is set or {@code pin} is right; otherwise 403 with {@code ask}. Wrong PINs are limited. */
    private void checkPin(UUID parentId, String pin, String ask) {
        Optional<String> hash = profiles.pinHash(parentId);
        if (hash.isEmpty()) return;
        Throttle.Key key = Throttle.pin(parentId);
        throttle.guard(key);
        if (pin == null || !pins.matches(pin, hash.get())) {
            throttle.failed(key);
            throw new ApiException(HttpStatus.FORBIDDEN, ask);
        }
        throttle.succeeded(key);
    }

    private void requireParent(UUID userId) {
        AppUser u = users.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unknown user"));
        if (u.role() != Role.PARENT) throw new ApiException(HttpStatus.FORBIDDEN, "Only parent accounts can manage children.");
    }

    private static GenerationProfile validated(Settings r, boolean child) {
        if (r == null) throw new ApiException(HttpStatus.BAD_REQUEST, "No settings sent.");
        Integer age = r.age();
        if (age != null && (age < 5 || age > 18)) throw new ApiException(HttpStatus.BAD_REQUEST, "Choose an age from 5 to 18, or Adult.");
        if (age != null && age < CHILD_AGE && !child) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Settings for children under 13 are set up by a parent: sign in as a parent and add your child.");
        }
        if (child && age == null) throw new ApiException(HttpStatus.BAD_REQUEST, "A child’s profile needs an age.");
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

    private ProfileView view(Stored s, UUID viewerId) {
        GenerationProfile p = s.settings();
        boolean child = profiles.parentOf(s.userId()).isPresent();
        boolean canEdit = child ? s.ownerUserId().equals(viewerId) : s.userId().equals(viewerId);
        List<String> categories = p.filters().stream().filter(f -> Category.fromKey(f).isPresent()).sorted().toList();
        List<CategoryView> all = java.util.Arrays.stream(Category.values()).map(c -> new CategoryView(c.key(), c.label())).toList();
        return new ProfileView(s.id().toString(), s.userId().toString(), p.age(), readingLevel(p.age()), p.childSafe(), categories,
                SafetyRules.extras(p), p.linksPolicy(), p.revealAfterAttempts(), child, canEdit, all);
    }

    /** For responses that list a parent's children with their lock state. */
    public Map<String, Object> parentState(UUID parentId) {
        return Map.of("pinSet", hasPin(parentId), "children", children(parentId));
    }
}
