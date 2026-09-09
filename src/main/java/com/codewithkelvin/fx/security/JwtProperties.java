package com.codewithkelvin.fx.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** HMAC signing key. At least 32 characters. */
    private String secret;

    /** Access-token lifetime in seconds. */
    private long expirationSeconds = 28800;

    private String issuer = "fx-trade-lifecycle";
}
