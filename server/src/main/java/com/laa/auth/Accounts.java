package com.laa.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.laa.web.ApiException;

/**
 * Signing up, signing in and passwords: the only way to a sign-in token.
 * <ul>
 *   <li><b>Sign-up</b>: email, a password that passes {@link Passwords#problem}, and Learner or Parent. An email on the
 *       owner list also needs the owner setup code ({@code laa.auth.owner-setup-code}), because nobody's email is
 *       checked: without it, whoever signed up first with the owner's email would get the owner console.</li>
 *   <li><b>Sign-in</b>: a wrong password and an unknown email get the same answer and take the same time.
 *       {@link Throttle} limits tries per email and per network address. Hashes made with weaker settings are redone.
 *       An OWNER whose email is no longer on the list becomes a learner.</li>
 *   <li><b>Password changes</b> (your own, or a parent resetting a child's) end every older sign-in; the person who
 *       changed their own gets a fresh token, so they stay signed in.</li>
 *   <li><b>Dev sign-in</b> (email only) is for local development and tests, when {@code laa.auth.dev-login-enabled}.</li>
 * </ul>
 */
@Service
public class Accounts {

    public record UserView(String id, String email, String displayName, Role role, boolean hasPassword) {}

    public record SignedIn(String token, String tokenType, long expiresIn, UserView user) {}

    public static final String NEEDS_OWNER_CODE = "owner-setup-code";
    private static final String WRONG = "Email or password is incorrect.";

    private final UserRepository users;
    private final Passwords passwords;
    private final Throttle throttle;
    private final JwtService jwt;
    private final AuthProperties props;

    Accounts(UserRepository users, Passwords passwords, Throttle throttle, JwtService jwt, AuthProperties props) {
        this.users = users;
        this.passwords = passwords;
        this.throttle = throttle;
        this.jwt = jwt;
        this.props = props;
    }

    public SignedIn signUp(String email, String password, String displayName, Role role, String ownerSetupCode, String address) {
        String mail = normalise(email);
        if (role == Role.OWNER) throw new ApiException(HttpStatus.BAD_REQUEST, "Sign up as a learner or a parent.");
        Role chosen = role == null ? Role.LEARNER : role;
        if (props.isOwnerEmail(mail)) {
            chosen = Role.OWNER;
            checkOwnerCode(ownerSetupCode, address);
        }
        if (users.findByEmail(mail).isPresent()) throw taken();
        String hash = newPasswordHash(password, mail);
        String name = displayName == null || displayName.isBlank() ? null : displayName.strip();
        try {
            return signedIn(users.create(mail, name, chosen, hash), 0, true);
        } catch (DuplicateKeyException e) {
            throw taken();
        }
    }

    public SignedIn signIn(String email, String password, String address) {
        String mail = normalise(email);
        Throttle.Key forEmail = Throttle.email(mail);
        Throttle.Key forAddress = Throttle.address(address);
        throttle.guard(forEmail, forAddress);
        UserRepository.Account account = users.accountByEmail(mail).orElse(null);
        String hash = account == null ? null : account.passwordHash();
        if (!passwords.matches(password, hash)) {
            throttle.failed(forEmail, forAddress);
            throw new ApiException(HttpStatus.UNAUTHORIZED, WRONG);
        }
        throttle.succeeded(forEmail);
        if (passwords.needsUpgrade(hash)) users.rehash(account.user().id(), passwords.hash(password));
        AppUser user = account.user();
        if (user.role() == Role.OWNER && !props.isOwnerEmail(mail)) user = users.updateRole(user.id(), Role.LEARNER);
        return signedIn(user, account.passwordVersion(), true);
    }

