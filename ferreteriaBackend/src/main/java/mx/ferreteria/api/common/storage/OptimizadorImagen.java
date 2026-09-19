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
 * bytes, devuelve el original tal cual (fail-open: se guarda sin optimizar
 * en vez de 500); dimensiones absurdas (&gt; 8000 px por lado, posible bomba
 * de descompresión) se rechazan con 400.
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
            return new ImagenOptimizada(original, contentType, extension);
        }
        if (imagen == null) {
            return new ImagenOptimizada(original, contentType, extension);
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
            return new ImagenOptimizada(original, contentType, extension);
        }
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
