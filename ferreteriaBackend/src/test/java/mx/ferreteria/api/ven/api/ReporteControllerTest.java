package mx.ferreteria.api.ven.api;

import static org.mockito.ArgumentMatchers.any;
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
import mx.ferreteria.api.ven.dto.ReportDtos.CierreDiarioResponse;
import mx.ferreteria.api.ven.dto.ReportDtos.ResumenDashboardResponse;
import mx.ferreteria.api.ven.dto.ReportDtos.TopProductoResponse;
import mx.ferreteria.api.ven.dto.ReportDtos.VentaPorHoraResponse;
import mx.ferreteria.api.ven.service.ReporteService;

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
    ReporteService service;

    @org.springframework.boot.test.context.TestConfiguration
    static class SliceConfig {
        @org.springframework.context.annotation.Bean
        mx.ferreteria.api.common.web.RequestIdProperties requestIdProperties() {
            return new mx.ferreteria.api.common.web.RequestIdProperties(
                    mx.ferreteria.api.common.web.RequestIdProperties.Mode.GENERATE);
        }
    }

    @Test
    @DisplayName("GET /api/v1/reportes/top-productos -> 200")
    void topProductos_returns200() throws Exception {
        TopProductoResponse r1 = new TopProductoResponse(
                LocalDate.now(), 1L, "P-001", "Martillo",
                "Herramientas", new BigDecimal("120.000"),
                new BigDecimal("15000.00"), new BigDecimal("9000.00"),
                new BigDecimal("6000.00"), 1L, 1L);
        when(service.topProductos(any(), any())).thenReturn(List.of(r1));

        mvc.perform(get("/api/v1/reportes/top-productos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].producto").value("Martillo"));
    }

    @Test
    @DisplayName("GET /api/v1/reportes/top-productos con rango -> 200 y envía rango al servicio")
    void topProductos_conRango_returns200() throws Exception {
        when(service.topProductos(any(), any())).thenReturn(List.of());

        mvc.perform(get("/api/v1/reportes/top-productos")
                        .param("fechaInicio", "2026-01-01")
                        .param("fechaFin", "2026-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/reportes/top-productos con rango invertido -> 400 VALOR_INVALIDO")
    void topProductos_rangoInvalido_400() throws Exception {
        mvc.perform(get("/api/v1/reportes/top-productos")
                        .param("fechaInicio", "2026-02-01")
                        .param("fechaFin", "2026-01-31"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.codigo").value("VALOR_INVALIDO"));
    }

    @Test
    @DisplayName("GET /api/v1/reportes/dashboard -> 200 con KPIs")
    void dashboard_returns200() throws Exception {
        ResumenDashboardResponse r1 = new ResumenDashboardResponse(
                new BigDecimal("15000.00"), 25L, new BigDecimal("800.00"),
                new BigDecimal("40000.00"), new BigDecimal("5000.00"),
                new BigDecimal("1800000.00"), 3L, 2L, 1L);
        when(service.resumenDashboard(any(), any())).thenReturn(r1);

        mvc.perform(get("/api/v1/reportes/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.ventasEnRango").value(15000.00))
                .andExpect(jsonPath("$.data.ticketsEnRango").value(25));
    }

    @Test
    @DisplayName("GET /api/v1/reportes/horas-pico -> 200")
    void horasPico_returns200() throws Exception {
        VentaPorHoraResponse r1 = new VentaPorHoraResponse(
                17, 20L, new BigDecimal("30000.00"),
                new BigDecimal("1500.00"), 1L);
        when(service.ventasPorHora(any(), any())).thenReturn(List.of(r1));

        mvc.perform(get("/api/v1/reportes/horas-pico"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].hora").value(17))
                .andExpect(jsonPath("$.data[0].rankingHorario").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/reportes/horas-pico con rango -> 200")
    void horasPico_conRango_returns200() throws Exception {
        when(service.ventasPorHora(any(), any())).thenReturn(List.of());

        mvc.perform(get("/api/v1/reportes/horas-pico")
                        .param("fechaInicio", "2026-01-01")
                        .param("fechaFin", "2026-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/reportes/cierre-diario -> 200")
    void cierreDiario_returns200() throws Exception {
        CierreDiarioResponse r1 = new CierreDiarioResponse(
                LocalDate.now(), 2L, 40L,
                new BigDecimal("45000.00"), new BigDecimal("18000.00"),
                new BigDecimal("40.00"), new BigDecimal("500.00"),
                new BigDecimal("40000.00"), new BigDecimal("1000.00"),
                new BigDecimal("39000.00"), BigDecimal.ZERO,
                new BigDecimal("5000.00"), true);
        when(service.cierreDiario(any(), any())).thenReturn(List.of(r1));

        mvc.perform(get("/api/v1/reportes/cierre-diario"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].todoCuadrado").value(true));
    }
}