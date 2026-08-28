package mx.ferreteria.api.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import mx.ferreteria.api.common.error.ValidacionException;
import mx.ferreteria.api.common.i18n.ErrorCode;

class RangoFechasTest {

    @Test
    @DisplayName("sin parámetros: default a hoy (inicio = fin = fecha actual)")
    void sinParametros_defaultHoy() {
        var r = RangoFechas.of(null, null);
        assertThat(r.inicio()).isEqualTo(LocalDate.now());
        assertThat(r.fin()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("con fechaFin vacío: fin hereda el inicio")
    void sinFin_heredaInicio() {
        var r = RangoFechas.of(LocalDate.of(2026, 1, 15), null);
        assertThat(r.inicio()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(r.fin()).isEqualTo(LocalDate.of(2026, 1, 15));
    }

    @Test
    @DisplayName("rango válido se conserva")
    void rangoValido() {
        var r = RangoFechas.of(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        assertThat(r.inicio()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(r.fin()).isEqualTo(LocalDate.of(2026, 1, 31));
    }

    @Test
    @DisplayName("fechaInicio después de fechaFin -> 400 VALOR_INVALIDO")
    void rangoInvertido_rechazado() {
        assertThatThrownBy(() -> RangoFechas.of(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 31)))
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.VALOR_INVALIDO));
    }
}