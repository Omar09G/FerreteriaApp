package mx.ferreteria.api.common.mail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EmailPlantillaTest {

    @Test
    @DisplayName("escapar neutraliza HTML inyectado en datos")
    void escapar_neutraliza() {
        assertThat(EmailPlantilla.escapar("<script>alert(1)</script>"))
                .isEqualTo("&lt;script&gt;alert(1)&lt;/script&gt;");
        assertThat(EmailPlantilla.escapar(null)).isEmpty();
    }

    @Test
    @DisplayName("documento incluye marca, título y bloque sin crudo")
    void documento_estructura() {
        String html = EmailPlantilla.documento("Mi título",
                "Intro aquí.",
                EmailPlantilla.detalles(EmailPlantilla.fila("Total", "$116.00")),
                "Nota final.");

        assertThat(html).contains(EmailPlantilla.MARCA);
        assertThat(html).contains("Mi título");
        assertThat(html).contains("$116.00");
        assertThat(html).contains("Nota final.");
        assertThat(html).contains("<table");
    }

    @Test
    @DisplayName("fila escapa etiqueta y valor")
    void fila_escapa() {
        String fila = EmailPlantilla.fila("Doc <1>", "a&b");

        assertThat(fila).contains("Doc &lt;1&gt;");
        assertThat(fila).contains("a&amp;b");
        assertThat(fila).doesNotContain("<1>");
    }
}
