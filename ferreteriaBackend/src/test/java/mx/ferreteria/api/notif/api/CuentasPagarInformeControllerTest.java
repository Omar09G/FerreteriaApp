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
import mx.ferreteria.api.notif.dto.CuentasPagarDtos.CuentasPagarEnvioResponse;
import mx.ferreteria.api.notif.dto.CuentasPagarDtos.CuentasPagarEstadoResponse;
import mx.ferreteria.api.notif.service.CuentasPagarInformeService;

@WebMvcTest(controllers = CuentasPagarInformeController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ DbErrorTranslator.class, WebMvcTestProps.class, CuentasPagarInformeControllerTest.SliceConfig.class })
@MockitoBean(types = { mx.ferreteria.api.common.security.JwtAuthFilter.class,
                mx.ferreteria.api.common.security.RestAuthEntryPoint.class,
                mx.ferreteria.api.common.security.JwtService.class })
class CuentasPagarInformeControllerTest {

        @Autowired
        MockMvc mvc;

        @MockitoBean
        CuentasPagarInformeService cuentasService;

        @org.springframework.boot.test.context.TestConfiguration
        static class SliceConfig {
                @org.springframework.context.annotation.Bean
                mx.ferreteria.api.common.web.RequestIdProperties requestIdProperties() {
                        return new mx.ferreteria.api.common.web.RequestIdProperties(
                                        mx.ferreteria.api.common.web.RequestIdProperties.Mode.GENERATE);
                }
        }

        @Test
        @DisplayName("POST /api/v1/reportes/cuentas-pagar/informe -> 200 con conteos y totales")
        void enviar_returns200() throws Exception {
                var r = new CuentasPagarEnvioResponse(LocalDate.of(2026, 10, 5), 2, 2,
                                1, 1, new BigDecimal("500.00"), new BigDecimal("300.00"));
                when(cuentasService.enviar()).thenReturn(r);

                mvc.perform(post("/api/v1/reportes/cuentas-pagar/informe"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.destinatarios").value(2))
                                .andExpect(jsonPath("$.data.vencidas").value(1))
                                .andExpect(jsonPath("$.data.pendientes").value(1));
        }

        @Test
        @DisplayName("GET /api/v1/reportes/cuentas-pagar/informe/estado -> 200 con yaEnviado")
        void estado_returns200() throws Exception {
                var r = new CuentasPagarEstadoResponse(LocalDate.of(2026, 10, 5), true,
                                "ENVIADA", java.time.Instant.now());
                when(cuentasService.estado()).thenReturn(r);

                mvc.perform(get("/api/v1/reportes/cuentas-pagar/informe/estado"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.yaEnviado").value(true));
        }
}
