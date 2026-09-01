package mx.ferreteria.api.seg.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import mx.ferreteria.api.seg.dto.AuthDtos.ChangePasswordRequest;
import mx.ferreteria.api.seg.dto.AuthDtos.LoginRequest;
import mx.ferreteria.api.seg.dto.AuthDtos.MeResponse;
import mx.ferreteria.api.seg.dto.AuthDtos.PasswordOk;
import mx.ferreteria.api.seg.dto.AuthDtos.RegisterRequest;
import mx.ferreteria.api.seg.dto.AuthDtos.RegisterResponse;
import mx.ferreteria.api.seg.dto.AuthDtos.TokenResponse;
import mx.ferreteria.api.seg.service.AuthService;
import mx.ferreteria.api.seg.service.AuthService.LoginResult;
import mx.ferreteria.api.seg.service.RequestMeta;

/** Contrato HTTP del endpoint de login (sin cadena de seguridad: eso lo cubren los IT). */
@WebMvcTest(controllers = AuthController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({mx.ferreteria.api.common.error.DbErrorTranslator.class,
        mx.ferreteria.api.common.web.WebMvcTestProps.class,
        AuthControllerTest.SliceConfig.class})
@MockBean({mx.ferreteria.api.common.security.JwtAuthFilter.class,
           mx.ferreteria.api.common.security.RestAuthEntryPoint.class,
           mx.ferreteria.api.common.security.JwtService.class})
class AuthControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    AuthService authService;

    @MockBean
    mx.ferreteria.api.common.security.AuthCookieProperties cookieProperties;

    @org.springframework.boot.test.context.TestConfiguration
    static class SliceConfig {
        @org.springframework.context.annotation.Bean
        mx.ferreteria.api.common.web.RequestIdProperties requestIdProperties() {
            return new mx.ferreteria.api.common.web.RequestIdProperties(
                    mx.ferreteria.api.common.web.RequestIdProperties.Mode.GENERATE);
        }
    }

    private static final MeResponse ME =
            new MeResponse(7, "cajero1", 42, List.of("VENDEDOR"), null, null);

    @Test
    @DisplayName("POST /auth/login valido -> 200 con accessToken en body y Set-Cookie rt HttpOnly")
    void login_valid_returnsTokens() throws Exception {
        Mockito.when(authService.login(any(LoginRequest.class), any(RequestMeta.class)))
                .thenReturn(new LoginResult(
                        new TokenResponse("acc.jwt", null, 28800, ME),
                        "ref.jwt"));
        Mockito.when(authService.buildRefreshCookie("ref.jwt"))
                .thenReturn(org.springframework.http.ResponseCookie.from("rt", "ref.jwt")
                        .httpOnly(true).secure(false).path("/api/v1/auth")
                        .maxAge(java.time.Duration.ofHours(8)).sameSite("Lax").build());
        Mockito.when(authService.buildAccessCookie("acc.jwt"))
                .thenReturn(org.springframework.http.ResponseCookie.from("at", "acc.jwt")
                        .httpOnly(true).secure(false).path("/")
                        .maxAge(java.time.Duration.ofHours(8)).sameSite("Lax").build());

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cajero1\",\"password\":\"Secreta123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("acc.jwt"))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.data.usuario.roles[0]").value("VENDEDOR"))
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(cookie().httpOnly("rt", true))
                .andExpect(cookie().path("rt", "/api/v1/auth"))
                .andExpect(cookie().httpOnly("at", true))
                .andExpect(cookie().path("at", "/"));
    }

    @Test
    @DisplayName("POST /auth/login con campos vacios -> 400 success:false CAMPO_REQUERIDO")
    void login_blankFields_rejectedByValidation() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value(400))
                .andExpect(jsonPath("$.codigo").value("CAMPO_REQUERIDO"))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    @DisplayName("GET /auth/me con principal -> 200 success:true data con perfil")
    void me_withPrincipal_returnsProfile() throws Exception {
        var up = new mx.ferreteria.api.common.security.UserPrincipal(7, "cajero1", 42,
                List.of("VENDEDOR"));
        Mockito.when(authService.me(up)).thenReturn(ME);

        mvc.perform(get("/api/v1/auth/me").principal(up))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("cajero1"))
                .andExpect(jsonPath("$.data.roles[0]").value("VENDEDOR"));
    }

    @Test
    @DisplayName("POST /auth/refresh valido -> 200 con nuevo accessToken y Set-Cookie rotado")
    void refresh_valid_returnsNewPair() throws Exception {
        Mockito.when(authService.refresh(eq("ref.jwt"), any(RequestMeta.class),
                        any(jakarta.servlet.http.HttpServletRequest.class)))
                .thenReturn(new LoginResult(
                        new TokenResponse("acc2", null, 28800, ME),
                        "ref2"));
        Mockito.when(authService.buildRefreshCookie("ref2"))
                .thenReturn(org.springframework.http.ResponseCookie.from("rt", "ref2")
                        .httpOnly(true).secure(false).path("/api/v1/auth")
                        .maxAge(java.time.Duration.ofHours(8)).sameSite("Lax").build());
        Mockito.when(authService.buildAccessCookie("acc2"))
                .thenReturn(org.springframework.http.ResponseCookie.from("at", "acc2")
                        .httpOnly(true).secure(false).path("/")
                        .maxAge(java.time.Duration.ofHours(8)).sameSite("Lax").build());

        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"ref.jwt\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("acc2"))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(header().exists("Set-Cookie"));
    }

    @Test
    @DisplayName("POST /auth/logout -> 200 con revocado:true y Set-Cookie de limpieza")
    void logout_ok() throws Exception {
        Mockito.when(authService.logout(eq("ref.jwt"),
                        any(jakarta.servlet.http.HttpServletRequest.class)))
                .thenReturn(true);
        Mockito.when(authService.clearRefreshCookie())
                .thenReturn(org.springframework.http.ResponseCookie.from("rt", "")
                        .httpOnly(true).secure(false).path("/api/v1/auth")
                        .maxAge(java.time.Duration.ZERO).sameSite("Lax").build());
        Mockito.when(authService.clearAccessCookie())
                .thenReturn(org.springframework.http.ResponseCookie.from("at", "")
                        .httpOnly(true).secure(false).path("/")
                        .maxAge(java.time.Duration.ZERO).sameSite("Lax").build());

        mvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"ref.jwt\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.revocado").value(true))
                .andExpect(header().exists("Set-Cookie"));
    }

    @Test
    @DisplayName("POST /auth/refresh sin body ni cookie -> 401 CREDENCIALES_INVALIDAS")
    void refresh_missingRefresh_rejectedByService() throws Exception {
        Mockito.when(authService.refresh(eq(null), any(RequestMeta.class),
                        any(jakarta.servlet.http.HttpServletRequest.class)))
                .thenThrow(new mx.ferreteria.api.common.error.ValidacionException(
                        mx.ferreteria.api.common.i18n.ErrorCode.CREDENCIALES_INVALIDAS));

        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /auth/register valido -> 200 success:true empleado+usuario+rol ENCARGADO_CAJA")
    void register_valid_createsEmpleadoYUsuario() throws Exception {
        Mockito.when(authService.register(any(RegisterRequest.class)))
                .thenReturn(new RegisterResponse(9, 5, "nuevo01", "nuevo01@ejemplo.mx"));

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nuevo01\",\"email\":\"nuevo01@ejemplo.mx\","
                                + "\"password\":\"Secreta123\",\"nombre\":\"Juan\","
                                + "\"apellidoPaterno\":\"Pérez\",\"puestoId\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.usuarioId").value(9))
                .andExpect(jsonPath("$.data.empleadoId").value(5))
                .andExpect(jsonPath("$.data.email").value("nuevo01@ejemplo.mx"));
    }

    @Test
    @DisplayName("POST /auth/register sin datos de empleado -> 400 CAMPO_REQUERIDO")
    void register_withoutEmpleadoData_badRequest() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nuevo01\",\"email\":\"nuevo01@ejemplo.mx\","
                                + "\"password\":\"Secreta123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.codigo").value("CAMPO_REQUERIDO"));
    }

    @Test
    @DisplayName("POST /auth/register con password corta -> 400 CAMPO_REQUERIDO")
    void register_shortPassword_rejectedByValidation() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nuevo01\",\"email\":\"nuevo01@ejemplo.mx\","
                                + "\"password\":\"1234\",\"nombre\":\"Juan\","
                                + "\"apellidoPaterno\":\"Pérez\",\"puestoId\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.codigo").value("CAMPO_REQUERIDO"));
    }

    @Test
    @DisplayName("POST /auth/change-password autenticado -> 200 success:true cambiada")
    void changePassword_validPrincipal_ok() throws Exception {
        var up = new mx.ferreteria.api.common.security.UserPrincipal(7, "cajero1", 42,
                List.of("VENDEDOR"));
        Mockito.when(authService.changePassword(eq(up), any(ChangePasswordRequest.class)))
                .thenReturn(new PasswordOk(true));

        mvc.perform(post("/api/v1/auth/change-password").principal(up)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"passwordActual\":\"Secreta123\","
                                + "\"nuevaPassword\":\"NuevaClave99\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.cambiada").value(true));
    }

    @Test
    @DisplayName("POST /auth/change-password con campos vacios -> 400 CAMPO_REQUERIDO")
    void changePassword_blankFields_badRequest() throws Exception {
        var up = new mx.ferreteria.api.common.security.UserPrincipal(7, "cajero1", 42,
                List.of("VENDEDOR"));
        mvc.perform(post("/api/v1/auth/change-password").principal(up)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CAMPO_REQUERIDO"));
    }
}
