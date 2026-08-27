package mx.ferreteria.api.ven.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import mx.ferreteria.api.common.error.DbErrorTranslator;
import mx.ferreteria.api.ven.dto.ReportDtos;

@WebMvcTest(controllers = ReporteController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({DbErrorTranslator.class, ReporteControllerTest.SliceConfig.class})
@MockBean({mx.ferreteria.api.common.security.JwtAuthFilter.class,
           mx.ferreteria.api.common.security.RestAuthEntryPoint.class,
           mx.ferreteria.api.common.security.JwtService.class})
class ReporteControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    mx.ferreteria.api.ven.service.ReporteService service;

    @org.springframework.boot.test.context.TestConfiguration
    static class SliceConfig {
        @org.springframework.context.annotation.Bean
        mx.ferreteria.api.common.web.RequestIdProperties requestIdProperties() {
            return new mx.ferreteria.api.common.web.RequestIdProperties(
                    mx.ferreteria.api.common.web.RequestIdProperties.Mode.GENERATE);
        }
    }

    // ── GET /api/v1/reportes/top-productos ─────────────────────

    @Test
    @DisplayName("GET /api/v1/reportes/top-productos -> 200 con data array")
    void topProductos_returns200() throws Exception {
        var r1 = new ReportDtos.TopProductoResponse(
                LocalDate.now(), 1L, "TAL-001", "Taladro 1/2\"",
                "Herramientas", new BigDecimal("15"), new BigDecimal("7500"),
                new BigDecimal("4500"), new BigDecimal("3000"), 1L, 1L);
        when(service.topProductos()).thenReturn(List.of(r1));

        mvc.perform(get("/api/v1/reportes/top-productos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    // ── GET /api/v1/reportes/dashboard ─────────────────────────

    @Test
    @DisplayName("GET /api/v1/reportes/dashboard -> 200 con data.ventasHoy")
    void dashboard_returns200() throws Exception {
        var resp = new ReportDtos.ResumenDashboardResponse(
                new BigDecimal("15000"), 25L,
                new BigDecimal("350000"), new BigDecimal("600"),
                new BigDecimal("12000"), BigDecimal.ZERO,
                new BigDecimal("500000"), 3L, 5L, 2L);
        when(service.resumenDashboard()).thenReturn(resp);

        mvc.perform(get("/api/v1/reportes/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.ventasHoy").value(15000))
                .andExpect(jsonPath("$.data.ticketsHoy").value(25));
    }

    // ── GET /api/v1/reportes/horas-pico ────────────────────────

    @Test
    @DisplayName("GET /api/v1/reportes/horas-pico -> 200 con data array")
    void ventasPorHora_returns200() throws Exception {
        var r1 = new ReportDtos.VentaPorHoraResponse(
                12, 15L, new BigDecimal("45000"), new BigDecimal("3000"), 1L);
        var r2 = new ReportDtos.VentaPorHoraResponse(
                13, 12L, new BigDecimal("36000"), new BigDecimal("3000"), 2L);
        when(service.ventasPorHora()).thenReturn(List.of(r1, r2));

        mvc.perform(get("/api/v1/reportes/horas-pico"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    // ── GET /api/v1/reportes/cierre-diario ─────────────────────

    @Test
    @DisplayName("GET /api/v1/reportes/cierre-diario -> 200 con data array")
    void cierreDiario_returns200() throws Exception {
        var r1 = new ReportDtos.CierreDiarioResponse(
                LocalDate.now(), 3L, 25L,
                new BigDecimal("150000"), new BigDecimal("45000"),
                new BigDecimal("30"), BigDecimal.ZERO,
                new BigDecimal("80000"), new BigDecimal("5000"),
                new BigDecimal("75000"), BigDecimal.ZERO,
                new BigDecimal("70000"), true);
        when(service.cierreDiario()).thenReturn(List.of(r1));

        mvc.perform(get("/api/v1/reportes/cierre-diario"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1));
    }
}
