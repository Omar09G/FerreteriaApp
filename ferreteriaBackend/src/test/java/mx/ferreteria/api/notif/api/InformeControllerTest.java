package mx.ferreteria.api.notif.api;

import static org.mockito.ArgumentMatchers.any;
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
import mx.ferreteria.api.notif.dto.InformeDtos.InformeEnvioResponse;
import mx.ferreteria.api.notif.dto.InformeDtos.InformeEstadoResponse;
import mx.ferreteria.api.notif.service.DashboardInformeService;

@WebMvcTest(controllers = InformeController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ DbErrorTranslator.class, WebMvcTestProps.class, InformeControllerTest.SliceConfig.class })
@MockitoBean(types = { mx.ferreteria.api.common.security.JwtAuthFilter.class,
                mx.ferreteria.api.common.security.RestAuthEntryPoint.class,
                mx.ferreteria.api.common.security.JwtService.class })
class InformeControllerTest {

        @Autowired
        MockMvc mvc;

        @MockitoBean
        DashboardInformeService informeService;

        @org.springframework.boot.test.context.TestConfiguration
        static class SliceConfig {
                @org.springframework.context.annotation.Bean
                mx.ferreteria.api.common.web.RequestIdProperties requestIdProperties() {
                        return new mx.ferreteria.api.common.web.RequestIdProperties(
                                        mx.ferreteria.api.common.web.RequestIdProperties.Mode.GENERATE);
                }
        }

        @Test
        @DisplayName("POST /api/v1/reportes/dashboard/informe -> 200 con conteos")
        void enviar_returns200() throws Exception {
                InformeEnvioResponse r1 = new InformeEnvioResponse(
                                LocalDate.now(), LocalDate.now(), 3, 2, 1);
                when(informeService.enviarInforme(any(), any())).thenReturn(r1);

                mvc.perform(post("/api/v1/reportes/dashboard/informe"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.destinatarios").value(3))
                                .andExpect(jsonPath("$.data.emailsEnviados").value(2))
                                .andExpect(jsonPath("$.data.whatsappEnviados").value(1));
        }

        @Test
        @DisplayName("POST /api/v1/reportes/dashboard/informe con rango invertido -> 400 VALOR_INVALIDO")
        void enviar_rangoInvalido_400() throws Exception {
                mvc.perform(post("/api/v1/reportes/dashboard/informe")
                                .param("fechaInicio", "2026-02-01")
                                .param("fechaFin", "2026-01-31"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.success").value(false))
                                .andExpect(jsonPath("$.codigo").value("VALOR_INVALIDO"));
        }

        @Test
        @DisplayName("GET /api/v1/reportes/dashboard/informe/estado -> 200 con yaEnviado")
        void estado_returns200() throws Exception {
                InformeEstadoResponse r1 = new InformeEstadoResponse(
                                LocalDate.now(), LocalDate.now(), true, "ENVIADA",
                                java.time.Instant.now());
                when(informeService.estadoInforme(any(), any())).thenReturn(r1);

                mvc.perform(get("/api/v1/reportes/dashboard/informe/estado"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.yaEnviado").value(true))
                                .andExpect(jsonPath("$.data.estado").value("ENVIADA"));
        }
}
