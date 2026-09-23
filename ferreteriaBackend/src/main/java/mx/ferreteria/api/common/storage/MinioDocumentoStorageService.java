package mx.ferreteria.api.common.storage;

import java.net.URI;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import mx.ferreteria.api.common.error.ValidacionException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * {@link DocumentoStoragePort} sobre MinIO con AWS SDK v2 (S3-compatible).
 * Activo cuando {@code app.storage.proveedor=minio} (default). Usa el bucket
 * dedicado {@code bucketDocumentos} (default ferreteria-tickets), SEPARADO
 * del bucket público de fotos; los PDFs permanecen privados (sin política
 * pública) y el backend los re-sirve o adjunta con credencial.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.storage.proveedor", havingValue = "minio", matchIfMissing = true)
public class MinioDocumentoStorageService implements DocumentoStoragePort {

    private final MinioProperties props;

    private volatile S3Client cliente;

    @Override
    public String subirPdf(String clave, byte[] datos) {
        try {
            S3Client s3 = cliente();
            asegurarBucket(s3);
            s3.putObject(PutObjectRequest.builder()
                    .bucket(props.bucketDocumentos())
                    .key(clave)
                    .contentType("application/pdf")
                    .build(),
                    RequestBody.fromBytes(datos));
        } catch (Exception e) {
            throw new ValidacionException(ErrorCode.SERVICIO_NO_DISPONIBLE);
        }
        return clave;
    }

    @Override
    public byte[] descargarPdf(String clave) {
        try (ResponseInputStream<?> in = cliente().getObject(GetObjectRequest.builder()
                .bucket(props.bucketDocumentos())
                .key(clave)
                .build())) {
            return in.readAllBytes();
        } catch (NoSuchKeyException e) {
            throw new ValidacionException(ErrorCode.RECURSO_NO_ENCONTRADO);
        } catch (Exception e) {
            throw new ValidacionException(ErrorCode.SERVICIO_NO_DISPONIBLE);
        }
    }

    private void asegurarBucket(S3Client s3) {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(props.bucketDocumentos()).build());
        } catch (NoSuchBucketException e) {
            s3.createBucket(CreateBucketRequest.builder().bucket(props.bucketDocumentos()).build());
            // Sin putBucketPolicy: PDFs permanecen privados.
        }
    }

    private S3Client cliente() {
        S3Client actual = cliente;
        if (actual == null) {
            synchronized (this) {
                actual = cliente;
                if (actual == null) {
                    // MinIO acepta cualquier región con path style.
                    actual = S3Client.builder()
                            .endpointOverride(URI.create(props.endpoint()))
                            .region(Region.US_EAST_1)
                            .credentialsProvider(StaticCredentialsProvider.create(
                                    AwsBasicCredentials.create(props.accessKey(), props.secretKey())))
                            .forcePathStyle(true)
                            .build();
                    cliente = actual;
                }
            }
        }
        return actual;
    }
}
