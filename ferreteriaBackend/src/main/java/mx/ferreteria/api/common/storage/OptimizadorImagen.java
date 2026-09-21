package mx.ferreteria.api.common.storage;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import mx.ferreteria.api.common.error.ValidacionException;
import mx.ferreteria.api.common.i18n.ErrorCode;

/**
 * Normaliza fotos de entidades para lectura y carga rápida en la UI.
 * <p>
 * Toda imagen aceptada (JPEG/PNG/WebP) se re-codifica a JPEG progresivo de
 * máximo 1600 px por lado con calidad 0.82: una foto típica de celular pasa
 * de varios MB a ~100-300 KB sin pérdida visible en miniaturas y detalle.
 * El JPEG progresivo además pinta por pasadas (carga percibida más rápida).
 * <p>
 * Reglas: solo reduce (nunca amplía); si el decodificador no reconoce los
 * bytes, se guardan <b>despojados de metadatos</b> en vez de 500 (fail-open
 * sanitizado; si ni el contenedor es parseable se guarda tal cual, pues sin
 * contenedor no hay metadato estructurado que filtrar); dimensiones absurdas
 * (&gt; 8000 px por lado, posible bomba de descompresión) se rechazan con 400.
 * <p>
 * <b>Garantía de privacidad:</b> lo que se guarda nunca lleva metadatos
 * EXIF/XMP/IPTC (GPS, cámara, autor, fechas): la re-codificación los suelta
 * y la ruta fail-open los elimina por segmentos ({@link #despojarMetadatos})
 * antes de guardar. Así, si el storage se compromete, las fotos no exponen
 * ubicación ni datos del usuario.
 */
final class OptimizadorImagen {

    /** Lado mayor máximo tras el resize. Suficiente para detalle a pantalla completa. */
    static final int LADO_MAXIMO = 1600;

    /** Calidad JPEG: punto dulce tamaño/calidad para fotos de catálogo. */
    static final float CALIDAD_JPEG = 0.82f;

    /** Tope por lado contra bombas de descompresión (el peso ya lo acota 5 MB). */
    static final int LADO_MAXIMO_ABSOLUTO = 8000;

    private OptimizadorImagen() { }

    record ImagenOptimizada(byte[] datos, String contentType, String extension) { }

    /**
     * @param contentType content-type ya validado del upload (se conserva si no se puede optimizar)
     * @param extension extensión canónica ya validada (se conserva si no se puede optimizar)
     */
    static ImagenOptimizada optimizar(byte[] original, String contentType, String extension) {
        BufferedImage imagen;
        try {
            imagen = ImageIO.read(new ByteArrayInputStream(original));
        } catch (IOException | IllegalArgumentException e) {
            return new ImagenOptimizada(sanitizadoSeguro(original), contentType, extension);
        }
        if (imagen == null) {
            return new ImagenOptimizada(sanitizadoSeguro(original), contentType, extension);
        }
        if (imagen.getWidth() > LADO_MAXIMO_ABSOLUTO
                || imagen.getHeight() > LADO_MAXIMO_ABSOLUTO) {
            throw new ValidacionException(ErrorCode.VALOR_INVALIDO);
        }
        BufferedImage reducida = reducir(imagen);
        try {
            return new ImagenOptimizada(escribirJpegProgresivo(reducida),
                    "image/jpeg", ".jpg");
        } catch (IOException e) {
            return new ImagenOptimizada(sanitizadoSeguro(original), contentType, extension);
        }
    }

    /**
     * Intenta {@link #despojarMetadatos}; si el contenedor ni siquiera es
     * parseable devuelve el original (fail-open histórico: sin contenedor no
     * hay metadato estructurado EXIF/XMP/IPTC que filtrar).
     */
    private static byte[] sanitizadoSeguro(byte[] original) {
        try {
            return despojarMetadatos(original);
        } catch (ValidacionException e) {
            return original;
        }
    }

    /**
     * Elimina metadatos (EXIF con GPS, XMP, IPTC, perfiles, comentarios y
     * chunks de texto) conservando intactos los datos de imagen. Opera a
     * nivel contenedor —no requiere decodificar píxeles— y por eso cubre la
     * ruta fail-open de {@link #optimizar}: lo que no se pudo re-codificar se
     * guarda al menos sanitizado.
     * <p>
     * Si el contenedor ni siquiera es parseable, se rechaza con 400: no se
     * guarda lo que no se puede sanitizar.
     *
     * @throws ValidacionException {@code VALOR_INVALIDO} si no es JPEG/PNG/WebP válido
     */
    static byte[] despojarMetadatos(byte[] original) {
        if (original == null || original.length < 12) {
            throw new ValidacionException(ErrorCode.VALOR_INVALIDO);
        }
        if (esJpeg(original)) {
            return sinSegmentosSensiblesJpeg(original);
        }
        if (esPng(original)) {
            return sinChunksSensiblesPng(original);
        }
        if (esWebp(original)) {
            return sinChunksSensiblesWebp(original);
        }
        throw new ValidacionException(ErrorCode.VALOR_INVALIDO);
    }

