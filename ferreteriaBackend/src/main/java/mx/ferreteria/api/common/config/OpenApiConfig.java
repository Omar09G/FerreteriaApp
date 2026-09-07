package mx.ferreteria.api.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/** Swagger UI con botón Authorize — DoD M1. */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerJWT";
    private static final String COOKIE = "cookieAuth";

    @Bean
    public OpenAPI openAPI() {
        // BACK-UI-002: el backend usa cookies HttpOnly (`at` access, `rt` refresh)
        // como metodo canonico de autenticacion. El esquema Bearer sigue siendo
        // valido para integraciones server-to-server, pero los clientes del
        // navegador usan cookies que JS no puede leer. Documentar ambos para
        // que Swagger UI ofrezca Authorize con cualquiera.
        return new OpenAPI()
                .info(new Info()
                        .title("Ferretería API")
                        .version("v1")
                        .description("""
                                REST API del sistema de ferretería (PLAN_IMPLEMENTACION_BACKEND.md).

                                **Autenticación**: cookies HttpOnly (`at` access, `rt` refresh) — método principal.
                                Alternativamente header `Authorization: Bearer <jwt>` para integraciones server-to-server.

                                **CSRF**: header `X-XSRF-TOKEN` (valor de la cookie `XSRF-TOKEN`) en métodos mutating.
                                """))
                .components(new Components()
                        .addSecuritySchemes(BEARER, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Authorization: Bearer <access_token>"))
                        .addSecuritySchemes(COOKIE, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("at")
                                .description("Cookie HttpOnly con el access token (nombre configurable via AUTH_COOKIE_ACCESS_NAME)")))
                .addSecurityItem(new SecurityRequirement().addList(COOKIE));
    }
}

