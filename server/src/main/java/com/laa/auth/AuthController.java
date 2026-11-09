package com.laa.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.laa.web.ApiException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Prototype-only sign-in: email in, JWT out, no password. Emails in {@code laa.auth.owner-emails} are OWNER;
 * anyone else may pick LEARNER (default) or PARENT on first sign-in.
 */
@RestController
@RequestMapping("/auth")
class AuthController {

    record DevLoginRequest(@NotBlank @Email @Size(max = 254) String email, @Size(max = 80) String displayName, Role role) {}

    record UserView(String id, String email, String displayName, Role role) {
        static UserView of(AppUser u) {
            return new UserView(u.id().toString(), u.email(), u.displayName(), u.role());
        }
    }

    record DevLoginResponse(String token, String tokenType, long expiresIn, UserView user) {}

    private final UserRepository users;
    private final JwtService jwt;
    private final AuthProperties props;

    AuthController(UserRepository users, JwtService jwt, AuthProperties props) {
        this.users = users;
        this.jwt = jwt;
        this.props = props;
    }

    @PostMapping("/dev-login")
    DevLoginResponse devLogin(@Valid @RequestBody DevLoginRequest request) {
        if (!props.devLoginEnabled()) throw new ApiException(HttpStatus.NOT_FOUND, "Dev login is disabled");
        String email = request.email().strip().toLowerCase();
        boolean owner = props.isOwnerEmail(email);
        if (request.role() == Role.OWNER && !owner) {
            throw new ApiException(HttpStatus.FORBIDDEN, "The OWNER role is only for emails listed in LAA_OWNER_EMAILS");
        }
        AppUser user = users.findByEmail(email).orElseGet(() -> users.create(email,
                request.displayName() == null || request.displayName().isBlank() ? null : request.displayName().strip(),
                owner ? Role.OWNER : request.role() == null ? Role.LEARNER : request.role()));
        if (owner && user.role() != Role.OWNER) user = users.updateRole(user.id(), Role.OWNER);
        return new DevLoginResponse(jwt.issue(user), "Bearer", jwt.ttlSeconds(), UserView.of(user));
    }

    @GetMapping("/me")
    UserView me(@AuthenticationPrincipal Jwt principal) {
        return users.findById(CurrentUser.id(principal)).map(UserView::of)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unknown user"));
    }
}
