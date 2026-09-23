package mx.ferreteria.api.seg.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
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

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import javax.crypto.SecretKey;

import mx.ferreteria.api.common.error.ValidacionException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.common.time.ZonaHoraria;
import mx.ferreteria.api.rh.service.EmpleadoGateway;
import mx.ferreteria.api.common.security.AuthCookieProperties;
import mx.ferreteria.api.common.security.JwtProperties;
import mx.ferreteria.api.common.security.JwtService;
import mx.ferreteria.api.common.security.UserPrincipal;
import mx.ferreteria.api.rh.service.EmpleadoGateway;
import mx.ferreteria.api.seg.dto.AuthDtos.ChangePasswordRequest;
import mx.ferreteria.api.seg.dto.AuthDtos.LoginRequest;
import mx.ferreteria.api.seg.dto.AuthDtos.RegisterRequest;
import mx.ferreteria.api.seg.dto.AuthDtos.RegisterResponse;
import mx.ferreteria.api.seg.dto.AuthDtos.TokenResponse;
import mx.ferreteria.api.seg.service.AuthService.LoginResult;
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
    JwtService jwtService;
    AuthService service;

    final AuthUser activo = new AuthUser(7, "cajero1",
            new BCryptPasswordEncoder().encode("Secreta123"), true, false, 42);

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(new JwtProperties("0123456789abcdef0123456789abcdef", null /*previousSecret*/, 15, 8));
        service = new AuthService(gateway, admin, empleados, encoder,
                jwtService,
                new AuthCookieProperties(false, "Lax", "/api/v1/auth", "rt", "at"));
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

        LoginResult result = service.login(new LoginRequest("cajero1", "Secreta123"),
                RequestMeta.UNKNOWN);
        TokenResponse r = result.body();

        assertThat(r.accessToken()).isNotBlank();
        // refresh NO se devuelve en body (viaja en cookie HttpOnly)
        assertThat(r.refreshToken()).isNull();
        assertThat(result.refreshRaw()).isNotBlank();
        assertThat(r.usuario().username()).isEqualTo("cajero1");
        assertThat(r.usuario().roles()).containsExactly("VENDEDOR");

        verify(gateway).abrirSesion(eq(7), any(), any());
        verify(gateway).revokeAllRefreshTokens(7); // login nuevo = una sola sesión activa
        verify(gateway).saveRefreshToken(eq(7),
                eq(JwtService.sha256Base64(result.refreshRaw())), any(Instant.class));
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
        var inactivo = new AuthUser(9, "baja", encoder.encode("x"), false, false, null);
        when(gateway.findByUsername("baja")).thenReturn(Optional.of(inactivo));
        assertThatThrownBy(() -> service.login(new LoginRequest("baja", "x"),
                RequestMeta.UNKNOWN))
                .isInstanceOf(ValidacionException.class);
    }

    @Test
    @DisplayName("refresh: rota el hash usado (revoca) y entrega par nuevo válido")
    void refresh_rotatesHash() {
        mockUserOk();
        LoginResult login = service.login(new LoginRequest("cajero1", "Secreta123"),
                RequestMeta.UNKNOWN);

        when(gateway.findRefreshRow(anyString()))
                .thenReturn(Optional.of(new AuthUserGateway.RefreshRow(7,
                        Instant.now().plusSeconds(3600), null)));
        when(gateway.revokeByHash(anyString())).thenReturn(true);
        when(gateway.findActiveRefreshOwner(anyString(), any(Instant.class)))
                .thenReturn(Optional.of(new AuthUserGateway.RefreshOwner(7, "cajero1", 42)));

        LoginResult r2 = service.refresh(login.refreshRaw(), RequestMeta.UNKNOWN, null);

        verify(gateway).revokeByHash(JwtService.sha256Base64(login.refreshRaw()));
        verify(gateway).saveRefreshToken(eq(7),
                eq(JwtService.sha256Base64(r2.refreshRaw())), any(Instant.class));
        assertThat(r2.refreshRaw()).isNotEqualTo(login.refreshRaw());
    }

    @Test
    @DisplayName("refresh revocado: marca error 'ya expiro' (TOKEN_EXPIRADO) sin volver a revocar")
    void refresh_revocado_throwsExpired() {
        mockUserOk();
        LoginResult login = service.login(new LoginRequest("cajero1", "Secreta123"),
                RequestMeta.UNKNOWN);
        when(gateway.findRefreshRow(anyString()))
                .thenReturn(Optional.of(new AuthUserGateway.RefreshRow(7,
                        Instant.now().plusSeconds(3600), Instant.now())));

        assertThatThrownBy(() -> service.refresh(login.refreshRaw(), RequestMeta.UNKNOWN, null))
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.TOKEN_EXPIRADO));
        // el token ya estaba revocado: no hay que marcarlo de nuevo
        verify(gateway, never()).revokeByHash(anyString());
    }

    @Test
    @DisplayName("refresh expirado por vigencia de BD: TOKEN_EXPIRADO y se revoca el hash")
    void refresh_expiradoEnBD_throwsExpired() {
        mockUserOk();
        LoginResult login = service.login(new LoginRequest("cajero1", "Secreta123"),
                RequestMeta.UNKNOWN);
        when(gateway.findRefreshRow(anyString()))
                .thenReturn(Optional.of(new AuthUserGateway.RefreshRow(7,
                        Instant.now().minusSeconds(60), null)));

        assertThatThrownBy(() -> service.refresh(login.refreshRaw(), RequestMeta.UNKNOWN, null))
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.TOKEN_EXPIRADO));
        verify(gateway).revokeByHash(anyString());
    }

    @Test
    @DisplayName("refresh sin registro en BD: TOKEN_EXPIRADO y revoke defensivo")
    void refresh_unknownHash_throwsExpired() {
        when(gateway.findActiveRefreshOwner(anyString(), any(Instant.class)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refresh("token-basura", RequestMeta.UNKNOWN, null))
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.TOKEN_EXPIRADO));
        verify(gateway).revokeByHash(anyString());
    }

    @Test
    @DisplayName("refresh cuyo hash apunta a otro usuario: TOKEN_EXPIRADO (sesion invalida)")
    void refresh_uidMismatch_throwsExpired() {
        mockUserOk();
        LoginResult login = service.login(new LoginRequest("cajero1", "Secreta123"),
                RequestMeta.UNKNOWN);
        // fila del hash pertenece a OTRO usuario (uid=8) que el claim del JWT (7)
        when(gateway.findRefreshRow(anyString()))
                .thenReturn(Optional.of(new AuthUserGateway.RefreshRow(8,
                        Instant.now().plusSeconds(3600), null)));

        assertThatThrownBy(() -> service.refresh(login.refreshRaw(), RequestMeta.UNKNOWN, null))
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.TOKEN_EXPIRADO));
        verify(gateway).revokeByHash(JwtService.sha256Base64(login.refreshRaw()));
        verify(gateway, never()).rolesOf(8);
    }

    @Test
    @DisplayName("logout: cierra la sesion ligada al refresh y revoca el hash")
    void logout_closesSessionAndRevokes() {
        mockUserOk();
        LoginResult login = service.login(new LoginRequest("cajero1", "Secreta123"),
                RequestMeta.UNKNOWN);

        when(gateway.findActiveRefreshOwner(anyString(), any(Instant.class)))
                .thenReturn(Optional.of(new AuthUserGateway.RefreshOwner(7, "cajero1", 42)));

        assertThat(service.logout(login.refreshRaw(), null)).isTrue();
        verify(gateway).cerrarSesion(1);          // sesion abierta en login (stub)
        verify(gateway).revokeByHash(JwtService.sha256Base64(login.refreshRaw()));
    }

    @Test
    @DisplayName("register: crea empleado + usuario ligado + UNICO rol ENCARGADO_CAJA")
    void register_createsEmpleadoUsuarioYRoleUnico() {
        when(empleados.create(any())).thenReturn(5);
        when(admin.createUsuario(eq("nuevo01"), eq("nuevo01@ejemplo.mx"), anyString(),
                eq(5), anyBoolean())).thenReturn(11);

        RegisterResponse r = service.register(new RegisterRequest(
                "nuevo01", "nuevo01@ejemplo.mx", "Secreta123",
                "Juan", "Pérez", "López", "555", 3));

        assertThat(r.usuarioId()).isEqualTo(11);
        assertThat(r.empleadoId()).isEqualTo(5);
        assertThat(r.username()).isEqualTo("nuevo01");
        // el único rol posible es ENCARGADO_CAJA, nunca ADMINISTRADOR
        verify(empleados).create(new EmpleadoGateway.EmpleadoDatos(3, "Juan", "Pérez", "López",
                null, null, "555", null, "nuevo01@ejemplo.mx", null, null, null, null,
                ZonaHoraria.hoy(), BigDecimal.ZERO, null));
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
                42, "Juan Pérez", "Vendedor", "juan@x.mx", "555", true, null);
        when(empleados.resumenById(42)).thenReturn(Optional.of(resumen));

        var me = service.me(new UserPrincipal(7, "cajero1", 42, List.of("VENDEDOR")));

        assertThat(me.empleado()).isNotNull();
        assertThat(me.empleado().nombreCompleto()).isEqualTo("Juan Pérez");
        assertThat(me.empleado().puestoNombre()).isEqualTo("Vendedor");
    }

    // ── helpers ─────────────────────────────────────────────────────

    private static final String TEST_SECRET = "0123456789abcdef0123456789abcdef";

    /** Refresh JWT firmado con el secret de test, con claims uid/ses opcionales. */
    private String refreshSinClaims(Integer uid, Integer ses) {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        var b = Jwts.builder().subject("7").claim("typ", "ref");
        if (uid != null) {
            b.claim("uid", uid);
        }
        if (ses != null) {
            b.claim("ses", ses);
        }
        return b.issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(key).compact();
    }

    private HttpServletRequest httpConCookie(String nombre, String valor) {
        HttpServletRequest http = mock(HttpServletRequest.class);
        when(http.getCookies()).thenReturn(new Cookie[]{new Cookie(nombre, valor)});
        return http;
    }

    private void mockRefreshOk() {
        when(gateway.findRefreshRow(anyString()))
                .thenReturn(Optional.of(new AuthUserGateway.RefreshRow(7,
                        Instant.now().plusSeconds(3600), null)));
        when(gateway.revokeByHash(anyString())).thenReturn(true);
        when(gateway.findActiveRefreshOwner(anyString(), any(Instant.class)))
                .thenReturn(Optional.of(new AuthUserGateway.RefreshOwner(7, "cajero1", 42)));
    }

    // ── login: bloqueo e intentos ───────────────────────────────────

    @Test
    @DisplayName("login bloqueada: CUENTA_BLOQUEADA sin abrir sesion")
    void login_bloqueada_rejected() {
        var bloqueado = new AuthUserGateway.AuthUser(7, "cajero1",
                encoder.encode("Secreta123"), true, false, 42, 3,
                Instant.now().plusSeconds(600));
        when(gateway.findByUsername("cajero1")).thenReturn(Optional.of(bloqueado));

        assertThatThrownBy(() -> service.login(new LoginRequest("cajero1", "Secreta123"),
                RequestMeta.UNKNOWN))
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CUENTA_BLOQUEADA));
        verify(gateway, never()).abrirSesion(anyInt(), any(), any());
    }

    @Test
    @DisplayName("login con bloqueo ya vencido: entra normal")
    void login_bloqueoVencido_ok() {
        var desbloqueado = new AuthUserGateway.AuthUser(7, "cajero1",
                encoder.encode("Secreta123"), true, false, 42, 3,
                Instant.now().minusSeconds(60));
        when(gateway.findByUsername("cajero1")).thenReturn(Optional.of(desbloqueado));
        when(gateway.rolesOf(7)).thenReturn(List.of("VENDEDOR"));
        when(gateway.abrirSesion(eq(7), any(), any())).thenReturn(1);

        LoginResult r = service.login(new LoginRequest("cajero1", "Secreta123"),
                RequestMeta.UNKNOWN);

        assertThat(r.refreshRaw()).isNotBlank();
    }

    @Test
    @DisplayName("login password mala: incrementa intentos fallidos")
    void login_fallo_incrementaIntentos() {
        mockUserOk();

        assertThatThrownBy(() -> service.login(new LoginRequest("cajero1", "mala"),
                RequestMeta.UNKNOWN))
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CREDENCIALES_INVALIDAS));
        verify(gateway).incrementFailedAttempts(7);
        verify(gateway, never()).resetFailedAttempts(anyInt());
    }

    @Test
    @DisplayName("login usuario inexistente: no incrementa intentos de nadie")
    void login_desconocido_noIncrementa() {
        when(gateway.findByUsername("fantasma")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("fantasma", "x"),
                RequestMeta.UNKNOWN))
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CREDENCIALES_INVALIDAS));
        verify(gateway, never()).incrementFailedAttempts(anyInt());
    }

    @Test
    @DisplayName("login exitoso: resetea intentos fallidos")
    void login_ok_reseteaIntentos() {
        mockUserOk();

        service.login(new LoginRequest("cajero1", "Secreta123"), RequestMeta.UNKNOWN);

        verify(gateway).resetFailedAttempts(7);
        verify(gateway, never()).incrementFailedAttempts(anyInt());
    }

    @Test
    @DisplayName("login con meta null: abre sesion sin ip ni user-agent")
    void login_metaNull_ok() {
        mockUserOk();

        LoginResult r = service.login(new LoginRequest("cajero1", "Secreta123"), null);

        assertThat(r.refreshRaw()).isNotBlank();
        verify(gateway).abrirSesion(eq(7), isNull(), isNull());
    }

    @Test
    @DisplayName("login propaga ip y user-agent a la sesion")
    void login_metaPassthrough() {
        mockUserOk();

        service.login(new LoginRequest("cajero1", "Secreta123"),
                new RequestMeta("1.2.3.4", "agent-x"));

        verify(gateway).abrirSesion(7, "1.2.3.4", "agent-x");
    }

    // ── changePassword: politica y ramas ────────────────────────────

    @Test
    @DisplayName("changePassword sin principal: CREDENCIALES_INVALIDAS")
    void changePassword_sinPrincipal_rejected() {
        assertThatThrownBy(() -> service.changePassword(null,
                new ChangePasswordRequest("Secreta123", "NuevaClave99")))
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CREDENCIALES_INVALIDAS));
        verify(gateway, never()).findByUsername(anyString());
    }

    @Test
    @DisplayName("changePassword nueva corta (<8): VALOR_INVALIDO")
    void changePassword_nuevaCorta_rejected() {
        var up = new UserPrincipal(7, "cajero1", 42, List.of("VENDEDOR"));

        assertThatThrownBy(() -> service.changePassword(up,
                new ChangePasswordRequest("Secreta123", "corta1")))
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.VALOR_INVALIDO));
        verify(admin, never()).actualizarPassword(anyInt(), anyString());
    }

    @Test
    @DisplayName("changePassword nueva sin digito: VALOR_INVALIDO")
    void changePassword_nuevaSinDigito_rejected() {
        var up = new UserPrincipal(7, "cajero1", 42, List.of("VENDEDOR"));

        assertThatThrownBy(() -> service.changePassword(up,
                new ChangePasswordRequest("Secreta123", "SinDigitosAqui")))
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.VALOR_INVALIDO));
        verify(admin, never()).actualizarPassword(anyInt(), anyString());
    }

    @Test
    @DisplayName("changePassword nueva null: VALOR_INVALIDO")
    void changePassword_nuevaNull_rejected() {
        var up = new UserPrincipal(7, "cajero1", 42, List.of("VENDEDOR"));

        assertThatThrownBy(() -> service.changePassword(up,
                new ChangePasswordRequest("Secreta123", null)))
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.VALOR_INVALIDO));
        verify(admin, never()).actualizarPassword(anyInt(), anyString());
    }

    @Test
    @DisplayName("changePassword nueva igual a la actual: VALOR_INVALIDO")
    void changePassword_nuevaIgual_rejected() {
        var up = new UserPrincipal(7, "cajero1", 42, List.of("VENDEDOR"));

        assertThatThrownBy(() -> service.changePassword(up,
                new ChangePasswordRequest("Secreta123", "Secreta123")))
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.VALOR_INVALIDO));
        verify(admin, never()).actualizarPassword(anyInt(), anyString());
    }

    @Test
    @DisplayName("changePassword usuario inactivo: CREDENCIALES_INVALIDAS")
    void changePassword_inactivo_rejected() {
        var inactivo = new AuthUser(9, "baja", encoder.encode("Actual123"),
                false, false, null);
        when(gateway.findByUsername("baja")).thenReturn(Optional.of(inactivo));
        var up = new UserPrincipal(9, "baja", null, List.of());

        assertThatThrownBy(() -> service.changePassword(up,
                new ChangePasswordRequest("Actual123", "NuevaClave99")))
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CREDENCIALES_INVALIDAS));
        verify(admin, never()).actualizarPassword(anyInt(), anyString());
    }

    @Test
    @DisplayName("changePassword exitoso: revoca todos los refresh tokens")
    void changePassword_ok_revocaRefresh() {
        var up = new UserPrincipal(7, "cajero1", 42, List.of("VENDEDOR"));
        when(gateway.findByUsername("cajero1")).thenReturn(Optional.of(activo));

        var ok = service.changePassword(up,
                new ChangePasswordRequest("Secreta123", "NuevaClave99"));

        assertThat(ok.cambiada()).isTrue();
        verify(gateway).revokeAllRefreshTokens(7);
    }

    // ── refresh: ramas de error y cookies ───────────────────────────

    @Test
    @DisplayName("refresh null sin cookie: CREDENCIALES_INVALIDAS")
    void refresh_nulo_rejected() {
        assertThatThrownBy(() -> service.refresh(null, RequestMeta.UNKNOWN, null))
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CREDENCIALES_INVALIDAS));
        verify(gateway, never()).revokeByHash(anyString());
    }

    @Test
    @DisplayName("refresh en blanco: CREDENCIALES_INVALIDAS")
    void refresh_blanco_rejected() {
        assertThatThrownBy(() -> service.refresh("   ", RequestMeta.UNKNOWN, null))
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CREDENCIALES_INVALIDAS));
        verify(gateway, never()).revokeByHash(anyString());
    }

    @Test
    @DisplayName("refresh con firma invalida: TOKEN_EXPIRADO y revoke defensivo")
    void refresh_firmaInvalida_rejected() {
        mockUserOk();
        LoginResult login = service.login(new LoginRequest("cajero1", "Secreta123"),
                RequestMeta.UNKNOWN);
        String adulterado = login.refreshRaw() + "x";

        assertThatThrownBy(() -> service.refresh(adulterado, RequestMeta.UNKNOWN, null))
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.TOKEN_EXPIRADO));
        verify(gateway).revokeByHash(JwtService.sha256Base64(adulterado));
    }

    @Test
    @DisplayName("refresh sin claim uid: CREDENCIALES_INVALIDAS y revoke")
    void refresh_sinUid_rejected() {
        String sinUid = refreshSinClaims(null, 1);

        assertThatThrownBy(() -> service.refresh(sinUid, RequestMeta.UNKNOWN, null))
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CREDENCIALES_INVALIDAS));
        verify(gateway).revokeByHash(JwtService.sha256Base64(sinUid));
        verify(gateway, never()).findRefreshRow(anyString());
    }

    @Test
    @DisplayName("refresh con dueño inactivo: TOKEN_EXPIRADO y revoke")
    void refresh_duenoInactivo_rejected() {
        mockUserOk();
        LoginResult login = service.login(new LoginRequest("cajero1", "Secreta123"),
                RequestMeta.UNKNOWN);
        when(gateway.findRefreshRow(anyString()))
                .thenReturn(Optional.of(new AuthUserGateway.RefreshRow(7,
                        Instant.now().plusSeconds(3600), null)));
        when(gateway.findActiveRefreshOwner(anyString(), any(Instant.class)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refresh(login.refreshRaw(), RequestMeta.UNKNOWN, null))
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.TOKEN_EXPIRADO));
        verify(gateway).revokeByHash(JwtService.sha256Base64(login.refreshRaw()));
        verify(gateway, never()).saveRefreshToken(anyInt(), anyString(), any());
    }

    @Test
    @DisplayName("refresh en carrera (ya revocado al rotar): TOKEN_EXPIRADO sin emitir")
    void refresh_concurrente_rejected() {
        mockUserOk();
        LoginResult login = service.login(new LoginRequest("cajero1", "Secreta123"),
                RequestMeta.UNKNOWN);
        when(gateway.findRefreshRow(anyString()))
                .thenReturn(Optional.of(new AuthUserGateway.RefreshRow(7,
                        Instant.now().plusSeconds(3600), null)));
        when(gateway.findActiveRefreshOwner(anyString(), any(Instant.class)))
                .thenReturn(Optional.of(new AuthUserGateway.RefreshOwner(7, "cajero1", 42)));
        when(gateway.revokeByHash(anyString())).thenReturn(false); // otro request gano

        assertThatThrownBy(() -> service.refresh(login.refreshRaw(), RequestMeta.UNKNOWN, null))
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.TOKEN_EXPIRADO));
        verify(gateway, never()).saveRefreshToken(anyInt(), anyString(), any());
    }

    @Test
    @DisplayName("refresh prefiere la cookie sobre el body")
    void refresh_cookiePrecedeAlBody() {
        mockUserOk();
        LoginResult deCookie = service.login(new LoginRequest("cajero1", "Secreta123"),
                RequestMeta.UNKNOWN);
        LoginResult deBody = service.login(new LoginRequest("cajero1", "Secreta123"),
                RequestMeta.UNKNOWN);
        mockRefreshOk();

        LoginResult r2 = service.refresh(deBody.refreshRaw(), RequestMeta.UNKNOWN,
                httpConCookie("rt", deCookie.refreshRaw()));

        assertThat(r2.refreshRaw()).isNotBlank();
        verify(gateway).revokeByHash(JwtService.sha256Base64(deCookie.refreshRaw()));
        verify(gateway, never())
                .revokeByHash(JwtService.sha256Base64(deBody.refreshRaw()));
    }

    @Test
    @DisplayName("refresh con cookie vacia: usa el body")
    void refresh_cookieVaciaUsaBody() {
        mockUserOk();
        LoginResult login = service.login(new LoginRequest("cajero1", "Secreta123"),
                RequestMeta.UNKNOWN);
        mockRefreshOk();

        LoginResult r2 = service.refresh(login.refreshRaw(), RequestMeta.UNKNOWN,
                httpConCookie("rt", "   "));

        assertThat(r2.refreshRaw()).isNotEqualTo(login.refreshRaw());
        verify(gateway).revokeByHash(JwtService.sha256Base64(login.refreshRaw()));
    }

    @Test
    @DisplayName("refresh con cookie de otro nombre: usa el body")
    void refresh_cookieOtroNombreUsaBody() {
        mockUserOk();
        LoginResult login = service.login(new LoginRequest("cajero1", "Secreta123"),
                RequestMeta.UNKNOWN);
        mockRefreshOk();

        LoginResult r2 = service.refresh(login.refreshRaw(), RequestMeta.UNKNOWN,
                httpConCookie("otra", "ignorar"));

        assertThat(r2.refreshRaw()).isNotEqualTo(login.refreshRaw());
    }

    @Test
    @DisplayName("refresh sin claim ses: rota con sesion 0")
    void refresh_sinSes_rotaConCero() {
        mockUserOk();
        mockRefreshOk();
        String sinSes = refreshSinClaims(7, null);

        LoginResult r = service.refresh(sinSes, RequestMeta.UNKNOWN, null);

        assertThat(r.refreshRaw()).isNotBlank();
        int sesNuevo = jwtService.parseRefresh(r.refreshRaw())
                .get(JwtService.CLAIM_SES, Integer.class);
        assertThat(sesNuevo).isZero();
    }

    // ── logout: ramas ───────────────────────────────────────────────

    @Test
    @DisplayName("logout sin token: true sin tocar la BD")
    void logout_sinToken_ok() {
        assertThat(service.logout(null, null)).isTrue();
        verifyNoInteractions(gateway);
    }

    @Test
    @DisplayName("logout con body en blanco: true sin tocar la BD")
    void logout_blanco_ok() {
        assertThat(service.logout("  ", null)).isTrue();
        verifyNoInteractions(gateway);
    }

    @Test
    @DisplayName("logout con token inservible: revoca el hash sin cerrar sesion")
    void logout_tokenInservible_revocaSinCerrar() {
        assertThat(service.logout("basura", null)).isTrue();
        verify(gateway).revokeByHash(JwtService.sha256Base64("basura"));
        verify(gateway, never()).cerrarSesion(anyInt());
    }

    @Test
    @DisplayName("logout prefiere la cookie sobre el body")
    void logout_cookiePrecedeAlBody() {
        mockUserOk();
        LoginResult login = service.login(new LoginRequest("cajero1", "Secreta123"),
                RequestMeta.UNKNOWN);

        assertThat(service.logout("otro-cuerpo",
                httpConCookie("rt", login.refreshRaw()))).isTrue();
        verify(gateway).cerrarSesion(1);
        verify(gateway).revokeByHash(JwtService.sha256Base64(login.refreshRaw()));
        verify(gateway, never()).revokeByHash(JwtService.sha256Base64("otro-cuerpo"));
    }

    // ── cookies ─────────────────────────────────────────────────────

    @Test
    @DisplayName("buildRefreshCookie: HttpOnly con path de auth y maxAge del refresh")
    void buildRefreshCookie_props() {
        var c = service.buildRefreshCookie("RAW");

        assertThat(c.getName()).isEqualTo("rt");
        assertThat(c.getValue()).isEqualTo("RAW");
        assertThat(c.isHttpOnly()).isTrue();
        assertThat(c.getPath()).isEqualTo("/api/v1/auth");
        assertThat(c.getMaxAge()).isEqualTo(Duration.ofHours(8));
    }

    @Test
    @DisplayName("clearRefreshCookie: vacia y expira inmediato")
    void clearRefreshCookie_props() {
        var c = service.clearRefreshCookie();

        assertThat(c.getName()).isEqualTo("rt");
        assertThat(c.getValue()).isEmpty();
        assertThat(c.isHttpOnly()).isTrue();
        assertThat(c.getMaxAge()).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("buildAccessCookie: HttpOnly con path raiz y maxAge del access")
    void buildAccessCookie_props() {
        var c = service.buildAccessCookie("ACC");

        assertThat(c.getName()).isEqualTo("at");
        assertThat(c.getValue()).isEqualTo("ACC");
        assertThat(c.isHttpOnly()).isTrue();
        assertThat(c.getPath()).isEqualTo("/");
        assertThat(c.getMaxAge()).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("clearAccessCookie: vacia y expira inmediato")
    void clearAccessCookie_props() {
        var c = service.clearAccessCookie();

        assertThat(c.getName()).isEqualTo("at");
        assertThat(c.getValue()).isEmpty();
        assertThat(c.isHttpOnly()).isTrue();
        assertThat(c.getMaxAge()).isEqualTo(Duration.ZERO);
    }

    // ── me: ramas ───────────────────────────────────────────────────

    @Test
    @DisplayName("me sin empleado vinculado: empleado null")
    void me_sinEmpleado() {
        when(gateway.findByUsername("cajero1")).thenReturn(Optional.of(activo));

        var me = service.me(new UserPrincipal(7, "cajero1", null, List.of("VENDEDOR")));

        assertThat(me.empleado()).isNull();
        assertThat(me.debeCambiarPassword()).isFalse();
    }

    @Test
    @DisplayName("me con resumen ausente: empleado null")
    void me_resumenAusente() {
        when(gateway.findByUsername("cajero1")).thenReturn(Optional.of(activo));
        when(empleados.resumenById(42)).thenReturn(Optional.empty());

        var me = service.me(new UserPrincipal(7, "cajero1", 42, List.of("VENDEDOR")));

        assertThat(me.empleado()).isNull();
    }

    @Test
    @DisplayName("me propaga debeCambiarPassword desde BD")
    void me_debeCambiar() {
        var pendiente = new AuthUser(7, "cajero1", activo.passwordHash(),
                true, true, 42);
        when(gateway.findByUsername("cajero1")).thenReturn(Optional.of(pendiente));

        var me = service.me(new UserPrincipal(7, "cajero1", 42, List.of("VENDEDOR")));

        assertThat(me.debeCambiarPassword()).isTrue();
    }

    @Test
    @DisplayName("me con usuario desconocido: debeCambiar false")
    void me_desconocido() {
        when(gateway.findByUsername("fantasma")).thenReturn(Optional.empty());

        var me = service.me(new UserPrincipal(99, "fantasma", null, List.of()));

        assertThat(me.debeCambiarPassword()).isFalse();
        assertThat(me.empleado()).isNull();
    }
}
