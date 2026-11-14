package com.laa.profile;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.laa.auth.CurrentUser;

/**
 * Reader settings (BRIEF section 10, {@code GET/PUT /profiles/{id}}):
 * <ul>
 *   <li>{@code GET /profiles/me}: the signed-in learner's own settings</li>
 *   <li>{@code GET|PUT /profiles/{id}}: a profile its learner or their parent may see; a parent edits a child's with
 *       the {@code X-Parent-Pin} header once a PIN is set</li>
 *   <li>{@code GET|POST /profiles/children}: a parent's children, and adding one</li>
 *   <li>{@code PUT /profiles/pin}: a parent sets or changes their PIN</li>
 * </ul>
 */
@RestController
@RequestMapping("/profiles")
class ProfileController {

    record ChildRequest(String email, String displayName, Integer age, Boolean childSafe) {}

    record PinRequest(String pin, String currentPin) {}

    private final Profiles profiles;

    ProfileController(Profiles profiles) {
        this.profiles = profiles;
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

    @GetMapping("/children")
    Map<String, Object> children(@AuthenticationPrincipal Jwt jwt) {
        return profiles.parentState(CurrentUser.id(jwt));
    }

    @PostMapping("/children")
    ResponseEntity<Profiles.ChildView> addChild(@RequestBody ChildRequest r, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(profiles.addChild(CurrentUser.id(jwt), r.email(), r.displayName(), r.age(), r.childSafe()));
    }

    @PutMapping("/pin")
    Map<String, Object> pin(@RequestBody PinRequest r, @AuthenticationPrincipal Jwt jwt) {
        profiles.setPin(CurrentUser.id(jwt), r.pin(), r.currentPin());
        return Map.of("pinSet", true);
    }
}
