package com.codewithkelvin.fx.common;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI fxOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("FX Trade Lifecycle API")
                        .version("1.0.0")
                        .description("""
                                Trade capture, lifecycle control, position keeping and mark-to-market \
                                for FX spot and forwards.

                                **Try it:** `POST /api/auth/login` with one of

                                | Login | Password | Role | Can |
                                |---|---|---|---|
                                | `trader@fxdesk.dev` | `Trader1234!` | TRADER | book, amend, cancel |
                                | `mo@fxdesk.dev` | `Middle1234!` | MIDDLE_OFFICE | validate, confirm, settle, load rates |
                                | `viewer@fxdesk.dev` | `Viewer1234!` | VIEWER | read only |

                                then paste the token into **Authorize**.

                                A trader cannot confirm a trade they booked themselves — that is the \
                                four-eyes rule, and it is enforced in the service, not the UI.
                                """))
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme()
                                .name(BEARER)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
