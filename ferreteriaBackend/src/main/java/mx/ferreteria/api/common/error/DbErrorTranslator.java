package mx.ferreteria.api.common.error;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import mx.ferreteria.api.common.i18n.ErrorCode;

/**
 * Traduce SQLSTATE de PostgreSQL a ErrorCode del catálogo.
 * Cero parsing de textos: solo SQLSTATE.
 * Cubre ERRCODE clase P0 (negocio BD) + constraint estándar (23503, 23505, 23514).
 */
@Component
public class DbErrorTranslator {

    private static final Map<String, ErrorCode> BY_SQLSTATE = Map.ofEntries(
            // ERRCODE clase P0 — reglas de negocio
            Map.entry("P0100", ErrorCode.STOCK_INSUFICIENTE),
            Map.entry("P0200", ErrorCode.CREDITO_EXCEDIDO),
            Map.entry("P0201", ErrorCode.CREDITO_NO_DISPONIBLE),
            Map.entry("P0300", ErrorCode.TURNO_YA_CERRADO),
            Map.entry("P0301", ErrorCode.RECURSO_NO_ENCONTRADO),
            Map.entry("P0302", ErrorCode.VALOR_INVALIDO),
            Map.entry("P0400", ErrorCode.PROMOCION_AGOTADA),
            Map.entry("P0401", ErrorCode.PROMOCION_LIMITE_CLIENTE),
            Map.entry("P0999", ErrorCode.KARDEX_APPEND_ONLY),
            // Constraint estándar PostgreSQL
            Map.entry("23503", ErrorCode.REFERENCIA_INVALIDA),   // FK violation
            Map.entry("23505", ErrorCode.REGISTRO_DUPLICADO),   // unique_violation
            Map.entry("23514", ErrorCode.VALOR_INVALIDO)        // check_violation
    );

    /** Busca el primer SQLException con SQLSTATE conocido en la cadena de causas. */
    public Optional<ErrorCode> translate(Throwable root) {
        Throwable t = root;
        int depth = 0;
        while (t != null && depth < 15) {
            if (t instanceof SQLException sqlEx) {
                String state = sqlEx.getSQLState();
                if (state != null) {
                    ErrorCode mapped = BY_SQLSTATE.get(state);
                    if (mapped != null) {
                        return Optional.of(mapped);
                    }
                }
            }
            t = t.getCause();
            depth++;
        }
        return Optional.empty();
    }
}
