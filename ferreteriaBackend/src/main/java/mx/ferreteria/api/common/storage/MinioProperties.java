package mx.ferreteria.api.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración del almacenamiento S3-compatible (MinIO autohospedado) para
 * fotos de entidades. {@code endpoint} lo usa el backend en red interna;
 * {@code publicUrl} es la base de la URL que se guarda en foto_url/imagen_url
 * y que resuelve el browser (ver compose: minio:9000 vs localhost:9000).
 */
@ConfigurationProperties(prefix = "app.minio")
public record MinioProperties(
        @DefaultValue("http://localhost:9000") String endpoint,
        @DefaultValue("http://localhost:9000") String publicUrl,
        @DefaultValue("minioadmin") String accessKey,
        @DefaultValue("minioadmin") String secretKey,
        @DefaultValue("ferreteria-fotos") String bucket,
        @DefaultValue("5") long maxMb) {
}
