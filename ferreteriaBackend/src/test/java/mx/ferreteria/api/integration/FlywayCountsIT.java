package mx.ferreteria.api.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * DoD M0 (PLAN §11): CI migra PG vacío con Flyway y verifica el conteo completo,
 * más instalación limpia SIN datos demo (perfil demo NO activo).
 * Se salta automáticamente donde no haya docker/podman socket (CI de GitHub sí lo tiene).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "spring.flyway.locations=classpath:db/migration")
class FlywayCountsIT {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> pg =
            new PostgreSQLContainer<>("postgres:17-alpine").withDatabaseName("ferreteria");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", pg::getJdbcUrl);
        registry.add("spring.datasource.username", pg::getUsername);
        registry.add("spring.datasource.password", pg::getPassword);
        registry.add("app.request-id.mode", () -> "STRICT");
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TestRestTemplate rest;

    @Test
    @DisplayName("Flyway migra PG vacio: 72 tablas (71+refresh_tokens), 24 vistas, 7 funcs ERRCODE P0")
    void migrations_produceFullSchema() {
        Integer tables = jdbc.queryForObject("""
                SELECT count(*) FROM pg_tables
                WHERE schemaname NOT LIKE 'pg_%' AND schemaname <> 'information_schema'
                  AND tablename <> 'flyway_schema_history'
                """, Integer.class);
        Integer views = jdbc.queryForObject("""
                SELECT count(*) FROM pg_views
                WHERE schemaname NOT LIKE 'pg_%' AND schemaname <> 'information_schema'
                """, Integer.class);
        Integer errcodedFuncs = jdbc.queryForObject("""
                SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
                WHERE n.nspname IN ('inv','ven','fin') AND p.prokind = 'f'
                  AND position('USING ERRCODE' in pg_get_functiondef(p.oid)) > 0
                """, Integer.class);

        assertThat(tables).isEqualTo(72);   // 71 del diseño + refresh_tokens (backend)
        assertThat(views).isEqualTo(24);
        assertThat(errcodedFuncs).isEqualTo(7);

        Integer refreshTables = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name='refresh_tokens'",
                Integer.class);
        assertThat(refreshTables).isEqualTo(1);          // migración V2 del backend
    }

    @Test
    @DisplayName("Instalacion limpia SIN demo: cero ventas y health UP (perfil demo apagado)")
    void cleanInstall_hasNoDemoData_andHealthIsUp() {
        Integer ventas = jdbc.queryForObject("SELECT count(*) FROM ven.ventas", Integer.class);
        assertThat(ventas).isZero();

        var health = rest.getForEntity("/actuator/health", String.class);
        assertThat(health.getStatusCode().value()).isEqualTo(200);
        assertThat(health.getBody()).contains("\"UP\"");
    }
}
