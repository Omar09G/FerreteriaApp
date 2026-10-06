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
import mx.ferreteria.api.notif.dto.TurnoAbiertoDtos.TurnoAbiertoEnvioResponse;
import mx.ferreteria.api.notif.dto.TurnoAbiertoDtos.TurnoAbiertoEstadoResponse;
import mx.ferreteria.api.notif.service.TurnoAbiertoInformeService;

@WebMvcTest(controllers = TurnoAbiertoInformeController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ DbErrorTranslator.class, WebMvcTestProps.class, TurnoAbiertoInformeControllerTest.SliceConfig.class })
@MockitoBean(types = { mx.ferreteria.api.common.security.JwtAuthFilter.class,
                mx.ferreteria.api.common.security.RestAuthEntryPoint.class,
                mx.ferreteria.api.common.security.JwtService.class })
class TurnoAbiertoInformeControllerTest {

        @Autowired
        MockMvc mvc;

        @MockitoBean
        TurnoAbiertoInformeService turnoService;

        @org.springframework.boot.test.context.TestConfiguration
        static class SliceConfig {
                @org.springframework.context.annotation.Bean
                mx.ferreteria.api.common.web.RequestIdProperties requestIdProperties() {
                        return new mx.ferreteria.api.common.web.RequestIdProperties(
                                        mx.ferreteria.api.common.web.RequestIdProperties.Mode.GENERATE);
                }
        }

        @Test
        @DisplayName("POST /api/v1/reportes/turnos/informe -> 200 con conteos")
        void enviar_returns200() throws Exception {
                var r = new TurnoAbiertoEnvioResponse(LocalDate.of(2026, 10, 5), 2, 2, 1, 3);
                when(turnoService.enviar()).thenReturn(r);

                mvc.perform(post("/api/v1/reportes/turnos/informe"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.destinatarios").value(2))
                                .andExpect(jsonPath("$.data.turnos").value(3));
        }

        @Test
        @DisplayName("GET /api/v1/reportes/turnos/informe/estado -> 200 con yaEnviado")
        void estado_returns200() throws Exception {
                var r = new TurnoAbiertoEstadoResponse(LocalDate.of(2026, 10, 5), false,
                                null, null);
                when(turnoService.estado()).thenReturn(r);

                mvc.perform(get("/api/v1/reportes/turnos/informe/estado"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.yaEnviado").value(false));
        }
}
