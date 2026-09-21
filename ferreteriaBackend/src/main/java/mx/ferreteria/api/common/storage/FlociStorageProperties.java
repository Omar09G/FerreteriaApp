package mx.ferreteria.api.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración del almacenamiento S3-compatible vía Floci (emulador AWS
 * local) para fotos de entidades. {@code endpoint} lo usa el backend en red
 * interna ({@code floci:4566} en compose); {@code publicUrl} es la base de la
 * URL que se guarda en foto_url/imagen_url y que resuelve el browser.
 * <p>
 * Floci acepta cualquier credencial no vacía; la región la exige el firmante
 * SigV4 del SDK (cualquiera vale en local).
 */
@ConfigurationProperties(prefix = "app.storage.floci")
public record FlociStorageProperties(
        @DefaultValue("http://localhost:4566") String endpoint,
        @DefaultValue("http://localhost:4566") String publicUrl,
        @DefaultValue("us-east-1") String region,
        @DefaultValue("test") String accessKey,
        @DefaultValue("test") String secretKey,
        @DefaultValue("ferreteria-fotos") String bucket,
        @DefaultValue("5") long maxMb) {
}
