package mx.ferreteria.api.common.storage;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import mx.ferreteria.api.common.error.ValidacionException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.core.sync.RequestBody;

/**
 * {@link FotoStoragePort} sobre Floci (S3 local) con AWS SDK v2. Activo solo
 * cuando {@code app.storage.proveedor=floci}. El flujo es el mismo que el de
 * MinIO: valida, optimiza ({@link OptimizadorImagen}), renombra a UUID y
 * devuelve la URL pública path-style ({@code /bucket/objeto}).
 * <p>
 * Diferencias contra el SDK de MinIO (detalles internos, mismo contrato):
 * path-style forzado (el SDK defaultea a virtual-hosted, que no resuelve en
 * local), región obligatoria para SigV4 y {@code headBucket} por excepción en
 * vez de {@code bucketExists} booleano. El bucket se crea (público, solo
 * lectura) de forma perezosa en la primera subida.
 */
@Service
@ConditionalOnProperty(name = "app.storage.proveedor", havingValue = "floci")
public class FlociFotoStorageService implements FotoStoragePort {

    private final FlociStorageProperties props;

    /** Ambiente (APP_AMBIENTE): dev permite HTTP, prod exige HTTPS en la URL pública. */
    @Value("${app.ambiente:dev}")
    private String ambiente;

    private volatile S3Client cliente;

    @Autowired
    public FlociFotoStorageService(FlociStorageProperties props) {
        this(props, null);
    }

    /** Constructor para tests: permite inyectar un S3Client mockeado. */
    FlociFotoStorageService(FlociStorageProperties props, S3Client cliente) {
        this.props = props;
        this.cliente = cliente;
    }

    @PostConstruct
    void validarAmbiente() {
        Fotos.validarAmbiente(ambiente, props.publicUrl());
    }

    @Override
    public String subir(String contentType, String nombreOriginal, InputStream datos, long tamano) {
        String extension = Fotos.extensionPara(contentType, nombreOriginal);
        long maxBytes = props.maxMb() * 1024L * 1024L;
        if (tamano > maxBytes) {
            throw new ValidacionException(ErrorCode.ARCHIVO_MUY_GRANDE, props.maxMb());
        }
        byte[] bytes;
        try {
            bytes = datos.readAllBytes();
        } catch (IOException e) {
            throw new ValidacionException(ErrorCode.SERVICIO_NO_DISPONIBLE);
        }
        OptimizadorImagen.ImagenOptimizada optimizada =
                OptimizadorImagen.optimizar(bytes, contentType, extension);
        String objeto = UUID.randomUUID() + optimizada.extension();
        try {
            S3Client s3 = cliente();
            asegurarBucket(s3);
            s3.putObject(PutObjectRequest.builder()
                    .bucket(props.bucket())
                    .key(objeto)
                    .contentType(optimizada.contentType())
                    .build(),
                    RequestBody.fromBytes(optimizada.datos()));
        } catch (ValidacionException e) {
            throw e;
        } catch (Exception e) {
            throw new ValidacionException(ErrorCode.SERVICIO_NO_DISPONIBLE);
        }
        return basePublica() + "/" + props.bucket() + "/" + objeto;
    }

    private void asegurarBucket(S3Client s3) {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(props.bucket()).build());
        } catch (NoSuchBucketException e) {
            s3.createBucket(CreateBucketRequest.builder().bucket(props.bucket()).build());
            s3.putBucketPolicy(PutBucketPolicyRequest.builder()
                    .bucket(props.bucket())
                    .policy(Fotos.politicaLecturaPublica(props.bucket()))
                    .build());
        }
    }

    private S3Client cliente() {
        S3Client actual = cliente;
        if (actual == null) {
            synchronized (this) {
                actual = cliente;
                if (actual == null) {
                    actual = S3Client.builder()
                            .endpointOverride(URI.create(props.endpoint()))
                            .region(Region.of(props.region()))
                            .credentialsProvider(StaticCredentialsProvider.create(
                                    AwsBasicCredentials.create(props.accessKey(), props.secretKey())))
                            // Floci local no resuelve virtual-hosted (bucket.host);
                            // path-style: http://host:4566/bucket/key.
                            .forcePathStyle(true)
                            .build();
                    cliente = actual;
                }
            }
        }
        return actual;
    }

    private String basePublica() {
        String base = props.publicUrl();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}
