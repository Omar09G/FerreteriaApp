package mx.ferreteria.api.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Regression test SQL estático (DB-SEC-003/004/005/006). Lee cada migración
 * V*.sql del classpath y verifica:
 *   - DB-SEC-003: ninguna contiene "GRANT ALL" / "GRANT ALL PRIVILEGES".
 *     El rol ferreteria_app recibe GRANTs granulares (SELECT, INSERT, UPDATE,
 *     DELETE explícitos) con REVOKE en tablas ledger (DB-SEC-004) y TRUNCATE.
 *   - DB-SEC-004: V11 añade REVOKE DELETE en ven.ventas, ven.cuentas_cobrar,
 *     com.compras, com.cuentas_pagar, fin.cortes_caja.
 *   - DB-SEC-005: V11 añade SET search_path = pg_catalog, public, <schema> a
 *     cada función PL/pgSQL de V1 + V4.
 *   - DB-SEC-006: V11 redefine seg.fn_auditar() para excluir password_hash del
 *     JSONB persistido en seg.auditoria.
 *
 * No requiere Spring context (PathMatchingResourcePatternResolver es standalone).
 * No usa Testcontainers: solo valida sintaxis y contenido textual del SQL.
 */
class SqlPermissionsTest {

    private static final String MIGRATIONS_PATTERN = "classpath:db/migration/V*.sql";

    @Test
    @DisplayName("DB-SEC-003: ninguna migracion usa 'GRANT ALL' (GRANULAR en su lugar)")
    void migrationsDoNotUseGrantAll() throws Exception {
        List<String> files = migrationFiles();
        assertThat(files).as("debe haber al menos una migración V*.sql").isNotEmpty();

        List<String> offenders = new ArrayList<>();
        for (String file : files) {
            String sql = readMigration(file);
            if (containsGrantAll(sql)) {
                offenders.add(file);
            }
        }
        assertThat(offenders)
                .as("archivos que usan GRANT ALL (deben granularizarse)")
                .isEmpty();
    }

    @Test
    @DisplayName("DB-SEC-004: V11 contiene REVOKE DELETE en ledger tables")
    void v11_revokesDeleteOnLedgerTables() throws Exception {
        String sql = readMigration("V11__permissions_hardening.sql");
        assertThat(sql)
                .contains("REVOKE DELETE")
                .contains("ven.ventas")
                .contains("ven.cuentas_cobrar")
                .contains("com.compras")
                .contains("com.cuentas_pagar")
                .contains("fin.cortes_caja");
    }

    @Test
    @DisplayName("DB-SEC-005: V11 define SET search_path en funciones PL/pgSQL")
    void v11_setsSearchPathOnFunctions() throws Exception {
        String sql = readMigration("V11__permissions_hardening.sql");
        assertThat(sql)
                .contains("ALTER FUNCTION")
                .contains("SET search_path")
                // Las funciones PL/pgSQL de V1/V4 que más toca search_path hijack
                .contains("seg.fn_auditar")
                .contains("fin.fn_cerrar_turno")
                .contains("ven.fn_recalc_totales_venta");
    }

    @Test
    @DisplayName("DB-SEC-006: V11 redefine seg.fn_auditar() para excluir password_hash")
    void v11_redactsPasswordHashFromAuditoria() throws Exception {
        String sql = readMigration("V11__permissions_hardening.sql");
        assertThat(sql)
                .contains("CREATE OR REPLACE FUNCTION seg.fn_auditar")
                .contains("'password_hash'")
                // El operador JSONB `-` elimina la clave antes del INSERT.
                .contains("to_jsonb(OLD) - 'password_hash'")
                .contains("to_jsonb(NEW) - 'password_hash'");
    }

    // --------------------------------------------------------------- helpers

    /** Lista los archivos V*.sql del classpath usando Spring resource pattern. */
    private static List<String> migrationFiles() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources(MIGRATIONS_PATTERN);
        List<String> names = new ArrayList<>(resources.length);
        for (Resource r : resources) {
            String filename = r.getFilename();
            if (filename != null && filename.endsWith(".sql")) {
                names.add(filename);
            }
        }
        return names;
    }

    /** Lee el contenido de una migración del classpath como String UTF-8. */
    private static String readMigration(String filename) throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource resource = resolver.getResource("classpath:db/migration/" + filename);
        assertThat(resource.exists())
                .as("migracion %s debe existir en classpath:db/migration/", filename)
                .isTrue();
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Detecta "GRANT ALL" / "GRANT ALL PRIVILEGES" como sentencia SQL
     * (case-insensitive, normalizando whitespace). NO considera:
     *   - GRANT SELECT, INSERT, ... (granular)
     *   - Comentarios de línea "-- ..."
     *   - Bloques /* ... * / (multilínea)
     */
    private static boolean containsGrantAll(String sql) {
        // Quitar comentarios de línea
        String noLineComments = sql.replaceAll("--[^\\n]*", "");
        // Quitar comentarios de bloque (no greedy)
        String noBlockComments = noLineComments.replaceAll("/\\*.*?\\*/", "");
        // Normalizar whitespace y buscar token GRANT seguido de ALL
        String[] tokens = noBlockComments.split("\\s+");
        for (int i = 0; i < tokens.length - 1; i++) {
            if ("GRANT".equalsIgnoreCase(tokens[i])
                    && ("ALL".equalsIgnoreCase(tokens[i + 1])
                        || "ALL".equalsIgnoreCase(stripTrailingParens(tokens[i + 1])))) {
                return true;
            }
        }
        return false;
    }

    /** Quita un paréntesis o coma final de un token para casos como "ALL,". */
    private static String stripTrailingParens(String s) {
        if (s.endsWith(",") || s.endsWith(";")) {
            return s.substring(0, s.length() - 1);
        }
        return s;
    }
}