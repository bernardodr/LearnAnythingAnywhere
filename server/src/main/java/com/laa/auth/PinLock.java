package com.laa.auth;

import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import com.laa.web.ApiException;

/**
 * The parent PIN: a 4–8 digit code the account holder can set so a child using their account can't change what's
 * locked: the reader settings, the password, and the PIN itself. Reading, practice and the tutor stay open.
 * <ul>
 *   <li>Stored as a BCrypt hash. Wrong PINs are limited ({@link Throttle#pin}): after 5, it pauses for 15 minutes.</li>
 *   <li>A forgotten PIN is replaced with the account password ({@link #resetWithPassword}), which is limited like a
 *       sign-in.</li>
 * </ul>
 */
@Component
public class PinLock {

    private final UserRepository users;
    private final Passwords passwords;
    private final Throttle throttle;
    private final BCryptPasswordEncoder pins = new BCryptPasswordEncoder();

    PinLock(UserRepository users, Passwords passwords, Throttle throttle) {
        this.users = users;
        this.passwords = passwords;
        this.throttle = throttle;
    }

    public boolean isSet(UUID userId) {
        return users.pinHash(userId).isPresent();
    }

    /** Passes if no PIN is set or {@code pin} is right; otherwise 403 with {@code ask}. */
    public void check(UUID userId, String pin, String ask) {
        Optional<String> hash = users.pinHash(userId);
        if (hash.isEmpty()) return;
        Throttle.Key key = Throttle.pin(userId);
        throttle.guard(key);
        if (pin == null || !pins.matches(pin, hash.get())) {
            throttle.failed(key);
            throw new ApiException(HttpStatus.FORBIDDEN, ask);
        }
        throttle.succeeded(key);
    }

    /** Sets the PIN, or changes it with the current one. */
    public void set(UUID userId, String pin, String currentPin) {
        valid(pin);
        check(userId, currentPin, "Enter your current PIN to change it.");
        users.setPinHash(userId, pins.encode(pin));
    }

    /** Turns the lock off; needs the current PIN. */
    public void remove(UUID userId, String currentPin) {
        if (!isSet(userId)) return;
        check(userId, currentPin, "Enter your current PIN to turn the lock off.");
        users.setPinHash(userId, null);
    }

    /** A forgotten PIN: a new one, with the account password instead of the old PIN. */
    public void resetWithPassword(UUID userId, String password, String pin, String address) {
        valid(pin);
        UserRepository.Account account = users.account(userId).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unknown user"));
        if (account.passwordHash() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "Set a password for your account first (under Your password), then use it here.");
        }
        Throttle.Key forEmail = Throttle.email(account.user().email());
        Throttle.Key forAddress = Throttle.address(address);
        throttle.guard(forEmail, forAddress);
        if (!passwords.matches(password, account.passwordHash())) {
            throttle.failed(forEmail, forAddress);
            throw new ApiException(HttpStatus.FORBIDDEN, "That password isn’t right.");
        }
        throttle.succeeded(forEmail);
        throttle.succeeded(Throttle.pin(userId));
        users.setPinHash(userId, pins.encode(pin));
    }

    private static void valid(String pin) {
        if (pin == null || !pin.matches("\\d{4,8}")) throw new ApiException(HttpStatus.BAD_REQUEST, "A PIN is 4 to 8 digits.");
    }
}
