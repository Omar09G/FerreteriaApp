package mx.ferreteria.api.seg.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import mx.ferreteria.api.common.error.ValidacionException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.common.security.JwtService;
import mx.ferreteria.api.common.security.OtpProperties;
import mx.ferreteria.api.seg.service.AuthService.LoginResult;
import mx.ferreteria.api.seg.service.AuthUserGateway.Contactos;
import mx.ferreteria.api.seg.service.OtpGateway.Desafio;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OtpServiceTest {

    @Mock
    OtpGateway otp;
    @Mock
    AuthUserGateway usuarios;
    @Mock
    AuthService authService;
    @Mock
    OtpEmailSender emailSender;
    @Mock
    OtpWhatsappPort whatsapp;

    OtpService service;

    @BeforeEach
    void setUp() {
        service = new OtpService(otp, usuarios, authService,
                new OtpProperties(5, 5, 60, 6), emailSender, whatsapp);
    }

    private Desafio pendiente() {
        return new Desafio(1L, "ch-1", 7, "email", null, 0,
                Instant.now().plusSeconds(300), null, null, null);
    }

    private Desafio conCodigo(String codigo, int intentos) {
        return new Desafio(1L, "ch-1", 7, "email", JwtService.sha256Base64(codigo),
                intentos, Instant.now().plusSeconds(300),
                Instant.now().minusSeconds(120), null, null);
    }

    @Test
    @DisplayName("enviar por email: genera código, lo envía y fija el hash")
    void enviar_email_ok() {
        when(otp.findByChallengeId("ch-1")).thenReturn(Optional.of(pendiente()));
        when(usuarios.contactosDe(7)).thenReturn(
                Optional.of(new Contactos("cajero@ferreteria.local", null, null)));

        service.enviar("ch-1", "email");

        ArgumentCaptor<String> codigo = ArgumentCaptor.forClass(String.class);
        verify(emailSender).enviarCodigo(eq("cajero@ferreteria.local"), codigo.capture(), eq(5));
        assertThat(codigo.getValue()).matches("\\d{6}");
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(otp).fijarCodigo(eq("ch-1"), eq("email"), hash.capture(), any(Instant.class));
        assertThat(hash.getValue()).isEqualTo(JwtService.sha256Base64(codigo.getValue()));
    }

    @Test
    @DisplayName("enviar por whatsapp: delega en el puerto y fija el hash")
    void enviar_whatsapp_ok() {
        when(otp.findByChallengeId("ch-1")).thenReturn(Optional.of(pendiente()));
        when(usuarios.contactosDe(7)).thenReturn(
                Optional.of(new Contactos(null, "5551234567", null)));
        when(whatsapp.enviarTexto(eq("5551234567"), anyString())).thenReturn(true);

        service.enviar("ch-1", "whatsapp");

        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
        verify(whatsapp).enviarTexto(eq("5551234567"), texto.capture());
        assertThat(texto.getValue()).containsPattern("\\d{6}");
        verify(otp).fijarCodigo(eq("ch-1"), eq("whatsapp"), anyString(), any(Instant.class));
    }

    @Test
    @DisplayName("enviar por whatsapp fallido: SERVICIO_NO_DISPONIBLE sin fijar código")
    void enviar_whatsapp_falla() {
        when(otp.findByChallengeId("ch-1")).thenReturn(Optional.of(pendiente()));
        when(usuarios.contactosDe(7)).thenReturn(
                Optional.of(new Contactos(null, "5551234567", null)));
        when(whatsapp.enviarTexto(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.enviar("ch-1", "whatsapp"))
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode())
                                .isEqualTo(ErrorCode.SERVICIO_NO_DISPONIBLE));
        verify(otp, never()).fijarCodigo(anyString(), anyString(), anyString(),
                any(Instant.class));
    }

    @Test
    @DisplayName("desafio vigente: devuelve canales y destinos enmascarados")
    void desafio_ok() {
        when(otp.findByChallengeId("ch-1")).thenReturn(Optional.of(pendiente()));
        var ch = new mx.ferreteria.api.seg.dto.AuthDtos.OtpChallengeResponse("ch-1",
                java.util.List.of("email"), "ca***@x", null, 300);
        when(authService.desafioPara(7, "ch-1")).thenReturn(ch);

        assertThat(service.desafio("ch-1")).isSameAs(ch);
    }

    @Test
    @DisplayName("desafio desconocido o vencido: OTP_INVALIDO")
    void desafio_desconocido_rejected() {
        when(otp.findByChallengeId("nope")).thenReturn(Optional.empty());
        var vencido = new Desafio(1L, "ch-v", 7, "email", "hash", 0,
                Instant.now().minusSeconds(1), Instant.now().minusSeconds(400), null, null);
        when(otp.findByChallengeId("ch-v")).thenReturn(Optional.of(vencido));

        assertThatThrownBy(() -> service.desafio("nope"))
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.OTP_INVALIDO));
        assertThatThrownBy(() -> service.desafio("ch-v"))
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.OTP_INVALIDO));
    }

    @Test
    @DisplayName("enviar con challenge desconocido: OTP_INVALIDO (anti-enumeración)")
    void enviar_desconocido_rejected() {
        when(otp.findByChallengeId("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.enviar("nope", "email"))
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.OTP_INVALIDO));
        verify(emailSender, never()).enviarCodigo(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("enviar por email sin correo registrado: CANAL_NO_DISPONIBLE")
    void enviar_sinDestino_rejected() {
        when(otp.findByChallengeId("ch-1")).thenReturn(Optional.of(pendiente()));
        when(usuarios.contactosDe(7)).thenReturn(
                Optional.of(new Contactos(null, "5551234567", null)));

        assertThatThrownBy(() -> service.enviar("ch-1", "email"))
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CANAL_NO_DISPONIBLE));
    }

    @Test
    @DisplayName("reenvío antes de 60 s: LIMITE_VELOCIDAD_EXCEDIDO")
    void enviar_reenvioPronto_rejected() {
        var reciente = new Desafio(1L, "ch-1", 7, "email", "hash", 0,
                Instant.now().plusSeconds(300), Instant.now(), null, null);
        when(otp.findByChallengeId("ch-1")).thenReturn(Optional.of(reciente));

        assertThatThrownBy(() -> service.enviar("ch-1", "email"))
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.LIMITE_VELOCIDAD_EXCEDIDO));
    }

    @Test
    @DisplayName("verificar código correcto: consume y emite sesión")
    void verificar_ok_emiteSesion() {
        when(otp.findByChallengeId("ch-1")).thenReturn(Optional.of(conCodigo("482913", 0)));
        var esperado = new LoginResult(
                new mx.ferreteria.api.seg.dto.AuthDtos.TokenResponse("acc", null, 28800, null),
                "ref");
        when(authService.emitirSesion(eq(7), any())).thenReturn(esperado);

        LoginResult r = service.verificar("ch-1", "482913", RequestMeta.UNKNOWN);

        assertThat(r).isSameAs(esperado);
        verify(otp).consumir("ch-1");
        verify(authService).emitirSesion(eq(7), any());
    }

    @Test
    @DisplayName("verificar código erróneo: OTP_INVALIDO e incrementa intentos")
    void verificar_mal_incrementa() {
        when(otp.findByChallengeId("ch-1")).thenReturn(Optional.of(conCodigo("482913", 0)));

        assertThatThrownBy(() -> service.verificar("ch-1", "000000", RequestMeta.UNKNOWN))
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.OTP_INVALIDO));
        verify(otp).incrementarIntentos("ch-1");
        verify(otp, never()).consumir(anyString());
    }

    @Test
    @DisplayName("verificar en el último intento: OTP_AGOTADO y revoca")
    void verificar_ultimoIntento_agota() {
        when(otp.findByChallengeId("ch-1")).thenReturn(Optional.of(conCodigo("482913", 4)));

        assertThatThrownBy(() -> service.verificar("ch-1", "000000", RequestMeta.UNKNOWN))
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.OTP_AGOTADO));
        verify(otp).revocar("ch-1");
        verify(authService, never()).emitirSesion(anyInt(), any());
    }

    @Test
    @DisplayName("verificar expirado: OTP_EXPIRADO y revoca")
    void verificar_expirado_rejected() {
        var expirado = new Desafio(1L, "ch-1", 7, "email",
                JwtService.sha256Base64("482913"), 0,
                Instant.now().minusSeconds(10), Instant.now().minusSeconds(400), null, null);
        when(otp.findByChallengeId("ch-1")).thenReturn(Optional.of(expirado));

        assertThatThrownBy(() -> service.verificar("ch-1", "482913", RequestMeta.UNKNOWN))
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.OTP_EXPIRADO));
        verify(otp).revocar("ch-1");
    }

    @Test
    @DisplayName("verificar consumido o desconocido: OTP_INVALIDO")
    void verificar_consumido_rejected() {
        var consumido = new Desafio(1L, "ch-1", 7, "email", "hash", 0,
                Instant.now().plusSeconds(300), Instant.now().minusSeconds(60),
                Instant.now(), null);
        when(otp.findByChallengeId("ch-1")).thenReturn(Optional.of(consumido));
        when(otp.findByChallengeId("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verificar("ch-1", "482913", RequestMeta.UNKNOWN))
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.OTP_INVALIDO));
        assertThatThrownBy(() -> service.verificar("nope", "482913", RequestMeta.UNKNOWN))
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.OTP_INVALIDO));
    }
}