    /** Email-only sign-in for development: the owner list decides OWNER both ways. */
    public SignedIn devLogin(String email, String displayName, Role role) {
        if (!props.devLoginEnabled()) throw new ApiException(HttpStatus.NOT_FOUND, "Dev sign-in is off");
        String mail = normalise(email);
        boolean owner = props.isOwnerEmail(mail);
        if (role == Role.OWNER && !owner) {
            throw new ApiException(HttpStatus.FORBIDDEN, "The OWNER role is only for emails listed in LAA_OWNER_EMAILS");
        }
        UserRepository.Account account = users.accountByEmail(mail).orElseGet(() -> new UserRepository.Account(users.create(mail,
                displayName == null || displayName.isBlank() ? null : displayName.strip(),
                owner ? Role.OWNER : role == null ? Role.LEARNER : role), null, 0));
        AppUser user = account.user();
        if (owner && user.role() != Role.OWNER) user = users.updateRole(user.id(), Role.OWNER);
        if (!owner && user.role() == Role.OWNER) user = users.updateRole(user.id(), Role.LEARNER);
        return signedIn(user, account.passwordVersion(), account.passwordHash() != null);
    }

    /**
     * Sets a new password for the signed-in user, ending their other sign-ins; returns their new sign-in. Needs the
     * current password, unless the account has none yet (made before passwords, or by dev sign-in).
     */
    public SignedIn changePassword(UUID userId, String currentPassword, String newPassword, String address) {
        UserRepository.Account account = users.account(userId).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unknown user"));
        String mail = account.user().email();
        if (account.passwordHash() != null) {
            Throttle.Key forEmail = Throttle.email(mail);
            Throttle.Key forAddress = Throttle.address(address);
            throttle.guard(forEmail, forAddress);
            if (!passwords.matches(currentPassword, account.passwordHash())) {
                throttle.failed(forEmail, forAddress);
                throw new ApiException(HttpStatus.FORBIDDEN, "Your current password isn’t right.");
            }
            throttle.succeeded(forEmail);
        }
        int version = users.setPassword(userId, newPasswordHash(newPassword, mail));
        jwt.forget(userId);
        return signedIn(account.user(), version, true);
    }

    /** A new password chosen by someone else (a parent, for their child). Ends all of that user's sign-ins. */
    public void resetPassword(UUID userId, String newPassword) {
        AppUser user = users.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No such user"));
        users.setPassword(userId, newPasswordHash(newPassword, user.email()));
        jwt.forget(userId);
    }

    /** {@code password} hashed for storing, once it passes the rules for {@code email}; otherwise 400 saying why. */
    public String newPasswordHash(String password, String email) {
        passwords.problem(password, email).ifPresent(problem -> {
            throw new ApiException(HttpStatus.BAD_REQUEST, problem);
        });
        return passwords.hash(password);
    }

    public UserView me(UUID userId) {
        UserRepository.Account a = users.account(userId).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unknown user"));
        return view(a.user(), a.passwordHash() != null);
    }

    // ------------------------------------------------------------------ helpers

    private void checkOwnerCode(String typed, String address) {
        if (props.ownerSetupCode() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This email can’t sign up here yet: the owner setup code isn’t set on the server.");
        }
        if (typed == null || typed.isBlank()) {
            throw ApiException.withCode(HttpStatus.FORBIDDEN, NEEDS_OWNER_CODE, "This email is for the owner. Enter the owner setup code.");
        }
        Throttle.Key forAddress = Throttle.address(address);
        throttle.guard(forAddress);
        boolean right = MessageDigest.isEqual(typed.strip().getBytes(StandardCharsets.UTF_8),
                props.ownerSetupCode().getBytes(StandardCharsets.UTF_8));
        if (!right) {
            throttle.failed(forAddress);
            throw ApiException.withCode(HttpStatus.FORBIDDEN, NEEDS_OWNER_CODE, "That owner setup code isn’t right.");
        }
    }

    private SignedIn signedIn(AppUser user, int passwordVersion, boolean hasPassword) {
        return new SignedIn(jwt.issue(user, passwordVersion), "Bearer", jwt.ttlSeconds(), view(user, hasPassword));
    }

    private static UserView view(AppUser u, boolean hasPassword) {
        return new UserView(u.id().toString(), u.email(), u.displayName(), u.role(), hasPassword);
    }

    private static String normalise(String email) {
        return email == null ? "" : email.strip().toLowerCase();
    }

    private static ApiException taken() {
        return new ApiException(HttpStatus.CONFLICT, "An account with this email already exists. Sign in instead.");
    }
}
