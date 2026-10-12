package mx.ferreteria.api.seg.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import mx.ferreteria.api.common.security.GeoIpProperties;

class GeoIpServiceTest {

    private static GeoIpService sinBase() {
        return new GeoIpService(new GeoIpProperties(""));
    }

    @Test
    @DisplayName("sin GEOIP_DB_PATH: deshabilitado y buscar devuelve vacío")
    void sinDb_deshabilitado() {
        GeoIpService svc = sinBase();

        assertThat(svc.habilitado()).isFalse();
        assertThat(svc.buscar("8.8.8.8")).isEmpty();
        assertThat(svc.buscar(null)).isEmpty();
        assertThat(svc.buscar("  ")).isEmpty();
    }

    @Test
    @DisplayName("ruta inexistente: deshabilitado sin lanzar")
    void rutaInexistente_deshabilitado() {
        GeoIpService svc = new GeoIpService(new GeoIpProperties("/no/existe/GeoLite2.mmdb"));

        assertThat(svc.habilitado()).isFalse();
        assertThat(svc.buscar("8.8.8.8")).isEmpty();
    }

    @Test
    @DisplayName("IP ilegible con base ausente: vacío (fail-open)")
    void ipIlegible_vacio() {
        assertThat(sinBase().buscar("no-es-ip")).isEmpty();
    }

    @Test
    @DisplayName("Ubicacion.texto omite partes vacías")
    void texto_omiteVacios() {
        assertThat(new GeoIpService.Ubicacion("Guadalajara", "Jalisco", "México").texto())
                .isEqualTo("Guadalajara, Jalisco, México");
        assertThat(new GeoIpService.Ubicacion(null, "Jalisco", "México").texto())
                .isEqualTo("Jalisco, México");
        assertThat(new GeoIpService.Ubicacion(null, null, "México").texto())
                .isEqualTo("México");
        assertThat(new GeoIpService.Ubicacion(null, " ", null).texto()).isEmpty();
    }

    @Test
    @DisplayName("nombre prefiere español, luego inglés, luego el primero")
    void nombre_prioridadEs() {
        assertThat(GeoIpService.nombre(Map.of("en", "Mexico", "es", "México")))
                .isEqualTo("México");
        assertThat(GeoIpService.nombre(Map.of("en", "Mexico")))
                .isEqualTo("Mexico");
        assertThat(GeoIpService.nombre(Map.of())).isNull();
        assertThat(GeoIpService.nombre(null)).isNull();
    }
}
