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
import mx.ferreteria.api.fin.dto.FinDtos.CajaResponse;
import mx.ferreteria.api.fin.dto.FinDtos.CorteCajaResponse;
import mx.ferreteria.api.fin.dto.FinDtos.MovimientoCajaResponse;
import mx.ferreteria.api.fin.dto.FinDtos.TurnoCajaResponse;
import mx.ferreteria.api.fin.service.CajaService;

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
    CajaService service;

    @org.springframework.boot.test.context.TestConfiguration
    static class SliceConfig {
        @org.springframework.context.annotation.Bean
        mx.ferreteria.api.common.web.RequestIdProperties requestIdProperties() {
            return new mx.ferreteria.api.common.web.RequestIdProperties(
                    mx.ferreteria.api.common.web.RequestIdProperties.Mode.GENERATE);
        }
    }

    @Test
    @DisplayName("GET /api/v1/cajas -> 200 con lista de cajas")
    void listCajas_returns200() throws Exception {
        when(service.listCajas()).thenReturn(List.of(
                new CajaResponse(1, "Caja Central", 1, "Almacen Central", true),
                new CajaResponse(2, "Caja Norte", 2, "Almacen Norte", true)));

        mvc.perform(get("/api/v1/cajas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].nombre").value("Caja Central"));
    }

    @Test
    @DisplayName("POST /api/v1/cajas/1/turnos -> 201 turno abierto")
    void abrirTurno_ok() throws Exception {
        TurnoCajaResponse resp = new TurnoCajaResponse(
                10L, 1, "Caja Central", 1, Instant.now(),
                new BigDecimal("5000.00"), null, null, null,
                null, "ABIERTO", null);
        when(service.abrirTurno(any())).thenReturn(resp);

        mvc.perform(post("/api/v1/cajas/1/turnos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"montoApertura\":5000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.turnoCajaId").value(10))
                .andExpect(jsonPath("$.data.estado").value("ABIERTO"));
    }

    @Test
    @DisplayName("POST /api/v1/cajas/1/turnos/1/movimientos -> 201")
    void registrarMovimiento_ok() throws Exception {
        MovimientoCajaResponse resp = new MovimientoCajaResponse(
                5L, 1L, "SALIDA", "GASTO_OPERATIVO",
                new BigDecimal("100.00"), 1, null, null, null, Instant.now(), null);
        when(service.registrarMovimiento(eq(1L), any())).thenReturn(resp);

        mvc.perform(post("/api/v1/cajas/1/turnos/1/movimientos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"SALIDA\",\"concepto\":\"GASTO_OPERATIVO\",\"monto\":100}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.movimientoId").value(5))
                .andExpect(jsonPath("$.data.concepto").value("GASTO_OPERATIVO"));
    }

    @Test
    @DisplayName("GET /api/v1/cajas/1/turnos/1/movimientos -> 200")
    void listMovimientos_returns200() throws Exception {
        when(service.listMovimientos(1L)).thenReturn(List.of(
                new MovimientoCajaResponse(1L, 1L, "ENTRADA", "APERTURA",
                        new BigDecimal("5000.00"), 1, null, null, null, Instant.now(), null)));

        mvc.perform(get("/api/v1/cajas/1/turnos/1/movimientos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].concepto").value("APERTURA"));
    }

    @Test
    @DisplayName("POST /api/v1/cajas/1/turnos/1/corte -> 200 corte completo")
    void cerrarTurno_ok() throws Exception {
        CorteCajaResponse resp = new CorteCajaResponse(
                1L, 1L, 1, "Caja Central", 1, "Almacen Central",
                1, 1, java.time.LocalDate.now(),
                Instant.now(), Instant.now(), 10L,
                new BigDecimal("1000.00"), new BigDecimal("160.00"), BigDecimal.ZERO,
                new BigDecimal("1160.00"), new BigDecimal("600.00"),
                new BigDecimal("400.00"), new BigDecimal("40.00"),
                new BigDecimal("5000.00"),
                new BigDecimal("1160.00"), BigDecimal.ZERO,
                new BigDecimal("6160.00"), new BigDecimal("6160.00"),
                BigDecimal.ZERO, "CUADRADO",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "{}", "{}", "{}", null);
        when(service.cerrarTurno(1L, new mx.ferreteria.api.fin.dto.FinDtos.CorteRequest(new BigDecimal("6160.00"), null)))
                .thenReturn(resp);

        mvc.perform(post("/api/v1/cajas/1/turnos/1/corte")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"montoContado\":6160.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.corteId").value(1))
                .andExpect(jsonPath("$.data.resultadoCaja").value("CUADRADO"));
    }
}