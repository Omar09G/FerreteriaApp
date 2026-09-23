package mx.ferreteria.api.common.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import mx.ferreteria.api.common.error.ValidacionException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MinioDocumentoStorageServiceTest {

    @Mock
    S3Client s3;

    MinioDocumentoStorageService service;

    @BeforeEach
    void setUp() {
        service = new MinioDocumentoStorageService(
                new MinioProperties("http://localhost:9000", "http://localhost:9000",
                        "minioadmin", "minioadmin", "ferreteria-fotos", "ferreteria-tickets", 5),
                s3);
    }

    @Test
    @DisplayName("subirPdf: put con content-type PDF y devuelve la clave")
    void subirPdf_ok() {
        when(s3.putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        String clave = service.subirPdf("tickets/1.pdf", new byte[] { 1, 2, 3 });

        assertThat(clave).isEqualTo("tickets/1.pdf");
        verify(s3).putObject(
                org.mockito.ArgumentMatchers.argThat((PutObjectRequest r) -> r.bucket().equals("ferreteria-tickets")
                        && r.key().equals("tickets/1.pdf")
                        && r.contentType().equals("application/pdf")),
                any(software.amazon.awssdk.core.sync.RequestBody.class));
    }

    @Test
    @DisplayName("subirPdf con bucket inexistente: lo crea sin política pública y reintenta")
    void subirPdf_creaBucket() {
        when(s3.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(NoSuchBucketException.builder().build());
        when(s3.putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        service.subirPdf("tickets/1.pdf", new byte[] { 1 });

        verify(s3).createBucket(any(software.amazon.awssdk.services.s3.model.CreateBucketRequest.class));
        verify(s3).putObject(any(PutObjectRequest.class),
                any(software.amazon.awssdk.core.sync.RequestBody.class));
    }

    @Test
    @DisplayName("descargarPdf: devuelve los bytes del objeto")
    void descargarPdf_ok() throws Exception {
        byte[] pdf = "%PDF".getBytes(StandardCharsets.UTF_8);
        @SuppressWarnings("unchecked")
        ResponseInputStream<GetObjectResponse> in = mock(ResponseInputStream.class);
        when(in.readAllBytes()).thenReturn(pdf);
        when(s3.getObject(any(GetObjectRequest.class))).thenReturn(in);

        assertThat(service.descargarPdf("tickets/1.pdf")).isEqualTo(pdf);
    }

    @Test
    @DisplayName("descargarPdf inexistente: RECURSO_NO_ENCONTRADO")
    void descargarPdf_noExiste() {
        when(s3.getObject(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().build());

        assertThatThrownBy(() -> service.descargarPdf("tickets/9.pdf"))
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO));
    }

    @Test
    @DisplayName("fallo de red: SERVICIO_NO_DISPONIBLE")
    void subirPdf_falloRed() {
        when(s3.putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class)))
                .thenThrow(new RuntimeException("red caída"));

        assertThatThrownBy(() -> service.subirPdf("tickets/1.pdf", new byte[] { 1 }))
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.SERVICIO_NO_DISPONIBLE));
    }

}
