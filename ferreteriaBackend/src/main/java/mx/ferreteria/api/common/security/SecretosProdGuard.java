package mx.ferreteria.api.common.security;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Guard de arranque: en contexto de producción rechaza secretos placeholder
 * bien conocidos (defaults de desarrollo). Es el complemento de
 * {@link JwtService} (fail-fast del JWT) y de {@code AuthService.validarAmbiente}
 * (cookies Secure en prod).
 *
 * <p>Los defaults de {@code application.yml} ({@code cambia_app_seguro},
 * {@code minioadmin}, {@code ferreteria}...) existen solo para desarrollo
 * local sin entorno; si llegan a prod, el arranque aborta con un mensaje
 * claro en lugar de exponer storage/broker/BD con credenciales públicas.</p>
 *
 * <p>"Contexto de producción" usa la misma definición que el guard de demo:
 * perfil {@code docker} activo, cookie {@code Secure}, o
 * {@code FERRETERIA_ENV=prod}.</p>
 */
@Slf4j
@Component
@Order(1) // Después del guard de demo (que es @Order(0))
@RequiredArgsConstructor
public class SecretosProdGuard implements ApplicationRunner {

    /** Secretos placeholder que nunca deben llegar a producción (minúsculas). */
    private static final Set<String> PLACEHOLDERS = Set.of(
            "", "cambia_app_seguro", "cambia_minio_seguro", "cambia_rabbit_seguro",
            "minioadmin", "ferreteria", "test", "guest", "cambiar_dev_only");

    private final Environment env;

    @Value("${spring.datasource.password:cambia_app_seguro}")
    private String pgPassword;

    @Value("${app.minio.secret-key:minioadmin}")
    private String minioSecret;

    @Value("${spring.rabbitmq.password:ferreteria}")
    private String rabbitPassword;

    @Value("${app.auth.cookie.secure:false}")
    private boolean cookieSecure;

    @Value("${FERRETERIA_ENV:}")
    private String ferreteriaEnv;

    @Override
    public void run(ApplicationArguments args) {
        if (!esContextoProd(
                Arrays.asList(env.getActiveProfiles()), cookieSecure, ferreteriaEnv)) {
            return;
        }
        Map<String, String> secretos = new LinkedHashMap<>();
        secretos.put("PG_PASSWORD/spring.datasource.password", pgPassword);
        secretos.put("MINIO_SECRET_KEY/app.minio.secret-key", minioSecret);
        secretos.put("RABBITMQ_PASSWORD/spring.rabbitmq.password", rabbitPassword);
        var debiles = secretos.entrySet().stream()
                .filter(e -> esPlaceholder(e.getValue()))
                .map(Map.Entry::getKey)
                .toList();
        if (!debiles.isEmpty()) {
            String msg = """
                    ============================================================
                     BLOQUEO DE ARRANQUE: secretos placeholder en contexto de
                     PRODUCCIÓN: %s.
                       perfiles activos: %s
                       app.cookie.secure: %s
                       FERRETERIA_ENV: %s
                     Solución: definir secretos reales vía entorno/Vault
                     (PG_PASSWORD, MINIO_SECRET_KEY, RABBITMQ_PASSWORD).
                    ============================================================""".formatted(debiles,
                    Arrays.toString(env.getActiveProfiles()), cookieSecure, ferreteriaEnv);
            log.error(msg);
            throw new IllegalStateException(
                    "Secretos placeholder en producción: " + debiles + ". Abortando arranque.");
        }
    }

    static boolean esContextoProd(java.util.List<String> perfiles, boolean secure, String ferreteriaEnv) {
        return perfiles.contains("docker")
                || secure
                || "prod".equalsIgnoreCase(String.valueOf(ferreteriaEnv).trim());
    }

    static boolean esPlaceholder(String valor) {
        String v = String.valueOf(valor).trim().toLowerCase(java.util.Locale.ROOT);
        return PLACEHOLDERS.contains(v) || v.startsWith("cambia_");
    }
}
