package com.laa.profile;

import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.laa.auth.CurrentUser;
import com.laa.auth.PinLock;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Reader settings (BRIEF section 10, {@code GET/PUT /profiles/{id}}) and the parent PIN that locks them:
 * <ul>
 *   <li>{@code GET /profiles/me}: the signed-in account's settings, with {@code pinSet}</li>
 *   <li>{@code GET|PUT /profiles/{id}}: the account's own profile; a change needs {@code X-Parent-Pin} once a PIN is set</li>
 *   <li>{@code PUT /profiles/pin}: set the PIN, or change it with the current one; {@code DELETE /profiles/pin} (with
 *       {@code X-Parent-Pin}): turn the lock off; {@code POST /profiles/pin/reset}: a forgotten PIN, replaced with the
 *       account password</li>
 * </ul>
 */
@RestController
@RequestMapping("/profiles")
class ProfileController {

    record PinRequest(String pin, String currentPin) {}

    record PinReset(String password, String pin) {}

    private final Profiles profiles;
    private final PinLock lock;

    ProfileController(Profiles profiles, PinLock lock) {
        this.profiles = profiles;
        this.lock = lock;
    }

    @GetMapping("/me")
    Profiles.ProfileView me(@AuthenticationPrincipal Jwt jwt) {
        return profiles.mine(CurrentUser.id(jwt));
    }

    @GetMapping("/{id}")
    Profiles.ProfileView get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return profiles.get(CurrentUser.id(jwt), id);
    }

    @PutMapping("/{id}")
    Profiles.ProfileView put(@PathVariable UUID id, @RequestBody Profiles.Settings settings,
                             @RequestHeader(name = "X-Parent-Pin", required = false) String pin, @AuthenticationPrincipal Jwt jwt) {
        return profiles.update(CurrentUser.id(jwt), id, settings, pin);
    }

    @PutMapping("/pin")
    Map<String, Object> pin(@RequestBody PinRequest r, @AuthenticationPrincipal Jwt jwt) {
        lock.set(CurrentUser.id(jwt), r.pin(), r.currentPin());
        return Map.of("pinSet", true);
    }

    @DeleteMapping("/pin")
    Map<String, Object> removePin(@RequestHeader(name = "X-Parent-Pin", required = false) String pin, @AuthenticationPrincipal Jwt jwt) {
        lock.remove(CurrentUser.id(jwt), pin);
        return Map.of("pinSet", false);
    }

    @PostMapping("/pin/reset")
    Map<String, Object> resetPin(@RequestBody PinReset r, @AuthenticationPrincipal Jwt jwt, HttpServletRequest http) {
        lock.resetWithPassword(CurrentUser.id(jwt), r.password(), r.pin(), http.getRemoteAddr());
        return Map.of("pinSet", true);
    }
}
