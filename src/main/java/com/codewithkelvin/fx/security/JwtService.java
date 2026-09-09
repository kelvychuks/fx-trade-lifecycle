package com.codewithkelvin.fx.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties properties;

    public String issue(AppUser user) {
        var now = Instant.now();
        var expiry = now.plusSeconds(properties.getExpirationSeconds());

        return Jwts.builder()
                .subject(user.getUsername())
                .issuer(properties.getIssuer())
                .claim("role", user.getRole().name())
                .claim("name", user.getFullName())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey())
                .compact();
    }

    /**
     * Empty rather than an exception for any invalid token: expired, wrong
     * signature, malformed. The filter treats all three the same way.
     */
    public Optional<AuthenticatedUser> verify(String token) {
        try {
            var claims = Jwts.parser()
                    .verifyWith(signingKey())
                    .requireIssuer(properties.getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return Optional.of(new AuthenticatedUser(
                    claims.getSubject(),
                    UserRole.valueOf(claims.get("role", String.class)),
                    claims.get("name", String.class)));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public long expirationSeconds() {
        return properties.getExpirationSeconds();
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public record AuthenticatedUser(String username, UserRole role, String fullName) {
    }
}
