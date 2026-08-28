package mx.ferreteria.api.seg.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import mx.ferreteria.api.common.error.ValidacionException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.common.security.JwtProperties;
import mx.ferreteria.api.common.security.JwtService;
import mx.ferreteria.api.common.security.UserPrincipal;
import mx.ferreteria.api.rh.service.EmpleadoGateway;
import mx.ferreteria.api.seg.dto.AuthDtos.ChangePasswordRequest;
import mx.ferreteria.api.seg.dto.AuthDtos.LoginRequest;
import mx.ferreteria.api.seg.dto.AuthDtos.RegisterRequest;
import mx.ferreteria.api.seg.dto.AuthDtos.RegisterResponse;
import mx.ferreteria.api.seg.dto.AuthDtos.TokenResponse;
import mx.ferreteria.api.seg.service.AuthUserGateway.AuthUser;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock
    AuthUserGateway gateway;

    @Mock
    SegAdminGateway admin;

    @Mock
    EmpleadoGateway empleados;

    final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    AuthService service;

    final AuthUser activo = new AuthUser(7, "cajero1",
            new BCryptPasswordEncoder().encode("Secreta123"), true, 42);

    @BeforeEach
    void setUp() {
        service = new AuthService(gateway, admin, empleados, encoder,
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
        verify(gateway).revokeAllRefreshTokens(7); // login nuevo = una sola sesión activa
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

        when(gateway.findRefreshRow(anyString()))
                .thenReturn(Optional.of(new AuthUserGateway.RefreshRow(7,
                        Instant.now().plusSeconds(3600), null)));
        when(gateway.findActiveRefreshOwner(anyString(), any(Instant.class)))
                .thenReturn(Optional.of(new AuthUserGateway.RefreshOwner(7, "cajero1", 42)));

        TokenResponse r2 = service.refresh(login.refreshToken(), RequestMeta.UNKNOWN);

        verify(gateway).revokeByHash(JwtService.sha256Base64(login.refreshToken()));
        verify(gateway).saveRefreshToken(eq(7),
                eq(JwtService.sha256Base64(r2.refreshToken())), any(Instant.class));
        assertThat(r2.refreshToken()).isNotEqualTo(login.refreshToken());
    }

    @Test
    @DisplayName("refresh revocado: marca error 'ya expiro' (TOKEN_EXPIRADO) sin volver a revocar")
    void refresh_revocado_throwsExpired() {
        mockUserOk();
        TokenResponse login = service.login(new LoginRequest("cajero1", "Secreta123"),
                RequestMeta.UNKNOWN);
        when(gateway.findRefreshRow(anyString()))
                .thenReturn(Optional.of(new AuthUserGateway.RefreshRow(7,
                        Instant.now().plusSeconds(3600), Instant.now())));

        assertThatThrownBy(() -> service.refresh(login.refreshToken(), RequestMeta.UNKNOWN))
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.TOKEN_EXPIRADO));
        // el token ya estaba revocado: no hay que marcarlo de nuevo
        verify(gateway, never()).revokeByHash(anyString());
    }

    @Test
    @DisplayName("refresh expirado por vigencia de BD: TOKEN_EXPIRADO y se revoca el hash")
    void refresh_expiradoEnBD_throwsExpired() {
        mockUserOk();
        TokenResponse login = service.login(new LoginRequest("cajero1", "Secreta123"),
                RequestMeta.UNKNOWN);
        when(gateway.findRefreshRow(anyString()))
                .thenReturn(Optional.of(new AuthUserGateway.RefreshRow(7,
                        Instant.now().minusSeconds(60), null)));

        assertThatThrownBy(() -> service.refresh(login.refreshToken(), RequestMeta.UNKNOWN))
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.TOKEN_EXPIRADO));
        verify(gateway).revokeByHash(anyString());
    }

    @Test
    @DisplayName("refresh sin registro en BD: TOKEN_EXPIRADO y revoke defensivo")
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
        // fila del hash pertenece a OTRO usuario (uid=8) que el claim del JWT (7)
        when(gateway.findRefreshRow(anyString()))
                .thenReturn(Optional.of(new AuthUserGateway.RefreshRow(8,
                        Instant.now().plusSeconds(3600), null)));

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

    @Test
    @DisplayName("register: crea empleado + usuario ligado + UNICO rol ENCARGADO_CAJA")
    void register_createsEmpleadoUsuarioYRoleUnico() {
        when(empleados.create(anyInt(), anyString(), anyString(), any(), any(), any(),
                any(), anyString(), any(), any(), any(), any(), any(), any()))
                .thenReturn(5);
        when(admin.createUsuario(eq("nuevo01"), eq("nuevo01@ejemplo.mx"), anyString(),
                eq(5), anyBoolean())).thenReturn(11);

        RegisterResponse r = service.register(new RegisterRequest(
                "nuevo01", "nuevo01@ejemplo.mx", "Secreta123",
                "Juan", "Pérez", "López", "555", 3));

        assertThat(r.usuarioId()).isEqualTo(11);
        assertThat(r.empleadoId()).isEqualTo(5);
        assertThat(r.username()).isEqualTo("nuevo01");
        // el único rol posible es ENCARGADO_CAJA, nunca ADMINISTRADOR
        verify(empleados).create(eq(3), eq("Juan"), eq("Pérez"), any(), any(), any(),
                eq("555"), eq("nuevo01@ejemplo.mx"), any(), any(), any(), any(), any(), any());
        verify(admin).reemplazarRoles(11, Set.of(AuthService.ROL_REGISTRO));
        org.mockito.ArgumentCaptor<String> hash =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(admin).createUsuario(eq("nuevo01"), eq("nuevo01@ejemplo.mx"), hash.capture(),
                eq(5), eq(true));
        assertThat(hash.getValue()).isNotEqualTo("Secreta123");
        assertThat(encoder.matches("Secreta123", hash.getValue())).isTrue();
    }

    @Test
    @DisplayName("changePassword: valida la actual contra el hash y persiste BCrypt nuevo")
    void changePassword_validaActualYPersisteNuevo() {
        UserPrincipal up = new UserPrincipal(7, "cajero1", 42, List.of("VENDEDOR"));
        when(gateway.findByUsername("cajero1")).thenReturn(Optional.of(activo));

        service.changePassword(up, new ChangePasswordRequest("Secreta123", "NuevaClave99"));

        org.mockito.ArgumentCaptor<String> hash =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(admin).actualizarPassword(eq(7), hash.capture());
        assertThat(hash.getValue()).isNotEqualTo("NuevaClave99");
        assertThat(encoder.matches("NuevaClave99", hash.getValue())).isTrue();
    }

    @Test
    @DisplayName("changePassword: password actual incorrecta -> 401 CREDENCIALES_INVALIDAS")
    void changePassword_actualErronea_rejected() {
        UserPrincipal up = new UserPrincipal(7, "cajero1", 42, List.of("VENDEDOR"));
        when(gateway.findByUsername("cajero1")).thenReturn(Optional.of(activo));

        assertThatThrownBy(() -> service.changePassword(up,
                new ChangePasswordRequest("mala", "NuevaClave99")))
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CREDENCIALES_INVALIDAS));
        verify(admin, never()).actualizarPassword(anyInt(), anyString());
    }

    @Test
    @DisplayName("changePassword: usuario inexistente -> 401 sin revelar existencia")
    void changePassword_usuarioDesconocido_rejected() {
        when(gateway.findByUsername("fantasma")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changePassword(
                new UserPrincipal(99, "fantasma", null, List.of()),
                new ChangePasswordRequest("x", "NuevaClave99")))
                .isInstanceOf(ValidacionException.class);
    }

    @Test
    @DisplayName("me: incluye la informacion principal del empleado cuando existe vínculo")
    void me_incluyeResumenEmpleado() {
        var resumen = new mx.ferreteria.api.rh.dto.EmpleadoDtos.EmpleadoResumen(
                42, "Juan Pérez", "Vendedor", "juan@x.mx", "555", true);
        when(empleados.resumenById(42)).thenReturn(Optional.of(resumen));

        var me = service.me(new UserPrincipal(7, "cajero1", 42, List.of("VENDEDOR")));

        assertThat(me.empleado()).isNotNull();
        assertThat(me.empleado().nombreCompleto()).isEqualTo("Juan Pérez");
        assertThat(me.empleado().puestoNombre()).isEqualTo("Vendedor");
    }
}
