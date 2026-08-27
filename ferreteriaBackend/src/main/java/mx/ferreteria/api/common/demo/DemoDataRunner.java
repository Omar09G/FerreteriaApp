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
 * Carga de datos demo SOLO con perfil "demo" (nunca productivo — PLAN §11 M0).
 * Ejecuta el script atómico vía una sola llamada execute(): pgjdbc envía todo al
 * servidor y PostgreSQL parsea el DO-block completo sin partir por ';'.
 */
@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
@org.springframework.context.annotation.Profile("demo")
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
