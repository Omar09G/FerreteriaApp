package mx.ferreteria.api.common.demo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Guard de arranque: impide que el perfil {@code demo} coexista con
 * indicadores de producción. Es la TERCERA capa de defensa (las otras dos son
 * {@code @Profile("demo & !docker")} en {@link DemoDataRunner} y el
 * {@code -Dspring.profiles.active=docker} en el ENTRYPOINT del Dockerfile).
 *
 * <p>Si por alguna razón alguien pasa
 * {@code SPRING_PROFILES_ACTIVE=docker,demo} y logra sortear el guard del
 * Profile, este runner falla al arranque con un mensaje claro en lugar de
 * cargar datos dummy en una BD productiva.</p>
 *
 * <p>Considera "contexto de producción" si CUALQUIERA de estas condiciones
 * se cumple:</p>
 * <ul>
 *   <li>El perfil {@code docker} está activo (imagen del backend en compose).</li>
 *   <li>La cookie de auth está marcada {@code Secure} (HTTPS activo).</li>
 *   <li>La variable {@code FERRETERIA_ENV=prod} está seteada (override explícito).</li>
 * </ul>
 */
@Slf4j
@Component
@Order(0)   // Antes que DemoDataRunner (que es @Order(100))
@RequiredArgsConstructor
public class DemoGuard implements ApplicationRunner {

    private static final String DEMO_PROFILE = "demo";

    private final Environment env;

    @Value("${app.auth.cookie.secure:false}")
    private boolean cookieSecure;

    @Value("${FERRETERIA_ENV:}")
    private String ferreteriaEnv;

    @Override
    public void run(ApplicationArguments args) {
        boolean demoActivo = Arrays.asList(env.getActiveProfiles()).contains(DEMO_PROFILE);
        if (!demoActivo) {
            return;
        }

        boolean contextoProd =
                Arrays.asList(env.getActiveProfiles()).contains("docker")
                || cookieSecure
                || "prod".equalsIgnoreCase(ferreteriaEnv);

        if (contextoProd) {
            String msg = "============================================================\n"
                    + " BLOQUEO DE ARRANQUE: perfil '" + DEMO_PROFILE + "' activo en\n"
                    + " contexto de PRODUCCIÓN. El seeder de datos dummy NO se ejecuta.\n"
                    + "   perfiles activos: " + Arrays.toString(env.getActiveProfiles()) + "\n"
                    + "   app.cookie.secure: " + cookieSecure + "\n"
                    + "   FERRETERIA_ENV: " + ferreteriaEnv + "\n"
                    + " Solución: quitar 'demo' de SPRING_PROFILES_ACTIVE y usar\n"
                    + " únicamente 'docker' (o el perfil que aplique).\n"
                    + "============================================================";
            log.error(msg);
            throw new IllegalStateException(
                    "Perfil 'demo' activado en contexto de producción. Abortando arranque.");
        }

        log.warn("================================================================"
                + "\n PERFIL DEMO ACTIVO — se cargarán datos dummy al arranque."
                + "\n NO exponer este perfil a internet."
                + "\n================================================================");
    }
}
