package mx.ferreteria.api.common.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import mx.ferreteria.api.common.mail.EmailPlantilla;

class DatosSensiblesTest {

    @Test
    @DisplayName("email conserva comportamiento histórico (ca***@dominio)")
    void email_mantiene_formato() {
        assertThat(DatosSensibles.enmascararEmail("cajero@ferreteria.local"))
                .isEqualTo("ca***@ferreteria.local");
        assertThat(DatosSensibles.enmascararEmail("a@x.mx")).isEqualTo("a*@x.mx");
        assertThat(DatosSensibles.enmascararEmail(null)).isEqualTo("***");
        assertThat(DatosSensibles.enmascararEmail("sin-arroba")).isEqualTo("***");
    }

    @Test
    @DisplayName("delegados históricos usan el dueño único")
    void delegados_usan_dueno_unico() {
        assertThat(EmailPlantilla.enmascararEmail("maria@tienda.mx"))
                .isEqualTo(DatosSensibles.enmascararEmail("maria@tienda.mx"));
    }

    @Test
    @DisplayName("teléfono deja solo últimos 3 dígitos")
    void telefono_ultimos_tres() {
        assertThat(DatosSensibles.enmascararTelefono("81-1111-1111")).isEqualTo("***111");
        assertThat(DatosSensibles.enmascararTelefono("+52 81 5000 1000")).isEqualTo("***000");
        assertThat(DatosSensibles.enmascararTelefono("12")).isEqualTo("***");
        assertThat(DatosSensibles.enmascararTelefono(null)).isEqualTo("***");
    }

    @Test
    @DisplayName("identificador (RFC/CURP/NSS) oculta todo menos últimos 3")
    void identificador_ultimos_tres() {
        assertThat(DatosSensibles.enmascararIdentificador("MFE120101JKL")).isEqualTo("***JKL");
        assertThat(DatosSensibles.enmascararIdentificador("PECJ800101MV7")).isEqualTo("***MV7");
        assertThat(DatosSensibles.enmascararIdentificador(null)).isEqualTo("***");
        assertThat(DatosSensibles.enmascararIdentificador("  ")).isEqualTo("***");
        assertThat(DatosSensibles.enmascararIdentificador("AB")).isEqualTo("***");
    }
}
