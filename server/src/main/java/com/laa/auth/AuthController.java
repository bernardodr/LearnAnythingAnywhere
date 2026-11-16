package com.laa.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Signing up and in ({@link Accounts}):
 * <ul>
 *   <li>{@code GET /auth/options}: what the title page offers (dev sign-in on or off, password length)</li>
 *   <li>{@code POST /auth/sign-up}, {@code POST /auth/sign-in}: email and password, token out</li>
 *   <li>{@code POST /auth/dev-login}: email only, when dev sign-in is on</li>
 *   <li>{@code PUT /auth/password}: the signed-in user's new password; a fresh token out</li>
 *   <li>{@code GET /auth/me}</li>
 * </ul>
 * Passwords are never put through bean validation, so a rejected value can't end up in an error message.
 */
@RestController
@RequestMapping("/auth")
class AuthController {

    record Options(boolean devLogin, int passwordMinLength, int passwordMaxLength) {}

    record SignUpRequest(@NotBlank @Email @Size(max = 254) String email, String password, @Size(max = 80) String displayName,
                         Role role, String ownerSetupCode) {}

    record SignInRequest(@NotBlank @Size(max = 254) String email, String password) {}

    record DevLoginRequest(@NotBlank @Email @Size(max = 254) String email, @Size(max = 80) String displayName, Role role) {}

    record PasswordRequest(String currentPassword, String newPassword) {}

    private final Accounts accounts;
    private final AuthProperties props;

    AuthController(Accounts accounts, AuthProperties props) {
        this.accounts = accounts;
        this.props = props;
    }

    @GetMapping("/options")
    Options options() {
        return new Options(props.devLoginEnabled(), Passwords.MIN_LENGTH, Passwords.MAX_LENGTH);
    }

    @PostMapping("/sign-up")
    @ResponseStatus(HttpStatus.CREATED)
    Accounts.SignedIn signUp(@Valid @RequestBody SignUpRequest r, HttpServletRequest http) {
        return accounts.signUp(r.email(), r.password(), r.displayName(), r.role(), r.ownerSetupCode(), http.getRemoteAddr());
    }

    @PostMapping("/sign-in")
    Accounts.SignedIn signIn(@Valid @RequestBody SignInRequest r, HttpServletRequest http) {
        return accounts.signIn(r.email(), r.password(), http.getRemoteAddr());
    }

    @PostMapping("/dev-login")
    Accounts.SignedIn devLogin(@Valid @RequestBody DevLoginRequest r) {
        return accounts.devLogin(r.email(), r.displayName(), r.role());
    }

    @PutMapping("/password")
    Accounts.SignedIn password(@RequestBody PasswordRequest r, @AuthenticationPrincipal Jwt jwt, HttpServletRequest http) {
        return accounts.changePassword(CurrentUser.id(jwt), r.currentPassword(), r.newPassword(), http.getRemoteAddr());
    }

    @GetMapping("/me")
    Accounts.UserView me(@AuthenticationPrincipal Jwt principal) {
        return accounts.me(CurrentUser.id(principal));
    }
}
