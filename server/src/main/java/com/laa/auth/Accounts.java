package com.laa.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.laa.auth.EmailLinks.Purpose;
import com.laa.mail.Mailer;
import com.laa.web.ApiException;

/**
 * Signing up, signing in and passwords: the only way to a sign-in token.
 * <ul>
 *   <li><b>Sign-up</b>: email and a password that passes {@link Passwords#problem}. Accounts are for adults; a child
 *       uses a parent's account, locked with the parent PIN ({@link PinLock}). An email on the owner list also needs
 *       the owner setup code ({@code laa.auth.owner-setup-code}), because nobody's email is
 *       checked: without it, whoever signed up first with the owner's email would get the owner console.</li>
 *   <li><b>Sign-in</b>: a wrong password and an unknown email get the same answer and take the same time.
 *       {@link Throttle} limits tries per email and per network address. Hashes made with weaker settings are redone.
 *       An OWNER whose email is no longer on the list becomes a learner.</li>
 *   <li><b>Password changes</b> need the parent PIN once one is set, and end every older sign-in; the person who
 *       changed it gets a fresh token, so they stay signed in.</li>
 *   <li><b>Email</b>: sign-up sends a link to verify the address; until it's used, the account can't use AI (the
 *       gateway's {@code Allowance} checks). A forgotten password gets a one-use reset link, and the answer is the same
 *       whether or not the email has an account. Using either link proves the address. {@link Throttle} limits new
 *       accounts per network address and emails per address.</li>
 *   <li><b>Dev sign-in</b> (email only) is for local development and tests, when {@code laa.auth.dev-login-enabled}.
 *       Its accounts count as verified.</li>
 * </ul>
 */
@Service
public class Accounts {

    public record UserView(String id, String email, String displayName, Role role, boolean hasPassword, boolean emailVerified) {}

    public record SignedIn(String token, String tokenType, long expiresIn, UserView user) {}

    public static final String NEEDS_OWNER_CODE = "owner-setup-code";
    private static final String WRONG = "Email or password is incorrect.";

    private final UserRepository users;
    private final Passwords passwords;
    private final Throttle throttle;
    private final JwtService jwt;
    private final AuthProperties props;
    private final EmailLinks links;
    private final Mailer mailer;
    private final PinLock lock;

    Accounts(UserRepository users, Passwords passwords, Throttle throttle, JwtService jwt, AuthProperties props, EmailLinks links,
             Mailer mailer, PinLock lock) {
        this.users = users;
        this.passwords = passwords;
        this.throttle = throttle;
        this.jwt = jwt;
        this.props = props;
        this.links = links;
        this.mailer = mailer;
        this.lock = lock;
    }

    public SignedIn signUp(String email, String password, String displayName, String ownerSetupCode, String address) {
        String mail = normalise(email);
        Throttle.Key fromAddress = Throttle.signUps(address);
        throttle.guard(fromAddress);
        Role chosen = Role.LEARNER;
        if (props.isOwnerEmail(mail)) {
            chosen = Role.OWNER;
            checkOwnerCode(ownerSetupCode, address);
        }
        if (users.findByEmail(mail).isPresent()) throw taken();
        String hash = newPasswordHash(password, mail);
        String name = displayName == null || displayName.isBlank() ? null : displayName.strip();
        AppUser user;
        try {
            user = users.create(mail, name, chosen, hash);
        } catch (DuplicateKeyException e) {
            throw taken();
        }
        throttle.used(fromAddress);
        sendVerification(user);
        return signedIn(user, 0, true);
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
        UserRepository.Account account = users.accountByEmail(mail).orElseGet(() -> {
            try {
                return new UserRepository.Account(users.create(mail, displayName == null || displayName.isBlank() ? null : displayName.strip(),
                        owner ? Role.OWNER : Role.LEARNER), null, 0);
            } catch (DuplicateKeyException e) { // two first sign-ins at once: the other one made it
                return users.accountByEmail(mail).orElseThrow();
            }
        });
        AppUser user = account.user();
        users.markVerified(user.id());
        if (owner && user.role() != Role.OWNER) user = users.updateRole(user.id(), Role.OWNER);
        if (!owner && user.role() == Role.OWNER) user = users.updateRole(user.id(), Role.LEARNER);
        return signedIn(user, account.passwordVersion(), account.passwordHash() != null);
    }

