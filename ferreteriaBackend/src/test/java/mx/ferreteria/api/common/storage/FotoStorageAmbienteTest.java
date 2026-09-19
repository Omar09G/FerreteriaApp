package mx.ferreteria.api.common.storage;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class FotoStorageAmbienteTest {

    private static FotoStorageService servicio(String ambiente, String publicUrl) {
        MinioProperties props = new MinioProperties(
                "http://localhost:9000", publicUrl, "minioadmin", "minioadmin",
                "ferreteria-fotos", 5);
        FotoStorageService service = new FotoStorageService(props);
        ReflectionTestUtils.setField(service, "ambiente", ambiente);
        return service;
    }

    @Test
    @DisplayName("dev permite URL pública HTTP (MinIO local)")
    void devPermiteHttp() {
        assertThatCode(() -> servicio("dev", "http://localhost:9000").validarAmbiente())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("prod exige URL pública HTTPS")
    void prodExigeHttps() {
        assertThatCode(() -> servicio("prod", "https://fotos.tienda.com").validarAmbiente())
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> servicio("prod", "http://minio:9000").validarAmbiente())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("ambiente distinto de dev/prod falla al arrancar")
    void ambienteInvalidoFalla() {
        assertThatThrownBy(() -> servicio("staging", "https://fotos.tienda.com").validarAmbiente())
                .isInstanceOf(IllegalStateException.class);
    }
}
