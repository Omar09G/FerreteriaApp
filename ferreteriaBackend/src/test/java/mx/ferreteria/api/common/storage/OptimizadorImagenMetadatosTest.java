package mx.ferreteria.api.common.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import mx.ferreteria.api.common.error.ValidacionException;
import mx.ferreteria.api.common.i18n.ErrorCode;

/**
 * Garantía de privacidad: lo que se guarda nunca lleva EXIF/XMP/IPTC
 * (GPS, cámara, autor). Fixtures fabricados a mano con metadatos reales.
 */
class OptimizadorImagenMetadatosTest {

    // ---------- utilidades de construcción ----------

    private static byte[] imagenBase(String formato) throws Exception {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(Color.RED);
            g.fillRect(0, 0, 64, 64);
        } finally {
            g.dispose();
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(img, formato, out);
            return out.toByteArray();
        }
    }

    private static byte[] concat(byte[]... partes) {
        int total = 0;
        for (byte[] p : partes) {
            total += p.length;
        }
        byte[] r = new byte[total];
        int p = 0;
        for (byte[] parte : partes) {
            System.arraycopy(parte, 0, r, p, parte.length);
            p += parte.length;
        }
        return r;
    }

    private static byte[] be16(int v) {
        return new byte[] { (byte) (v >>> 8), (byte) v };
    }

    private static byte[] le16(int v) {
        return new byte[] { (byte) v, (byte) (v >>> 8) };
    }

    private static byte[] le32(int v) {
        return new byte[] { (byte) v, (byte) (v >>> 8), (byte) (v >>> 16), (byte) (v >>> 24) };
    }

    private static byte[] segmentoJpeg(int marcador, byte[] contenido) {
        return concat(new byte[] { (byte) 0xFF, (byte) marcador }, be16(contenido.length + 2), contenido);
    }

    /** APP1 EXIF little-endian con Make + GPS (19°25′N 99°7′W). */
    private static byte[] app1ExifGps() {
        byte[] make = "TESTCAM\0".getBytes(StandardCharsets.US_ASCII); // 8B
        int oMake = 38;
        int oGps = 46;
        int oLat = 100;
        int oLon = 124;
        ByteArrayOutputStream tiff = new ByteArrayOutputStream();
        try {
            tiff.write(new byte[] { 'I', 'I', 0x2A, 0x00 });
            tiff.write(le32(8));
            // IFD0: Make + GPSInfo
            tiff.write(le16(2));
            tiff.write(le16(0x010F));
            tiff.write(le16(2));
            tiff.write(le32(make.length));
            tiff.write(le32(oMake));
            tiff.write(le16(0x8825));
            tiff.write(le16(4));
            tiff.write(le32(1));
            tiff.write(le32(oGps));
            tiff.write(le32(0));
            // IFD GPS: latRef, lat, lonRef, lon
            tiff.write(le16(4));
            tiff.write(le16(0x00));
            tiff.write(le16(2));
            tiff.write(le32(2));
            tiff.write(new byte[] { 'N', 0, 0, 0 });
            tiff.write(le16(0x02));
            tiff.write(le16(5));
            tiff.write(le32(3));
            tiff.write(le32(oLat));
            tiff.write(le16(0x01));
            tiff.write(le16(2));
            tiff.write(le32(2));
            tiff.write(new byte[] { 'W', 0, 0, 0 });
            tiff.write(le16(0x03));
            tiff.write(le16(5));
            tiff.write(le32(3));
            tiff.write(le32(oLon));
            tiff.write(le32(0));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        byte[] head = tiff.toByteArray();
        assertThat(oMake + make.length).isEqualTo(oGps);
        // Racionales: lat 19/1 25/1 0/1, lon 99/1 7/1 0/1
        byte[] lat = concat(le32(19), le32(1), le32(25), le32(1), le32(0), le32(1));
        byte[] lon = concat(le32(99), le32(1), le32(7), le32(1), le32(0), le32(1));
        byte[] tiffBytes = new byte[oLon + lon.length];
        System.arraycopy(head, 0, tiffBytes, 0, head.length);
        System.arraycopy(make, 0, tiffBytes, oMake, make.length);
        System.arraycopy(lat, 0, tiffBytes, oLat, lat.length);
        System.arraycopy(lon, 0, tiffBytes, oLon, lon.length);
        return segmentoJpeg(0xE1, concat("Exif\0\0".getBytes(StandardCharsets.US_ASCII), tiffBytes));
    }

    private static byte[] jpegConMetadatos() throws Exception {
        byte[] base = imagenBase("jpeg");
        byte[] app13 = segmentoJpeg(0xED,
                "Photoshop 3.0\08BIM GPS-DUMMY".getBytes(StandardCharsets.US_ASCII));
        byte[] com = segmentoJpeg(0xFE,
                "comentario secreto gps 19.43,-99.13".getBytes(StandardCharsets.US_ASCII));
        byte[] resto = new byte[base.length - 2];
        System.arraycopy(base, 2, resto, 0, resto.length);
        // SOI + APP1(EXIF/GPS) + APP13 + COM + resto original
        return concat(new byte[] { (byte) 0xFF, (byte) 0xD8 }, app1ExifGps(), app13, com, resto);
    }

    private static byte[] chunkPng(String tipo, byte[] datos) {
        byte[] t = tipo.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(t);
        crc.update(datos);
        long v = crc.getValue();
        return concat(be16((datos.length >>> 16) & 0xFFFF), new byte[] { (byte) (datos.length >>> 8), (byte) datos.length },
                t, datos,
                new byte[] { (byte) (v >>> 24), (byte) (v >>> 16), (byte) (v >>> 8), (byte) v });
    }

    // Nota: be16 de 32 bits se arma en dos partes por simplicidad.
    private static byte[] pngConMetadatos() throws Exception {
        byte[] base = imagenBase("png");
        // IHDR termina en 8 + 25 = 33
        byte[] head = new byte[33];
        System.arraycopy(base, 0, head, 0, head.length);
        byte[] resto = new byte[base.length - head.length];
        System.arraycopy(base, head.length, resto, 0, resto.length);
        byte[] text = chunkPng("tEXt", "Author\0Juan Perez".getBytes(StandardCharsets.US_ASCII));
        byte[] exif = chunkPng("eXIf", new byte[] { 'I', 'I', 0x2A, 0x00, 1, 2, 3, 4, 5, 6 });
        return concat(head, text, exif, resto);
    }

    private static byte[] chunkWebp(String tipo, byte[] datos) {
        byte[] t = tipo.getBytes(StandardCharsets.US_ASCII);
        int n = datos.length;
        byte[] head = concat(t,
                new byte[] { (byte) n, (byte) (n >>> 8), (byte) (n >>> 16), (byte) (n >>> 24) });
        return (n % 2 == 1) ? concat(head, datos, new byte[] { 0 }) : concat(head, datos);
    }

    private static byte[] webpConMetadatos() {
        byte[] vp8l = chunkWebp("VP8L", "DATOSVP8L!".getBytes(StandardCharsets.US_ASCII));
        // 13B (impar): ejercita el pad par entre chunks.
        byte[] exif = chunkWebp("EXIF", "EXIF-DATA-GP!".getBytes(StandardCharsets.US_ASCII));
        byte[] xmp = chunkWebp("XMP ", "XMPPAYLD".getBytes(StandardCharsets.US_ASCII));
        byte[] alph = chunkWebp("ALPH", "ALPH".getBytes(StandardCharsets.US_ASCII));
        byte[] cuerpo = concat("WEBP".getBytes(StandardCharsets.US_ASCII), vp8l, exif, xmp, alph);
        int n = cuerpo.length;
        return concat("RIFF".getBytes(StandardCharsets.US_ASCII),
                new byte[] { (byte) n, (byte) (n >>> 8), (byte) (n >>> 16), (byte) (n >>> 24) },
                cuerpo);
    }

    private static boolean contiene(byte[] datos, String texto) {
        byte[] aguja = texto.getBytes(StandardCharsets.US_ASCII);
        outer: for (int i = 0; i + aguja.length <= datos.length; i++) {
            for (int j = 0; j < aguja.length; j++) {
                if (datos[i + j] != aguja[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    // ---------- tests ----------

    @Test
    @DisplayName("JPEG con EXIF/GPS+IPTC+COM: optimizar lo limpia y decodifica igual")
    void jpegConExif_seLimpiaAlOptimizar() throws Exception {
        byte[] sucio = jpegConMetadatos();
        assertThat(contiene(sucio, "Exif")).isTrue(); // el fixture sí trae metadatos

        OptimizadorImagen.ImagenOptimizada r =
                OptimizadorImagen.optimizar(sucio, "image/jpeg", ".jpg");

        assertThat(contiene(r.datos(), "Exif")).isFalse();
        assertThat(contiene(r.datos(), "TESTCAM")).isFalse();
        assertThat(contiene(r.datos(), "Photoshop")).isFalse();
        assertThat(contiene(r.datos(), "comentario secreto")).isFalse();
        assertThat(r.contentType()).isEqualTo("image/jpeg");
        int[] dims = OptimizadorImagen.dimensiones(r.datos());
        assertThat(dims).containsExactly(64, 64);
    }

    @Test
    @DisplayName("despojarMetadatos deja JPEG válido sin APP1/APP13/COM")
    void jpegStrip_directo() throws Exception {
        byte[] limpio = OptimizadorImagen.despojarMetadatos(jpegConMetadatos());

        assertThat(limpio[0]).isEqualTo((byte) 0xFF);
        assertThat(limpio[1]).isEqualTo((byte) 0xD8);
        assertThat(contiene(limpio, "Exif")).isFalse();
        assertThat(contiene(limpio, "TESTCAM")).isFalse();
        assertThat(contiene(limpio, "Photoshop")).isFalse();
        assertThat(contiene(limpio, "comentario secreto")).isFalse();
        int[] dims = OptimizadorImagen.dimensiones(limpio);
        assertThat(dims).containsExactly(64, 64);
    }

    @Test
    @DisplayName("PNG con tEXt/eXIf: se sueltan y la imagen decodifica")
    void pngStrip_directo() throws Exception {
        byte[] sucio = pngConMetadatos();
        assertThat(contiene(sucio, "Juan Perez")).isTrue();

        byte[] limpio = OptimizadorImagen.despojarMetadatos(sucio);

        assertThat(contiene(limpio, "Juan Perez")).isFalse();
        assertThat(contiene(limpio, "Author")).isFalse();
        assertThat(contiene(limpio, "eXIf")).isFalse();
        int[] dims = OptimizadorImagen.dimensiones(limpio);
        assertThat(dims).containsExactly(64, 64);
    }

    @Test
    @DisplayName("WebP con EXIF/XMP: se sueltan, VP8L intacto y RIFF consistente")
    void webpStrip_directo() {
        byte[] limpio = OptimizadorImagen.despojarMetadatos(webpConMetadatos());

        assertThat(contiene(limpio, "EXIF")).isFalse();
        assertThat(contiene(limpio, "XMPPAYLD")).isFalse();
        assertThat(contiene(limpio, "DATOSVP8L!")).isTrue();
        assertThat(contiene(limpio, "ALPH")).isTrue();
        int tamano = (limpio[4] & 0xFF) | ((limpio[5] & 0xFF) << 8)
                | ((limpio[6] & 0xFF) << 16) | ((limpio[7] & 0xFF) << 24);
        assertThat(tamano).isEqualTo(limpio.length - 8);
    }

    @Test
    @DisplayName("PNG con IDAT corrupto: lo que se guarda sale sin metadatos")
    void pngCorrupto_sinMetadatos() throws Exception {
        byte[] sucio = pngConMetadatos();
        // Corrompe el primer IDAT (los datos, no la estructura)
        int idx = new String(sucio, StandardCharsets.US_ASCII).indexOf("IDAT");
        byte[] roto = sucio.clone();
        roto[idx + 8] ^= 0xFF;

        OptimizadorImagen.ImagenOptimizada r =
                OptimizadorImagen.optimizar(roto, "image/png", ".png");

        // Vale ruta fail-open (sanitizado) o éxito (re-codificado):
        // en ambas, sin texto sensible.
        assertThat(contiene(r.datos(), "Juan Perez")).isFalse();
        assertThat(r.datos().length).isGreaterThan(0);
    }

    @Test
    @DisplayName("basura o contenedor truncado se rechaza con 400")
    void basura_rechazada() {
        assertThatThrownBy(() -> OptimizadorImagen.despojarMetadatos(new byte[] { 1, 2, 3 }))
                .isInstanceOf(ValidacionException.class)
                .matches(e -> ((ValidacionException) e).errorCode() == ErrorCode.VALOR_INVALIDO);
        assertThatThrownBy(() -> OptimizadorImagen.despojarMetadatos(new byte[] {
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1, 0x00, 0x05,
                'x', 0, 0, 0, 0, 0 }))
                .isInstanceOf(ValidacionException.class)
                .matches(e -> ((ValidacionException) e).errorCode() == ErrorCode.VALOR_INVALIDO);
    }
}