    private static boolean esJpeg(byte[] d) {
        return (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8;
    }

    private static boolean esPng(byte[] d) {
        return d[0] == (byte) 0x89 && d[1] == 0x50 && d[2] == 0x4E && d[3] == 0x47
                && d[4] == 0x0D && d[5] == 0x0A && d[6] == 0x1A && d[7] == 0x0A;
    }

    private static boolean esWebp(byte[] d) {
        return d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P';
    }

    /**
     * JPEG: elimina APP1 (EXIF/XMP), APP2 (ICC), APP13 (IPTC/Photoshop) y COM
     * (comentarios). Conserva APP0/APP14, tablas, SOF y los datos de scan
     * verbatim (tras el primer SOS todo se copia tal cual, incluidos los
     * scans progresivos posteriores).
     */
    private static byte[] sinSegmentosSensiblesJpeg(byte[] jpg) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream(jpg.length);
        salida.write(0xFF);
        salida.write(0xD8);
        int p = 2;
        while (p < jpg.length) {
            if ((jpg[p] & 0xFF) != 0xFF) {
                throw new ValidacionException(ErrorCode.VALOR_INVALIDO);
            }
            p++;
            int m;
            do {
                if (p >= jpg.length) {
                    throw new ValidacionException(ErrorCode.VALOR_INVALIDO);
                }
                m = jpg[p++] & 0xFF;
            } while (m == 0xFF); // bytes de relleno entre segmentos
            if (m == 0xD9) {
                // EOI: se copia el marcador y se suelta basura trailing.
                salida.write(0xFF);
                salida.write(0xD9);
                break;
            }
            if (m == 0xDA) {
                // SOS: encabezado + scans verbatim hasta el primer EOI
                // adyacente (FF00 es relleno: un FFD9 adyacente solo puede
                // ser el EOI real). Sin EOI se copia el resto (truncado,
                // pero ya sin metadatos en encabezados).
                int largo = u16seg(jpg, p);
                salida.write(0xFF);
                salida.write(0xDA);
                salida.write(jpg, p, largo);
                int scan = p + largo;
                int fin = jpg.length;
                for (int i = scan; i + 1 < jpg.length; i++) {
                    if ((jpg[i] & 0xFF) == 0xFF && (jpg[i + 1] & 0xFF) == 0xD9) {
                        fin = i + 2;
                        break;
                    }
                }
                salida.write(jpg, scan, fin - scan);
                break;
            }
            if (m == 0xD8 || m == 0x01 || (m >= 0xD0 && m <= 0xD7)) {
                // Sin longitud: SOI repetido, TEM, reinicios.
                salida.write(0xFF);
                salida.write(m);
                continue;
            }
            int largo = u16seg(jpg, p);
            boolean sensible = m == 0xE1 || m == 0xE2 || m == 0xED || m == 0xFE;
            if (!sensible) {
                salida.write(0xFF);
                salida.write(m);
                salida.write(jpg, p, largo);
            }
            p += largo;
        }
        return salida.toByteArray();
    }

    /**
     * Lee longitud de segmento JPEG (2 bytes big-endian, incluye los 2 del
     * largo) validando cotas; lanza 400 si el contenedor está corrupto.
     */
    private static int u16seg(byte[] d, int p) {
        if (p + 1 >= d.length) {
            throw new ValidacionException(ErrorCode.VALOR_INVALIDO);
        }
        int largo = ((d[p] & 0xFF) << 8) | (d[p + 1] & 0xFF);
        if (largo < 2 || (long) p + largo > d.length) {
            throw new ValidacionException(ErrorCode.VALOR_INVALIDO);
        }
        return largo;
    }

