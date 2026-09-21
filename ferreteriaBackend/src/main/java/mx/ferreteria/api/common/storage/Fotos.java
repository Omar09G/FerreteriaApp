package mx.ferreteria.api.common.storage;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

import mx.ferreteria.api.common.error.ValidacionException;
import mx.ferreteria.api.common.i18n.ErrorCode;

/**
 * Lógica compartida de subida de fotos, agnóstica al proveedor S3
 * (MinIO / Floci). Los {@code *FotoStorageService} la reutilizan para que el
 * contrato visible (tipos aceptados, validación de ambiente, política de
 * lectura pública) sea idéntico sin importar el backend.
 */
final class Fotos {

    /** MIME permitidos y su extensión canónica. */
    private static final Map<String, String> MIME_A_EXTENSION = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp");

    private static final Set<String> EXTENSIONES = Set.of(".jpg", ".jpeg", ".png", ".webp");

    private static final String POLITICA_LECTURA_PUBLICA = """
            {"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"AWS":["*"]},\
            "Action":["s3:GetObject"],"Resource":["arn:aws:s3:::%s/*"]}]}""";

    private Fotos() {
    }

    /** Resuelve la extensión canónica desde el MIME o el nombre original. */
    static String extensionPara(String contentType, String nombreOriginal) {
        String ct = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (MIME_A_EXTENSION.containsKey(ct)) {
            return MIME_A_EXTENSION.get(ct);
        }
        String ext = "";
        if (nombreOriginal != null) {
            int punto = nombreOriginal.lastIndexOf('.');
            if (punto >= 0) {
                ext = nombreOriginal.substring(punto).toLowerCase(Locale.ROOT);
            }
        }
        if (EXTENSIONES.contains(ext)) {
            return ".jpeg".equals(ext) ? ".jpg" : ext;
        }
        throw new ValidacionException(ErrorCode.ARCHIVO_TIPO_NO_PERMITIDO,
                contentType == null ? "desconocido" : contentType);
    }

    /**
     * Fail-fast al arrancar: en prod la URL pública de fotos debe ser HTTPS
     * (si no, los browsers bloquean las imágenes por contenido mixto).
     */
    static void validarAmbiente(String ambiente, String publicUrl) {
        String amb = ambiente == null ? "" : ambiente.trim().toLowerCase();
        if (!amb.equals("dev") && !amb.equals("prod")) {
            throw new IllegalStateException(
                    "APP_AMBIENTE debe ser dev o prod, valor actual: " + ambiente);
        }
        if (amb.equals("prod") && publicUrl != null
                && publicUrl.trim().toLowerCase().startsWith("http://")) {
            throw new IllegalStateException(
                    "En ambiente prod la URL pública de fotos debe ser HTTPS");
        }
    }

    /** Política de bucket: lectura pública de objetos (lenguaje AWS estándar). */
    static String politicaLecturaPublica(String bucket) {
        return String.format(POLITICA_LECTURA_PUBLICA, bucket);
    }
}
