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
import mx.ferreteria.api.notif.dto.StockBajoDtos.StockBajoEnvioResponse;
import mx.ferreteria.api.notif.dto.StockBajoDtos.StockBajoEstadoResponse;
import mx.ferreteria.api.notif.service.StockBajoInformeService;

@WebMvcTest(controllers = StockBajoInformeController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ DbErrorTranslator.class, WebMvcTestProps.class, StockBajoInformeControllerTest.SliceConfig.class })
@MockitoBean(types = { mx.ferreteria.api.common.security.JwtAuthFilter.class,
                mx.ferreteria.api.common.security.RestAuthEntryPoint.class,
                mx.ferreteria.api.common.security.JwtService.class })
class StockBajoInformeControllerTest {

        @Autowired
        MockMvc mvc;

        @MockitoBean
        StockBajoInformeService stockService;

        @org.springframework.boot.test.context.TestConfiguration
        static class SliceConfig {
                @org.springframework.context.annotation.Bean
                mx.ferreteria.api.common.web.RequestIdProperties requestIdProperties() {
                        return new mx.ferreteria.api.common.web.RequestIdProperties(
                                        mx.ferreteria.api.common.web.RequestIdProperties.Mode.GENERATE);
                }
        }

        @Test
        @DisplayName("POST /api/v1/reportes/stock-bajo/informe -> 200 con conteos")
        void enviar_returns200() throws Exception {
                var r = new StockBajoEnvioResponse(LocalDate.of(2026, 10, 5), 2, 2, 1, 10, 3, 2);
                when(stockService.enviar()).thenReturn(r);

                mvc.perform(post("/api/v1/reportes/stock-bajo/informe"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.destinatarios").value(2))
                                .andExpect(jsonPath("$.data.productos").value(10))
                                .andExpect(jsonPath("$.data.agotados").value(3));
        }

        @Test
        @DisplayName("GET /api/v1/reportes/stock-bajo/informe/estado -> 200 con yaEnviado")
        void estado_returns200() throws Exception {
                var r = new StockBajoEstadoResponse(LocalDate.of(2026, 10, 5), false,
                                null, null);
                when(stockService.estado()).thenReturn(r);

                mvc.perform(get("/api/v1/reportes/stock-bajo/informe/estado"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.yaEnviado").value(false));
        }
}
