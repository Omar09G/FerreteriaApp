package mx.ferreteria.api.seg.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import mx.ferreteria.api.seg.dto.AuthDtos.LoginRequest;
import mx.ferreteria.api.seg.dto.AuthDtos.MeResponse;
import mx.ferreteria.api.seg.dto.AuthDtos.TokenResponse;
import mx.ferreteria.api.seg.service.AuthService;
import mx.ferreteria.api.seg.service.RequestMeta;

/** Contrato HTTP del endpoint de login (sin cadena de seguridad: eso lo cubren los IT). */
@WebMvcTest(controllers = AuthController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({mx.ferreteria.api.common.error.DbErrorTranslator.class,
        AuthControllerTest.SliceConfig.class})
@MockBean({mx.ferreteria.api.common.security.JwtAuthFilter.class,
           mx.ferreteria.api.common.security.RestAuthEntryPoint.class,
           mx.ferreteria.api.common.security.JwtService.class})
class AuthControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    AuthService authService;

    @org.springframework.boot.test.context.TestConfiguration
    static class SliceConfig {
        @org.springframework.context.annotation.Bean
        mx.ferreteria.api.common.web.RequestIdProperties requestIdProperties() {
            return new mx.ferreteria.api.common.web.RequestIdProperties(
                    mx.ferreteria.api.common.web.RequestIdProperties.Mode.GENERATE);
        }
    }

    private static final MeResponse ME =
            new MeResponse(7, "cajero1", 42, List.of("VENDEDOR"), null);

    @Test
    @DisplayName("POST /auth/login valido -> 200 success:true con tokens en data")
    void login_valid_returnsTokens() throws Exception {
        Mockito.when(authService.login(any(LoginRequest.class), any(RequestMeta.class)))
                .thenReturn(new TokenResponse("acc.jwt", "ref.jwt", 28800, ME));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cajero1\",\"password\":\"Secreta123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("acc.jwt"))
                .andExpect(jsonPath("$.data.refreshToken").value("ref.jwt"))
                .andExpect(jsonPath("$.data.usuario.roles[0]").value("VENDEDOR"));
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
    @DisplayName("POST /auth/refresh valido -> 200 success:true con nuevo par")
    void refresh_valid_returnsNewPair() throws Exception {
        Mockito.when(authService.refresh(eq("ref.jwt"), any(RequestMeta.class)))
                .thenReturn(new TokenResponse("acc2", "ref2", 28800, ME));

        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"ref.jwt\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("acc2"))
                .andExpect(jsonPath("$.data.refreshToken").value("ref2"));
    }

    @Test
    @DisplayName("POST /auth/logout -> 200 success:true con revocado en data")
    void logout_ok() throws Exception {
        Mockito.when(authService.logout("ref.jwt")).thenReturn(true);

        mvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"ref.jwt\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.revocado").value(true));
    }

    @Test
    @DisplayName("POST /auth/refresh sin refreshToken -> 400 success:false CAMPO_REQUERIDO")
    void refresh_missingField_badRequest() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value(400))
                .andExpect(jsonPath("$.codigo").value("CAMPO_REQUERIDO"));
    }
}
