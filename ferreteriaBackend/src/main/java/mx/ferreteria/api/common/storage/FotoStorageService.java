package mx.ferreteria.api.common.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.SetBucketPolicyArgs;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.common.error.ValidacionException;
import mx.ferreteria.api.common.i18n.ErrorCode;

/**
 * Subida de fotos de entidades a MinIO. El archivo se renombra a
 * {@code UUID + extensión original} para evitar colisiones y sanear nombres
 * provistos por el cliente. El bucket se crea (público, solo lectura) de forma
 * perezosa en la primera subida para no acoplar el arranque al storage.
 * <p>
 * Antes de guardar, la imagen se normaliza con {@link OptimizadorImagen}
 * (resize + JPEG progresivo) para lectura y carga rápida en la UI.
 */
@Service
@RequiredArgsConstructor
public class FotoStorageService {

    /** MIME permitidos y su extensión canónica. */
    private static final Map<String, String> MIME_A_EXTENSION = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp");

    private static final Set<String> EXTENSIONES = Set.of(".jpg", ".jpeg", ".png", ".webp");

    private static final String POLITICA_LECTURA_PUBLICA = """
            {"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"AWS":["*"]},\
            "Action":["s3:GetObject"],"Resource":["arn:aws:s3:::%s/*"]}]}""";

    private final MinioProperties props;

    /** Ambiente (APP_AMBIENTE): dev permite HTTP, prod exige HTTPS en la URL pública. */
    @Value("${app.ambiente:dev}")
    private String ambiente;

    private volatile MinioClient cliente;

    /**
     * Fail-fast al arrancar: en prod la URL pública de fotos debe ser HTTPS
     * (si no, los browsers bloquean las imágenes por contenido mixto).
     */
    @PostConstruct
    void validarAmbiente() {
        String amb = ambiente == null ? "" : ambiente.trim().toLowerCase();
        if (!amb.equals("dev") && !amb.equals("prod")) {
            throw new IllegalStateException(
                    "APP_AMBIENTE debe ser dev o prod, valor actual: " + ambiente);
        }
        if (amb.equals("prod") && props.publicUrl() != null
                && props.publicUrl().trim().toLowerCase().startsWith("http://")) {
            throw new IllegalStateException(
                    "En ambiente prod MINIO_PUBLIC_URL debe ser HTTPS");
        }
    }

    /**
     * Sube la imagen y devuelve la URL pública a guardar en foto_url/imagen_url.
     * La imagen se optimiza (JPEG progresivo ≤1600 px) antes de guardarse.
     */
    public String subir(String contentType, String nombreOriginal, InputStream datos, long tamano) {
        String extension = extensionPara(contentType, nombreOriginal);
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
            MinioClient minio = cliente();
            asegurarBucket(minio);
            minio.putObject(PutObjectArgs.builder()
                    .bucket(props.bucket())
                    .object(objeto)
                    .stream(new ByteArrayInputStream(optimizada.datos()),
                            optimizada.datos().length, -1)
                    .contentType(optimizada.contentType())
                    .build());
        } catch (ValidacionException e) {
            throw e;
        } catch (Exception e) {
            throw new ValidacionException(ErrorCode.SERVICIO_NO_DISPONIBLE);
        }
        return basePublica() + "/" + props.bucket() + "/" + objeto;
    }

    private String extensionPara(String contentType, String nombreOriginal) {
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

    private void asegurarBucket(MinioClient minio) throws Exception {
        if (!minio.bucketExists(BucketExistsArgs.builder().bucket(props.bucket()).build())) {
            minio.makeBucket(MakeBucketArgs.builder().bucket(props.bucket()).build());
            minio.setBucketPolicy(SetBucketPolicyArgs.builder()
                    .bucket(props.bucket())
                    .config(String.format(POLITICA_LECTURA_PUBLICA, props.bucket()))
                    .build());
        }
    }

    private MinioClient cliente() {
        MinioClient actual = cliente;
        if (actual == null) {
            synchronized (this) {
                actual = cliente;
                if (actual == null) {
                    actual = MinioClient.builder()
                            .endpoint(props.endpoint())
                            .credentials(props.accessKey(), props.secretKey())
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
