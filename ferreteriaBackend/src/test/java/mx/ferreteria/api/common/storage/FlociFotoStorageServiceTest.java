package mx.ferreteria.api.common.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import mx.ferreteria.api.common.error.ValidacionException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.core.sync.RequestBody;

class FlociFotoStorageServiceTest {

    private static FlociStorageProperties props() {
        return new FlociStorageProperties(
                "http://localhost:4566", "http://localhost:4566", "us-east-1",
                "test", "test", "test-bucket", 5);
    }

    private static FlociFotoStorageService servicio(S3Client s3) {
        FlociFotoStorageService service = new FlociFotoStorageService(props(), s3);
        ReflectionTestUtils.setField(service, "ambiente", "dev");
        return service;
    }

    private static byte[] imagenPng() throws Exception {
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(Color.RED);
            g.fillRect(0, 0, 100, 100);
        } finally {
            g.dispose();
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        }
    }

    @Test
    @DisplayName("bucket nuevo: lo crea con política pública y sube, devuelve URL path-style")
    void bucketNuevo_creaYSube() throws Exception {
        S3Client s3 = mock(S3Client.class);
        when(s3.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(NoSuchBucketException.builder().message("no existe").build());

        byte[] png = imagenPng();
        String url = servicio(s3).subir("image/png", "foto.png",
                new ByteArrayInputStream(png), png.length);

        assertThat(url).startsWith("http://localhost:4566/test-bucket/");
        assertThat(url).endsWith(".jpg");
        verify(s3).createBucket(any(CreateBucketRequest.class));
        verify(s3).putBucketPolicy(any(PutBucketPolicyRequest.class));
        verify(s3).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("bucket existente: no lo recrea ni repite la política")
    void bucketExistente_soloSube() throws Exception {
        S3Client s3 = mock(S3Client.class);
        when(s3.headBucket(any(HeadBucketRequest.class)))
                .thenReturn(HeadBucketResponse.builder().build());

        byte[] png = imagenPng();
        servicio(s3).subir("image/png", "foto.png",
                new ByteArrayInputStream(png), png.length);

        verify(s3, never()).createBucket(any(CreateBucketRequest.class));
        verify(s3, never()).putBucketPolicy(any(PutBucketPolicyRequest.class));
        verify(s3).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("archivo mayor al máximo se rechaza sin tocar S3")
    void muyGrande_rechazado() {
        S3Client s3 = mock(S3Client.class);
        assertThatThrownBy(() -> servicio(s3).subir("image/jpeg", "f.jpg",
                new ByteArrayInputStream(new byte[10]), 6 * 1024 * 1024))
                .isInstanceOf(ValidacionException.class);
        verify(s3, never()).headBucket(any(HeadBucketRequest.class));
    }

    @Test
    @DisplayName("tipo no permitido se rechaza sin tocar S3")
    void tipoNoPermitido_rechazado() {
        S3Client s3 = mock(S3Client.class);
        assertThatThrownBy(() -> servicio(s3).subir("application/pdf", "doc.pdf",
                new ByteArrayInputStream(new byte[10]), 10))
                .isInstanceOf(ValidacionException.class);
        verify(s3, never()).headBucket(any(HeadBucketRequest.class));
    }

    @Test
    @DisplayName("prod exige URL pública HTTPS igual que el impl MinIO")
    void prodExigeHttps() {
        S3Client s3 = mock(S3Client.class);
        FlociFotoStorageService service = new FlociFotoStorageService(props(), s3);
        ReflectionTestUtils.setField(service, "ambiente", "prod");
        assertThatThrownBy(service::validarAmbiente)
                .isInstanceOf(IllegalStateException.class);
    }
}
