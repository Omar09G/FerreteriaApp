package mx.ferreteria.api.common.storage;

import java.io.InputStream;

/**
 * Puerto de almacenamiento de fotos de entidades (clientes, proveedores,
 * empleados, productos). El proveedor concreto se elige con
 * {@code app.storage.proveedor}: {@code minio} (MinIO autohospedado,
 * default) o {@code floci} (emulador AWS S3 local).
 * <p>
 * El contrato es agnóstico al SDK: recibe el archivo, lo normaliza y devuelve
 * la URL pública a guardar en {@code foto_url}/{@code imagen_url}.
 */
public interface FotoStoragePort {

    /**
     * Sube la imagen renombrada a {@code UUID + extensión} y devuelve su URL
     * pública.
     *
     * @param contentType    MIME declarado por el cliente (puede ser null)
     * @param nombreOriginal nombre original (para inferir extensión si el MIME
     *                       no alcanza)
     * @param datos          contenido del archivo
     * @param tamano         tamaño en bytes (se valida contra el máximo)
     * @return URL pública del objeto subido
     */
    String subir(String contentType, String nombreOriginal, InputStream datos, long tamano);
}
