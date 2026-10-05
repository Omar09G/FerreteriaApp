package mx.ferreteria.api.notif.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import mx.ferreteria.api.common.error.DbErrorTranslator;
import mx.ferreteria.api.common.web.WebMvcTestProps;
import mx.ferreteria.api.notif.dto.CobranzaDtos.CobranzaEnvioResponse;
import mx.ferreteria.api.notif.dto.CobranzaDtos.CobranzaEstadoResponse;
import mx.ferreteria.api.notif.service.CobranzaInformeService;

@WebMvcTest(controllers = CobranzaInformeController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ DbErrorTranslator.class, WebMvcTestProps.class, CobranzaInformeControllerTest.SliceConfig.class })
@MockitoBean(types = { mx.ferreteria.api.common.security.JwtAuthFilter.class,
                mx.ferreteria.api.common.security.RestAuthEntryPoint.class,
                mx.ferreteria.api.common.security.JwtService.class })
class CobranzaInformeControllerTest {

        @Autowired
        MockMvc mvc;

        @MockitoBean
        CobranzaInformeService cobranzaService;

        @org.springframework.boot.test.context.TestConfiguration
        static class SliceConfig {
                @org.springframework.context.annotation.Bean
                mx.ferreteria.api.common.web.RequestIdProperties requestIdProperties() {
                        return new mx.ferreteria.api.common.web.RequestIdProperties(
                                        mx.ferreteria.api.common.web.RequestIdProperties.Mode.GENERATE);
                }
        }

        @Test
        @DisplayName("POST /api/v1/reportes/cobranza/informe -> 200 con conteos")
        void enviar_returns200() throws Exception {
                var r = new CobranzaEnvioResponse(LocalDate.of(2026, 10, 5), 2, 2, 1,
                                1, 1, new BigDecimal("500.00"), new BigDecimal("300.00"));
                when(cobranzaService.enviar()).thenReturn(r);

                mvc.perform(post("/api/v1/reportes/cobranza/informe"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.destinatarios").value(2))
                                .andExpect(jsonPath("$.data.vencidas").value(1))
                                .andExpect(jsonPath("$.data.pendientes").value(1));
        }

        @Test
        @DisplayName("GET /api/v1/reportes/cobranza/informe/estado -> 200 con yaEnviado")
        void estado_returns200() throws Exception {
                var r = new CobranzaEstadoResponse(LocalDate.of(2026, 10, 5), false,
                                null, null);
                when(cobranzaService.estado()).thenReturn(r);

                mvc.perform(get("/api/v1/reportes/cobranza/informe/estado"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.yaEnviado").value(false));
        }
}
