package mx.ferreteria.api.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ZonaHorariaTest {

    @Test
    @DisplayName("Zona canónica es America/Mexico_City y hoy() la respeta")
    void zonaCanonicaEsMexicoCity() {
        assertThat(ZoneId.of("America/Mexico_City")).isEqualTo(ZonaHoraria.ZONA);
        assertThat(ZonaHoraria.hoy()).isEqualTo(LocalDate.now(ZonaHoraria.ZONA));
        assertThat(ZonaHoraria.ahora()).isNotNull();
        assertThat(ZonaHoraria.reloj().getZone()).isEqualTo(ZonaHoraria.ZONA);
    }
}
