package mx.ferreteria.api.common.demo;

import javax.sql.DataSource;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Carga de datos demo SOLO con perfil "demo" y NUNCA con perfil "docker".
 *
 * <p>SpEL {@code @Profile("demo & !docker")} garantiza que este bean NO se
 * instancie en una ejecución de Docker (donde el perfil {@code docker} está
 * activo por línea de comandos del ENTRYPOINT, con precedencia sobre cualquier
 * env var). Combinado con el guard de arranque
 * {@link mx.ferreteria.api.common.demo.DemoGuard}, hace imposible correr el
 * seeder de datos dummy en un entorno de tipo producción.</p>
 *
 * <p>Ejecuta el script atómico vía una sola llamada execute(): pgjdbc envía
 * todo al servidor y PostgreSQL parsea el DO-block completo sin partir por ';'.</p>
 */
@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
@org.springframework.context.annotation.Profile("demo & !docker")
public class DemoDataRunner implements ApplicationRunner {

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String sql = new ClassPathResource("db/demo/05_dummy.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        sql = sql.lines()
                .filter(l -> !l.stripLeading().startsWith("\\"))
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
        try (var conn = dataSource.getConnection(); var st = conn.createStatement()) {
            st.execute(sql);
        }
        log.info("Datos demo cargados (perfil demo). NO usar este perfil en producción.");
    }
}