    /**
     * Sets a new password for the signed-in user, ending their other sign-ins; returns their new sign-in. Needs the
     * parent PIN once one is set, and the current password unless the account has none yet (made before passwords,
     * or by dev sign-in).
     */
    public SignedIn changePassword(UUID userId, String currentPassword, String newPassword, String pin, String address) {
        lock.check(userId, pin, "Enter the parent PIN to change the password.");
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

    // ------------------------------------------------------------------ email links

    /** Uses a verification link. */
    public void verifyEmail(String token) {
        UUID userId = links.redeem(token, Purpose.VERIFY).orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST,
                "This link has expired or has already been used. Sign in and send a new one from your shelf."));
        users.markVerified(userId);
    }

    /** Sends the signed-in user a new verification link. False if there's nothing to verify. */
    public boolean resendVerification(UUID userId) {
        AppUser user = users.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unknown user"));
        if (users.emailVerified(userId)) return false;
        throttle.guard(Throttle.emailsTo(user.email()));
        sendVerification(user);
        return true;
    }

    /**
     * Sends a reset link if {@code email} has an account. Answers the same either way, and never says whether an
     * email went (an address that's had its share of emails just gets nothing more for a while).
     */
    public void requestPasswordReset(String email, String address) {
        String mail = normalise(email);
        Throttle.Key fromAddress = Throttle.resetRequests(address);
        throttle.guard(fromAddress);
        throttle.used(fromAddress);
        Throttle.Key toEmail = Throttle.emailsTo(mail);
        users.findByEmail(mail).ifPresent(user -> {
            if (throttle.paused(toEmail)) return;
            throttle.used(toEmail);
            mailer.send(user.email(), "Reset your Learn Anything Anywhere password", String.join("\n",
                    "Hi " + greeting(user) + ",",
                    "",
                    "Someone asked to reset the password for your Learn Anything Anywhere account. To choose a new one, open this link:",
                    "",
                    links.create(user.id(), Purpose.RESET),
                    "",
                    "It works once, for 30 minutes. Choosing a new password signs you out everywhere else.",
                    "",
                    "If you didn’t ask for this, you can ignore this email: your password hasn’t changed."));
        });
    }

    /** Uses a reset link: the new password must pass the rules first. Ends every older sign-in, and signs in here. */
    public SignedIn resetPassword(String token, String newPassword) {
        String expired = "This reset link has expired or has already been used. Ask for a new one from the sign-in page.";
        UUID userId = links.peek(token, Purpose.RESET).orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, expired));
        AppUser user = users.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, expired));
        String hash = newPasswordHash(newPassword, user.email());
        if (links.redeem(token, Purpose.RESET).isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, expired);
        int version = users.setPassword(userId, hash);
        users.markVerified(userId);
        jwt.forget(userId);
        throttle.succeeded(Throttle.email(user.email()));
        if (user.role() == Role.OWNER && !props.isOwnerEmail(user.email())) user = users.updateRole(userId, Role.LEARNER);
        return signedIn(user, version, true);
    }

    private void sendVerification(AppUser user) {
        throttle.used(Throttle.emailsTo(user.email()));
        mailer.send(user.email(), "Confirm your email for Learn Anything Anywhere", String.join("\n",
                "Hi " + greeting(user) + ",",
                "",
                "Welcome to Learn Anything Anywhere. Please confirm this is your email address by opening this link:",
                "",
                links.create(user.id(), Purpose.VERIFY),
                "",
                "It works for 24 hours. Until you confirm, you can look around, but starting a textbook waits.",
                "",
                "If you didn’t make an account, you can ignore this email."));
    }

    private static String greeting(AppUser user) {
        return user.displayName() == null || user.displayName().isBlank() ? "there" : user.displayName();
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

    private UserView view(AppUser u, boolean hasPassword) {
        return new UserView(u.id().toString(), u.email(), u.displayName(), u.role(), hasPassword, users.emailVerified(u.id()));
    }

    private static String normalise(String email) {
        return email == null ? "" : email.strip().toLowerCase();
    }

    private static ApiException taken() {
        return new ApiException(HttpStatus.CONFLICT, "An account with this email already exists. Sign in instead.");
    }
}
