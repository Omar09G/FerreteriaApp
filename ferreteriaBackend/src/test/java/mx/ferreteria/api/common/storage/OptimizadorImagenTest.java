package mx.ferreteria.api.common.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import mx.ferreteria.api.common.error.ValidacionException;
import mx.ferreteria.api.common.i18n.ErrorCode;

class OptimizadorImagenTest {

    private static byte[] imagen(String formato, int w, int h, boolean conAlfa) throws Exception {
        int tipo = conAlfa ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage img = new BufferedImage(w, h, tipo);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(conAlfa ? new Color(255, 0, 0, 128) : Color.RED);
            g.fillRect(0, 0, w, h);
            g.setColor(Color.BLUE);
            g.fillRect(0, 0, w / 2, h / 2);
        } finally {
            g.dispose();
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(img, formato, out);
            return out.toByteArray();
        }
    }

    @Test
    @DisplayName("foto grande se reduce al lado máximo y sale como JPEG progresivo")
    void grandeSeReduceAJpeg() throws Exception {
        byte[] original = imagen("png", 2000, 1000, false);

        OptimizadorImagen.ImagenOptimizada r =
                OptimizadorImagen.optimizar(original, "image/png", ".png");

        assertThat(r.contentType()).isEqualTo("image/jpeg");
        assertThat(r.extension()).isEqualTo(".jpg");
        int[] dims = OptimizadorImagen.dimensiones(r.datos());
        assertThat(dims).containsExactly(1600, 800);
        // Magia JPEG (SOI): el PNG plano de prueba comprime mejor que el
        // JPEG, así que no se afirma tamaño aquí.
        assertThat(r.datos()[0]).isEqualTo((byte) 0xFF);
        assertThat(r.datos()[1]).isEqualTo((byte) 0xD8);
        assertThat(r.datos()[2]).isEqualTo((byte) 0xFF);
    }

    @Test
    @DisplayName("PNG con alfa se compone a JPEG legible sin ampliar")
    void pngConAlfaNoSeAmplia() throws Exception {
        byte[] original = imagen("png", 100, 100, true);

        OptimizadorImagen.ImagenOptimizada r =
                OptimizadorImagen.optimizar(original, "image/png", ".png");

        assertThat(r.contentType()).isEqualTo("image/jpeg");
        assertThat(OptimizadorImagen.dimensiones(r.datos())).containsExactly(100, 100);
    }

    @Test
    @DisplayName("bytes corruptos se devuelven tal cual (fail-open, no 500)")
    void corruptosFailOpen() {
        byte[] basura = { 0x01, 0x02, 0x03, 0x04 };

        OptimizadorImagen.ImagenOptimizada r =
                OptimizadorImagen.optimizar(basura, "image/jpeg", ".jpg");

        assertThat(r.datos()).isSameAs(basura);
        assertThat(r.contentType()).isEqualTo("image/jpeg");
        assertThat(r.extension()).isEqualTo(".jpg");
    }

    @Test
    @DisplayName("lado mayor a 8000 px se rechaza (bomba de descompresión)")
    void dimensionesAbsurdasSeRechazan() throws Exception {
        byte[] enorme = imagen("png", 8001, 10, false);

        assertThatThrownBy(() -> OptimizadorImagen.optimizar(enorme, "image/png", ".png"))
                .isInstanceOf(ValidacionException.class)
                .matches(e -> ((ValidacionException) e).errorCode() == ErrorCode.VALOR_INVALIDO);
    }
}
