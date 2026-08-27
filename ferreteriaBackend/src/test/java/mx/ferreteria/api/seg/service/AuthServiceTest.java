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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import mx.ferreteria.api.common.error.ValidacionException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.common.security.JwtProperties;
import mx.ferreteria.api.common.security.JwtService;
import mx.ferreteria.api.seg.dto.AuthDtos.LoginRequest;
import mx.ferreteria.api.seg.dto.AuthDtos.TokenResponse;
import mx.ferreteria.api.seg.service.AuthUserGateway.AuthUser;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock
    AuthUserGateway gateway;

    final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    AuthService service;

    final AuthUser activo = new AuthUser(7, "cajero1",
            new BCryptPasswordEncoder().encode("Secreta123"), true, 42);

    @BeforeEach
    void setUp() {
        service = new AuthService(gateway, encoder,
                new JwtService(new JwtProperties("0123456789abcdef0123456789abcdef", 15, 8)));
    }

    private void mockUserOk() {
        when(gateway.findByUsername("cajero1")).thenReturn(Optional.of(activo));
        when(gateway.rolesOf(7)).thenReturn(List.of("VENDEDOR"));
        when(gateway.abrirSesion(eq(7), any(), any())).thenReturn(1);
    }

    @Test
    @DisplayName("login feliz: tokens emitidos, sesion abierta y refresh persistido hasheado")
    void login_ok_issuesTokensAndAudits() {
        mockUserOk();

        TokenResponse r = service.login(new LoginRequest("cajero1", "Secreta123"), RequestMeta.UNKNOWN);

        assertThat(r.accessToken()).isNotBlank();
        assertThat(r.refreshToken()).isNotBlank();
        assertThat(r.usuario().username()).isEqualTo("cajero1");
        assertThat(r.usuario().roles()).containsExactly("VENDEDOR");

        verify(gateway).abrirSesion(eq(7), any(), any());
        verify(gateway).saveRefreshToken(eq(7), anyString(), any(Instant.class));
        verify(gateway).updateUltimoLogin(7);
    }

    @Test
    @DisplayName("password incorrecta: CREDENCIALES_INVALIDAS y auditoria LOGIN_FALLIDO")
    void login_wrongPassword_throws401_andAuditsFailure() {
        mockUserOk();

        assertThatThrownBy(() -> service.login(new LoginRequest("cajero1", "mala"), RequestMeta.UNKNOWN))
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CREDENCIALES_INVALIDAS));

        verify(gateway, never()).saveRefreshToken(anyInt(), anyString(), any());
        verify(gateway, never()).abrirSesion(anyInt(), any(), any());
    }

    @Test
    @DisplayName("usuario inexistente o inactivo: mismo error (sin filtrar existencia)")
    void login_unknownOrInactive_sameGenericError() {
        when(gateway.findByUsername("fantasma")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("fantasma", "x"), RequestMeta.UNKNOWN))
                .isInstanceOf(ValidacionException.class);
        var inactivo = new AuthUser(9, "baja", encoder.encode("x"), false, null);
        when(gateway.findByUsername("baja")).thenReturn(Optional.of(inactivo));
        assertThatThrownBy(() -> service.login(new LoginRequest("baja", "x"),
                RequestMeta.UNKNOWN))
                .isInstanceOf(ValidacionException.class);
    }

    @Test
    @DisplayName("refresh: rota el hash usado (revoca) y entrega par nuevo válido")
    void refresh_rotatesHash() {
        mockUserOk();
        TokenResponse login = service.login(new LoginRequest("cajero1", "Secreta123"), RequestMeta.UNKNOWN);

        when(gateway.findActiveRefreshOwner(anyString(), any(Instant.class)))
                .thenReturn(Optional.of(new AuthUserGateway.RefreshOwner(7, "cajero1", 42)));

        TokenResponse r2 = service.refresh(login.refreshToken(), RequestMeta.UNKNOWN);

        verify(gateway).revokeByHash(JwtService.sha256Base64(login.refreshToken()));
        verify(gateway).saveRefreshToken(eq(7),
                eq(JwtService.sha256Base64(r2.refreshToken())), any(Instant.class));
        assertThat(r2.refreshToken()).isNotEqualTo(login.refreshToken());
    }

    @Test
    @DisplayName("refresh revocado/expirado: TOKEN_EXPIRADO y revoke defensivo")
    void refresh_unknownHash_throwsExpired() {
        when(gateway.findActiveRefreshOwner(anyString(), any(Instant.class)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refresh("token-basura", RequestMeta.UNKNOWN))
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.TOKEN_EXPIRADO));
        verify(gateway).revokeByHash(anyString());
    }

    @Test
    @DisplayName("refresh cuyo hash apunta a otro usuario: TOKEN_EXPIRADO (sesion invalida)")
    void refresh_uidMismatch_throwsExpired() {
        mockUserOk();
        TokenResponse login = service.login(new LoginRequest("cajero1", "Secreta123"),
                RequestMeta.UNKNOWN);
        // owner del hash pertenece a OTRO usuario (uid=8) que el claim del JWT (7)
        when(gateway.findActiveRefreshOwner(anyString(), any(Instant.class)))
                .thenReturn(Optional.of(new AuthUserGateway.RefreshOwner(8, "otro", null)));

        assertThatThrownBy(() -> service.refresh(login.refreshToken(), RequestMeta.UNKNOWN))
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.TOKEN_EXPIRADO));
        verify(gateway).revokeByHash(JwtService.sha256Base64(login.refreshToken()));
        verify(gateway, never()).rolesOf(8);
    }

    @Test
    @DisplayName("logout: cierra la sesion ligada al refresh y revoca el hash")
    void logout_closesSessionAndRevokes() {
        mockUserOk();
        TokenResponse login = service.login(new LoginRequest("cajero1", "Secreta123"),
                RequestMeta.UNKNOWN);

        when(gateway.findActiveRefreshOwner(anyString(), any(Instant.class)))
                .thenReturn(Optional.of(new AuthUserGateway.RefreshOwner(7, "cajero1", 42)));

        assertThat(service.logout(login.refreshToken())).isTrue();
        verify(gateway).cerrarSesion(1);          // sesion abierta en login (stub)
        verify(gateway).revokeByHash(JwtService.sha256Base64(login.refreshToken()));
    }
}
