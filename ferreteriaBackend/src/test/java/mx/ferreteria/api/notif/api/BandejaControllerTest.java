package mx.ferreteria.api.notif.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import mx.ferreteria.api.common.error.DbErrorTranslator;
import mx.ferreteria.api.common.security.AuthCookieProperties;
import mx.ferreteria.api.common.security.JwtService;
import mx.ferreteria.api.common.web.WebMvcTestProps;
import mx.ferreteria.api.notif.dto.BandejaDtos.BandejaResponse;
import mx.ferreteria.api.notif.dto.BandejaDtos.NoLeidasResponse;
import mx.ferreteria.api.notif.service.BandejaService;
import mx.ferreteria.api.notif.service.RealtimePushService;

@WebMvcTest(controllers = BandejaController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ DbErrorTranslator.class, WebMvcTestProps.class, BandejaControllerTest.SliceConfig.class })
@MockitoBean(types = { mx.ferreteria.api.common.security.JwtAuthFilter.class,
                mx.ferreteria.api.common.security.RestAuthEntryPoint.class })
class BandejaControllerTest {

        @Autowired
        MockMvc mvc;

        @MockitoBean
        BandejaService bandejaService;

        @MockitoBean
        RealtimePushService pushService;

        @MockitoBean
        JwtService jwtService;

        @MockitoBean
        AuthCookieProperties cookieProps;

        @org.springframework.boot.test.context.TestConfiguration
        static class SliceConfig {
                @org.springframework.context.annotation.Bean
                mx.ferreteria.api.common.web.RequestIdProperties requestIdProperties() {
                        return new mx.ferreteria.api.common.web.RequestIdProperties(
                                        mx.ferreteria.api.common.web.RequestIdProperties.Mode.GENERATE);
                }
        }

        private BandejaResponse fila() {
                return new BandejaResponse(1L, "VENTA_TICKET", "Venta V-1 registrada",
                                "Total $100.00", "VENTA", 5L, null, Instant.now());
        }

        @Test
        @DisplayName("GET /api/v1/notificaciones -> 200 con items y meta")
        void listar_returns200() throws Exception {
                when(bandejaService.listar(org.mockito.ArgumentMatchers.anyInt(),
                                org.mockito.ArgumentMatchers.any()))
                                .thenReturn(new PageImpl<>(List.of(fila()),
                                                PageRequest.of(0, 20), 1));

                mvc.perform(get("/api/v1/notificaciones"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data[0].tipo").value("VENTA_TICKET"))
                                .andExpect(jsonPath("$.meta.totalElements").value(1));
        }

        @Test
        @DisplayName("GET /api/v1/notificaciones/no-leidas -> 200 con conteo")
        void noLeidas_returns200() throws Exception {
                when(bandejaService.noLeidas(org.mockito.ArgumentMatchers.anyInt()))
                                .thenReturn(new NoLeidasResponse(3));

                mvc.perform(get("/api/v1/notificaciones/no-leidas"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.noLeidas").value(3));
        }

        @Test
        @DisplayName("PATCH /api/v1/notificaciones/{id}/leida -> 200")
        void marcarLeida_returns200() throws Exception {
                when(bandejaService.marcarLeida(org.mockito.ArgumentMatchers.anyInt(),
                                org.mockito.ArgumentMatchers.eq(1L)))
                                .thenReturn(fila());

                mvc.perform(patch("/api/v1/notificaciones/1/leida"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.bandejaId").value(1));
        }

        @Test
        @DisplayName("PATCH /api/v1/notificaciones/leidas -> 200 con cero")
        void marcarTodas_returns200() throws Exception {
                mvc.perform(patch("/api/v1/notificaciones/leidas"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.noLeidas").value(0));
        }

        @Test
        @DisplayName("DELETE /api/v1/notificaciones/leidas -> 200 con eliminadas")
        void eliminarLeidas_returns200() throws Exception {
                when(bandejaService.eliminarLeidas(org.mockito.ArgumentMatchers.anyInt()))
                                .thenReturn(4L);

                mvc.perform(delete("/api/v1/notificaciones/leidas"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.eliminadas").value(4));
        }

        @Test
        @DisplayName("GET /stream con cookie rt liga la suscripción a la sesión del refresh")
        void stream_conCookie_suscribeConSesion() throws Exception {
                when(cookieProps.refreshName()).thenReturn("rt");
                Claims claims = org.mockito.Mockito.mock(Claims.class);
                when(claims.get(JwtService.CLAIM_SES, Integer.class)).thenReturn(7);
                when(jwtService.parseRefresh("ref.jwt")).thenReturn(claims);
                when(pushService.suscribir(org.mockito.ArgumentMatchers.anyInt(),
                                org.mockito.ArgumentMatchers.any()))
                                                .thenReturn(new SseEmitter(30_000L));

                mvc.perform(get("/api/v1/notificaciones/stream")
                                .cookie(new Cookie("rt", "ref.jwt")))
                                                .andExpect(request().asyncStarted());
                verify(pushService).suscribir(org.mockito.ArgumentMatchers.anyInt(),
                                org.mockito.ArgumentMatchers.eq(7));
        }

        @Test
        @DisplayName("GET /stream sin cookie suscribe sin sesión (recibe todo)")
        void stream_sinCookie_suscribeSinSesion() throws Exception {
                when(cookieProps.refreshName()).thenReturn("rt");
                when(pushService.suscribir(org.mockito.ArgumentMatchers.anyInt(),
                                org.mockito.ArgumentMatchers.any()))
                                                .thenReturn(new SseEmitter(30_000L));

                mvc.perform(get("/api/v1/notificaciones/stream"))
                                                .andExpect(request().asyncStarted());
                verify(pushService).suscribir(org.mockito.ArgumentMatchers.anyInt(),
                                org.mockito.ArgumentMatchers.isNull());
        }
}
