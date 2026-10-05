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
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import mx.ferreteria.api.common.error.ValidacionException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.common.security.GoogleAuthProperties;
import mx.ferreteria.api.rh.service.EmpleadoGateway;
import mx.ferreteria.api.seg.dto.AuthDtos.OtpChallengeResponse;
import mx.ferreteria.api.seg.service.AuthUserGateway.AuthUser;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GoogleAuthServiceTest {

    @Mock
    AuthUserGateway usuarios;
    @Mock
    SegAdminGateway admin;
    @Mock
    EmpleadoGateway empleados;
    @Mock
    OtpGateway otp;
    @Mock
    AuthService authService;
    @Mock
    PasswordEncoder passwordEncoder;

    GoogleAuthService service;
    MockRestServiceServer server;

    final GoogleAuthProperties props = new GoogleAuthProperties("cid-test", "sec-test",
            "http://localhost:8080/api/v1/auth/oauth2/google/callback",
            "http://localhost:5173/auth/callback");

    @BeforeEach
    void setUp() {
        service = new GoogleAuthService(props, usuarios, admin, empleados, otp,
                authService, passwordEncoder, new ObjectMapper());
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service.restClientParaTests(builder.build());
    }

    private void stubGoogle(String sub, String email, boolean verificado) {
        long exp = Instant.now().plusSeconds(3600).getEpochSecond();
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andExpect(method(POST))
                .andRespond(withSuccess("{\"id_token\":\"IDTOKEN\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(
                org.hamcrest.Matchers.startsWith("https://oauth2.googleapis.com/tokeninfo")))
                .andExpect(method(GET))
                .andRespond(withSuccess("{\"aud\":\"cid-test\","
                        + "\"iss\":\"https://accounts.google.com\","
                        + "\"exp\":" + exp + ","
                        + "\"sub\":\"" + sub + "\","
                        + "\"email\":\"" + email + "\","
                        + "\"email_verified\":\"" + verificado + "\","
                        + "\"name\":\"Juan Perez\"}",
                        MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("urlAutorizacion: apunta a Google con client_id y state")
    void urlAutorizacion_ok() {
        String url = service.urlAutorizacion("estado-1");

        assertThat(url).startsWith("https://accounts.google.com/o/oauth2/v2/auth");
        assertThat(url).contains("client_id=cid-test");
        assertThat(url).contains("state=estado-1");
        assertThat(url).contains("scope=openid");
    }

    @Test
    @DisplayName("sin configurar: url y callback fallan con OAUTH_FALLIDO")
    void sinConfig_rejected() {
        var vacio = new GoogleAuthService(
                new GoogleAuthProperties(null, null, null, null),
                usuarios, admin, empleados, otp, authService, passwordEncoder,
                new ObjectMapper());

        assertThatThrownBy(() -> vacio.urlAutorizacion(null))
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.OAUTH_FALLIDO));
        assertThatThrownBy(() -> vacio.callback("code"))
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.OAUTH_FALLIDO));
    }

    @Test
    @DisplayName("callback con sub conocido: entra directo con desafío")
    void callback_subConocido_ok() {
        stubGoogle("sub-1", "cajero@ferreteria.local", true);
        var user = new AuthUser(7, "cajero1", "hash", true, false, 42);
        when(usuarios.findByGoogleSub("sub-1")).thenReturn(Optional.of(user));
        when(otp.crearDesafio(eq(7), any())).thenReturn("ch-7");
        var ch = new OtpChallengeResponse("ch-7", List.of("email"), "ca***@x", null, 300);
        when(authService.desafioPara(7, "ch-7")).thenReturn(ch);

        assertThat(service.callback("code-abc")).isSameAs(ch);
        verify(usuarios, never()).linkGoogleAccount(anyInt(), anyString());
        verify(admin, never()).createUsuario(anyString(), anyString(), anyString(),
                any(), any(Boolean.class));
        server.verify();
    }

    @Test
    @DisplayName("callback con email existente: vincula google_sub y devuelve desafío")
    void callback_vinculaPorEmail() {
        stubGoogle("sub-9", "nuevo@ferreteria.local", true);
        var user = new AuthUser(9, "nuevo", "hash", true, false, 43);
        when(usuarios.findByGoogleSub("sub-9")).thenReturn(Optional.empty());
        when(usuarios.findByEmail("nuevo@ferreteria.local")).thenReturn(Optional.of(user));
        when(otp.crearDesafio(eq(9), any())).thenReturn("ch-9");
        var ch = new OtpChallengeResponse("ch-9", List.of("email"), "nu***@x", null, 300);
        when(authService.desafioPara(9, "ch-9")).thenReturn(ch);

        assertThat(service.callback("code-abc")).isSameAs(ch);
        verify(usuarios).linkGoogleAccount(9, "sub-9");
        server.verify();
    }

    @Test
    @DisplayName("callback con email nuevo: crea empleado+usuario ENCARGADO_CAJA y vincula")
    void callback_creaUsuario() {
        stubGoogle("sub-nuevo", "juan@gmail.com", true);
        when(usuarios.findByGoogleSub("sub-nuevo")).thenReturn(Optional.empty());
        when(usuarios.findByEmail("juan@gmail.com")).thenReturn(Optional.empty());
        when(usuarios.findByUsername("juan")).thenReturn(Optional.empty());
        when(empleados.puestoIdPorNombre("Auxiliar administrativo"))
                .thenReturn(Optional.of(6));
        when(empleados.create(any())).thenReturn(50);
        when(passwordEncoder.encode(anyString())).thenReturn("hash-aleatorio");
        when(admin.createUsuario(eq("juan"), eq("juan@gmail.com"), eq("hash-aleatorio"),
                eq(50), eq(true))).thenReturn(11);

        service.callback("code-abc");

        verify(empleados).create(any(EmpleadoGateway.EmpleadoDatos.class));
        verify(admin).reemplazarRoles(11, Set.of(AuthService.ROL_REGISTRO));
        verify(usuarios).linkGoogleAccount(11, "sub-nuevo");
        verify(otp).crearDesafio(eq(11), any());
        server.verify();
    }

    @Test
    @DisplayName("callback con email no verificado: OAUTH_FALLIDO sin crear nada")
    void callback_noVerificado_rejected() {
        stubGoogle("sub-x", "x@gmail.com", false);

        assertThatThrownBy(() -> service.callback("code-abc"))
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.OAUTH_FALLIDO));
        verify(admin, never()).createUsuario(anyString(), anyString(), anyString(),
                any(), any(Boolean.class));
        server.verify();
    }
}