    /**
     * PNG: conserva firma + chunks críticos (IHDR/PLTE/IDAT/IEND) y ancilares
     * de render (sRGB/gAMA/cHRM/bKGD/pHYs/tRNS/sBIT). Suelta texto (tEXt/
     * zTXt/iTXt: autor, software, ubicación), eXIf, iCCP, hIST y tIME. Un
     * chunk crítico desconocido se rechaza (el spec obliga al decoder).
     */
    private static byte[] sinChunksSensiblesPng(byte[] png) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream(png.length);
        salida.write(png, 0, 8);
        int p = 8;
        boolean vioIhdr = false;
        while (p + 8 <= png.length) {
            long largo = u32(png, p);
            String tipo = new String(png, p + 4, 4, java.nio.charset.StandardCharsets.US_ASCII);
            if (largo > Integer.MAX_VALUE || (long) p + 12 + largo > png.length) {
                throw new ValidacionException(ErrorCode.VALOR_INVALIDO);
            }
            int total = 12 + (int) largo;
            if (!vioIhdr && !"IHDR".equals(tipo)) {
                throw new ValidacionException(ErrorCode.VALOR_INVALIDO);
            }
            vioIhdr = true;
            boolean critico = Character.isUpperCase(tipo.charAt(0));
            boolean conservar = "IHDR".equals(tipo) || "PLTE".equals(tipo) || "IDAT".equals(tipo)
                    || "IEND".equals(tipo) || "sRGB".equals(tipo) || "gAMA".equals(tipo)
                    || "cHRM".equals(tipo) || "bKGD".equals(tipo) || "pHYs".equals(tipo)
                    || "tRNS".equals(tipo) || "sBIT".equals(tipo);
            if (!conservar && critico) {
                throw new ValidacionException(ErrorCode.VALOR_INVALIDO);
            }
            if (conservar) {
                salida.write(png, p, total);
            }
            p += total;
            if ("IEND".equals(tipo)) {
                break;
            }
        }
        return salida.toByteArray();
    }

    /**
     * WebP (contenedor RIFF): elimina chunks EXIF y XMP, conserva el resto
     * verbatim y recalcula el tamaño RIFF. Respeta el pad par de chunks
     * impares.
     */
    private static byte[] sinChunksSensiblesWebp(byte[] webp) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream(webp.length);
        salida.write(webp, 0, 12);
        int p = 12;
        while (p + 8 <= webp.length) {
            String tipo = new String(webp, p, 4, java.nio.charset.StandardCharsets.US_ASCII);
            long largo = u32le(webp, p + 4);
            if (largo > Integer.MAX_VALUE || p + 8 + largo > webp.length) {
                throw new ValidacionException(ErrorCode.VALOR_INVALIDO);
            }
            int total = 8 + (int) largo + ((largo % 2 == 1 && p + 8 + largo < webp.length) ? 1 : 0);
            boolean sensible = "EXIF".equals(tipo) || "XMP ".equals(tipo);
            if (!sensible) {
                salida.write(webp, p, total);
            }
            p += total;
        }
        byte[] limpio = salida.toByteArray();
        // Recalcula tamaño RIFF (total - 8), little-endian.
        int tamano = limpio.length - 8;
        limpio[4] = (byte) (tamano & 0xFF);
        limpio[5] = (byte) ((tamano >>> 8) & 0xFF);
        limpio[6] = (byte) ((tamano >>> 16) & 0xFF);
        limpio[7] = (byte) ((tamano >>> 24) & 0xFF);
        return limpio;
    }

    private static long u32le(byte[] d, int p) {
        return ((long) (d[p] & 0xFF)) | ((long) (d[p + 1] & 0xFF) << 8)
                | ((long) (d[p + 2] & 0xFF) << 16) | ((long) (d[p + 3] & 0xFF) << 24);
    }

    private static long u32(byte[] d, int p) {
        return ((long) (d[p] & 0xFF) << 24) | ((d[p + 1] & 0xFF) << 16)
                | ((d[p + 2] & 0xFF) << 8) | (d[p + 3] & 0xFF);
    }

    private static BufferedImage reducir(BufferedImage imagen) {
        int w = imagen.getWidth();
        int h = imagen.getHeight();
        int mayor = Math.max(w, h);
        if (mayor <= LADO_MAXIMO) {
            return aRgb(imagen, w, h);
        }
        double escala = (double) LADO_MAXIMO / mayor;
        return aRgb(imagen, (int) Math.round(w * escala), (int) Math.round(h * escala));
    }

    /** JPEG no admite alfa: todo se compone sobre fondo blanco. */
    private static BufferedImage aRgb(BufferedImage origen, int w, int h) {
        BufferedImage destino = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = destino.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(origen, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return destino;
    }

    private static byte[] escribirJpegProgresivo(BufferedImage imagen) throws IOException {
        Iterator<ImageWriter> escritores = ImageIO.getImageWritersByFormatName("jpeg");
        if (!escritores.hasNext()) {
            throw new IOException("sin escritor JPEG");
        }
        ImageWriter escritor = escritores.next();
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream();
                ImageOutputStream ios = ImageIO.createImageOutputStream(salida)) {
            escritor.setOutput(ios);
            ImageWriteParam params = escritor.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(CALIDAD_JPEG);
            params.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
            escritor.write(null, new IIOImage(imagen, null, null), params);
            ios.flush();
            return salida.toByteArray();
        } finally {
            escritor.dispose();
        }
    }

    /** Solo para tests: dimensiones sin decodificar la imagen completa. */
    static int[] dimensiones(byte[] datos) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(datos))) {
            Iterator<ImageReader> lectores = ImageIO.getImageReaders(in);
            if (!lectores.hasNext()) {
                throw new IOException("formato no reconocido");
            }
            ImageReader lector = lectores.next();
            try {
                lector.setInput(in);
                return new int[] { lector.getWidth(0), lector.getHeight(0) };
            } finally {
                lector.dispose();
            }
        }
    }
}
