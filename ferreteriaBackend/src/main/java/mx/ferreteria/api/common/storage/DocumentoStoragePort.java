package mx.ferreteria.api.common.storage;

/**
 * Puerto de almacenamiento de documentos (PDF de ticket/nómina).
 * A diferencia de {@link FotoStoragePort}, no normaliza imagen: sube bytes
 * PDF crudos bajo una clave determinista ({@code prefijo/archivo.pdf}).
 * El proveedor se elige con {@code app.storage.proveedor} (minio | floci).
 */
public interface DocumentoStoragePort {

    /**
     * Sube un PDF y devuelve la clave/URL a persistir en pdf_url.
     *
     * @param clave  clave del objeto, p.ej. {@code tickets/123.pdf}
     * @param datos  contenido PDF
     * @return clave/URL pública o interna del objeto subido
     */
    String subirPdf(String clave, byte[] datos);

    /**
     * Descarga el PDF por clave (para adjuntar en email/Telegram).
     *
     * @param clave clave devuelta por {@link #subirPdf}
     * @return bytes del PDF
     */
    byte[] descargarPdf(String clave);
}
