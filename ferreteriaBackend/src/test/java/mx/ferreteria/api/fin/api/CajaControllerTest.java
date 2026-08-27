package mx.ferreteria.api.fin.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import mx.ferreteria.api.common.error.DbErrorTranslator;
import mx.ferreteria.api.fin.dto.FinDtos;

@WebMvcTest(controllers = CajaController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({DbErrorTranslator.class, CajaControllerTest.SliceConfig.class})
@MockBean({mx.ferreteria.api.common.security.JwtAuthFilter.class,
           mx.ferreteria.api.common.security.RestAuthEntryPoint.class,
           mx.ferreteria.api.common.security.JwtService.class})
class CajaControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    mx.ferreteria.api.fin.service.CajaService service;

    @org.springframework.boot.test.context.TestConfiguration
    static class SliceConfig {
        @org.springframework.context.annotation.Bean
        mx.ferreteria.api.common.web.RequestIdProperties requestIdProperties() {
            return new mx.ferreteria.api.common.web.RequestIdProperties(
                    mx.ferreteria.api.common.web.RequestIdProperties.Mode.GENERATE);
        }
    }

    // ── GET /api/v1/cajas ──────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/cajas -> 200 con array de 2 cajas")
    void listCajas_returns200() throws Exception {
        var r1 = new FinDtos.CajaResponse(1, "Caja Principal", 1, "Almacén Central", true);
        var r2 = new FinDtos.CajaResponse(2, "Caja Norte", 2, "Almacén Norte", true);
        when(service.listCajas()).thenReturn(List.of(r1, r2));

        mvc.perform(get("/api/v1/cajas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    // ── POST /api/v1/cajas/{id}/turnos ─────────────────────────

    @Test
    @DisplayName("POST /api/v1/cajas/1/turnos válido -> 201")
    void abrirTurno_ok() throws Exception {
        var resp = new FinDtos.TurnoCajaResponse(
                1L, 1, "Caja Principal", 10, Instant.now(),
                new BigDecimal("5000"), null, null, null, null, "ABIERTO", null);
        when(service.abrirTurno(any(FinDtos.TurnoAperturaRequest.class))).thenReturn(resp);

        mvc.perform(post("/api/v1/cajas/1/turnos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"montoApertura\":5000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.turnoCajaId").value(1));
    }

    // ── POST /api/v1/cajas/{cajaId}/turnos/{turnoId}/movimientos ─

    @Test
    @DisplayName("POST /api/v1/cajas/1/turnos/1/movimientos válido -> 201")
    void registrarMovimiento_ok() throws Exception {
        var resp = new FinDtos.MovimientoCajaResponse(
                1L, 1L, "INGRESO", "Venta al contado", new BigDecimal("1500"),
                1, "Efectivo", null, null, Instant.now());
        when(service.registrarMovimiento(eq(1L), any(FinDtos.MovimientoCajaRequest.class)))
                .thenReturn(resp);

        mvc.perform(post("/api/v1/cajas/1/turnos/1/movimientos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"INGRESO\",\"concepto\":\"Venta al contado\",\"monto\":1500}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.movimientoId").value(1));
    }

    // ── GET /api/v1/cajas/{cajaId}/turnos/{turnoId}/movimientos ─

    @Test
    @DisplayName("GET /api/v1/cajas/1/turnos/1/movimientos -> 200")
    void listMovimientos_returns200() throws Exception {
        var m1 = new FinDtos.MovimientoCajaResponse(
                1L, 1L, "INGRESO", "Venta", new BigDecimal("500"),
                1, "Efectivo", null, null, Instant.now());
        var m2 = new FinDtos.MovimientoCajaResponse(
                2L, 1L, "EGRESO", "Compra", new BigDecimal("200"),
                1, "Efectivo", null, null, Instant.now());
        when(service.listMovimientos(1L)).thenReturn(List.of(m1, m2));

        mvc.perform(get("/api/v1/cajas/1/turnos/1/movimientos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }
}
