package com.codewithkelvin.fx.security;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class AuthController {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CurrentUserService currentUserService;

    @PostMapping("/login")
    @Operation(summary = "Exchange credentials for a bearer token")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        var user = userRepository.findByUsername(request.username().trim().toLowerCase()).orElse(null);

        // Same response whether the user is unknown, inactive, or the password
        // is wrong: an attacker should not learn which usernames exist.
        if (user == null || !user.isActive()
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(new LoginResponse(
                jwtService.issue(user),
                jwtService.expirationSeconds(),
                user.getUsername(),
                user.getFullName(),
                user.getRole()));
    }

    @GetMapping("/me")
    @Operation(summary = "Who the bearer token belongs to")
    public MeResponse me() {
        var user = currentUserService.require();
        return new MeResponse(user.getUsername(), user.getFullName(), user.getRole());
    }

    public record LoginRequest(
            @NotBlank(message = "username is required") String username,
            @NotBlank(message = "password is required") String password) {
    }

    public record LoginResponse(String token, long expiresInSeconds, String username,
                                String fullName, UserRole role) {
    }

    public record MeResponse(String username, String fullName, UserRole role) {
    }
}
