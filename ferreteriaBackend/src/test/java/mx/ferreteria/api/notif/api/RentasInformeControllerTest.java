package mx.ferreteria.api.notif.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import mx.ferreteria.api.notif.dto.RentasDtos.RentasEnvioResponse;
import mx.ferreteria.api.notif.dto.RentasDtos.RentasEstadoResponse;
import mx.ferreteria.api.notif.service.RentasInformeService;

@WebMvcTest(controllers = RentasInformeController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ DbErrorTranslator.class, WebMvcTestProps.class, RentasInformeControllerTest.SliceConfig.class })
@MockitoBean(types = { mx.ferreteria.api.common.security.JwtAuthFilter.class,
                mx.ferreteria.api.common.security.RestAuthEntryPoint.class,
                mx.ferreteria.api.common.security.JwtService.class })
class RentasInformeControllerTest {

        @Autowired
        MockMvc mvc;

        @MockitoBean
        RentasInformeService rentasService;

        @org.springframework.boot.test.context.TestConfiguration
        static class SliceConfig {
                @org.springframework.context.annotation.Bean
                mx.ferreteria.api.common.web.RequestIdProperties requestIdProperties() {
                        return new mx.ferreteria.api.common.web.RequestIdProperties(
                                        mx.ferreteria.api.common.web.RequestIdProperties.Mode.GENERATE);
                }
        }

        @Test
        @DisplayName("POST /api/v1/reportes/rentas/informe -> 200 con conteos")
        void enviar_returns200() throws Exception {
                var r = new RentasEnvioResponse(LocalDate.of(2026, 10, 5), 2, 2, 1, 1, 1);
                when(rentasService.enviar()).thenReturn(r);

                mvc.perform(post("/api/v1/reportes/rentas/informe"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.destinatarios").value(2))
                                .andExpect(jsonPath("$.data.vencidas").value(1))
                                .andExpect(jsonPath("$.data.proximas").value(1));
        }

        @Test
        @DisplayName("GET /api/v1/reportes/rentas/informe/estado -> 200 con yaEnviado")
        void estado_returns200() throws Exception {
                var r = new RentasEstadoResponse(LocalDate.of(2026, 10, 5), true,
                                "ENVIADA", java.time.Instant.now());
                when(rentasService.estado()).thenReturn(r);

                mvc.perform(get("/api/v1/reportes/rentas/informe/estado"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.yaEnviado").value(true));
        }
}
