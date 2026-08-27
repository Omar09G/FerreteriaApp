package mx.ferreteria.api.common.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import mx.ferreteria.api.common.error.DbErrorTranslator;

/** Fija el contrato ERRCODE BD → ErrorCode → HTTP de PLAN §4.3. */
class ErrorCodeContractTest {

    private final DbErrorTranslator translator = new DbErrorTranslator();

    private static final Map<String, Expected> CONTRATO = Map.of(
            "P0100", new Expected(ErrorCode.STOCK_INSUFICIENTE, 409),
            "P0200", new Expected(ErrorCode.CREDITO_EXCEDIDO, 422),
            "P0201", new Expected(ErrorCode.CREDITO_NO_DISPONIBLE, 422),
            "P0300", new Expected(ErrorCode.TURNO_YA_CERRADO, 409),
            "P0301", new Expected(ErrorCode.RECURSO_NO_ENCONTRADO, 404),
            "P0302", new Expected(ErrorCode.VALOR_INVALIDO, 400),
            "P0400", new Expected(ErrorCode.PROMOCION_AGOTADA, 409),
            "P0401", new Expected(ErrorCode.PROMOCION_LIMITE_CLIENTE, 409),
            "P0999", new Expected(ErrorCode.KARDEX_APPEND_ONLY, 409));

    private record Expected(ErrorCode code, int http) { }

    @Test
    @DisplayName("cada ERRCODE P0 traduce al ErrorCode y HTTP del contrato")
    void errcodeTable_mapsExactly() {
        CONTRATO.forEach((sqlstate, esperado) -> {
            var ex = new SQLException("msg", sqlstate, 1);
            assertThat(translator.translate(ex)).contains(esperado.code());
            assertThat(esperado.code().http().value())
                    .as("%s -> %s", sqlstate, esperado.code())
                    .isEqualTo(esperado.http());
        });
    }

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    @DisplayName("todas las claves siguen el formato error.<modulo>.<slug>")
    void keys_followConvention(ErrorCode code) {
        assertThat(code.key()).matches("^error\\.(auth|validacion|negocio|interno)\\.[a-z0-9-]+$");
    }
}
