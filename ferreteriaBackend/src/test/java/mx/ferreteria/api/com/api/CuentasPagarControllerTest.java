package mx.ferreteria.api.com.api;

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
import mx.ferreteria.api.common.web.WebMvcTestProps;
import mx.ferreteria.api.com.dto.ComDtos.CuentasPagarResponse;
import mx.ferreteria.api.com.dto.ComDtos.FacturaPendienteResponse;
import mx.ferreteria.api.com.dto.ComDtos.FacturaProveedorResponse;
import mx.ferreteria.api.com.dto.ComDtos.FacturaVencidaResponse;
import mx.ferreteria.api.com.service.CompraService;

@WebMvcTest(controllers = CuentasPagarController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({DbErrorTranslator.class, WebMvcTestProps.class, CuentasPagarControllerTest.SliceConfig.class})
@MockBean({mx.ferreteria.api.common.security.JwtAuthFilter.class,
           mx.ferreteria.api.common.security.RestAuthEntryPoint.class,
           mx.ferreteria.api.common.security.JwtService.class})
class CuentasPagarControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    CompraService service;

    @org.springframework.boot.test.context.TestConfiguration
    static class SliceConfig {
        @org.springframework.context.annotation.Bean
        mx.ferreteria.api.common.web.RequestIdProperties requestIdProperties() {
            return new mx.ferreteria.api.common.web.RequestIdProperties(
                    mx.ferreteria.api.common.web.RequestIdProperties.Mode.GENERATE);
        }
    }

    @Test
    @DisplayName("GET /api/v1/cuentas-pagar -> 200 con vencidas/pendientes de proveedores")
    void cuentasPagar_returns200() throws Exception {
        when(service.cuentasPagar(null)).thenReturn(List.of(new CuentasPagarResponse(
                1L, "COMPRA-0001", "Ferritas SA",
                new BigDecimal("1160.00"), new BigDecimal("600.00"),
                new BigDecimal("560.00"), LocalDate.now(), 5, "PENDIENTE")));

        mvc.perform(get("/api/v1/cuentas-pagar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].proveedor").value("Ferritas SA"))
                .andExpect(jsonPath("$.data[0].saldo").value(560.0));
    }

    @Test
    @DisplayName("GET /api/v1/reportes/facturas-vencidas -> 200")
    void facturasVencidas_returns200() throws Exception {
        when(service.facturasVencidas()).thenReturn(List.of(new FacturaVencidaResponse(
                1L, "COMPRA-0001", "F-0001", 1, "Ferritas SA", "555-0100",
                LocalDate.now().minusDays(30), new BigDecimal("1160.00"),
                new BigDecimal("1160.00"), BigDecimal.ZERO,
                LocalDate.now().minusDays(10), 10, "10-20 dias")));

        mvc.perform(get("/api/v1/reportes/facturas-vencidas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].diasVencido").value(10));
    }

    @Test
    @DisplayName("GET /api/v1/reportes/facturas-pendientes -> 200")
    void facturasPendientes_returns200() throws Exception {
        when(service.facturasPendientes()).thenReturn(List.of(new FacturaPendienteResponse(
                1L, "COMPRA-0001", "F-0001", 1, "Ferritas SA", LocalDate.now().minusDays(30),
                new BigDecimal("1160.00"), new BigDecimal("400.00"),
                new BigDecimal("760.00"), "ABONADO",
                LocalDate.now().plusDays(5), 5, "PROXIMA")));

        mvc.perform(get("/api/v1/reportes/facturas-pendientes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].estadoPago").value("ABONADO"));
    }

    @Test
    @DisplayName("GET /api/v1/facturas-proveedor/1 -> 200")
    void facturasProveedor_returns200() throws Exception {
        when(service.facturasProveedor(1)).thenReturn(List.of(new FacturaProveedorResponse(
                1, 1, "Ferritas SA", "COMPRA-0001", "F-0001",
                LocalDate.now().minusDays(5), new BigDecimal("1000.00"),
                new BigDecimal("160.00"), new BigDecimal("1160.00"),
                new BigDecimal("1160.00"), new BigDecimal("1160.00"),
                BigDecimal.ZERO, "CONTADO", LocalDate.now().plusDays(55))));

        mvc.perform(get("/api/v1/facturas-proveedor/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].numeroMasReciente").value(1))
                .andExpect(jsonPath("$.data[0].estadoPago").value("CONTADO"));
    }
}