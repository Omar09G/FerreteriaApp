package mx.ferreteria.api.fin.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import mx.ferreteria.api.common.error.DbErrorTranslator;
import mx.ferreteria.api.fin.dto.FinDtos;

@WebMvcTest(controllers = GastoController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({DbErrorTranslator.class, GastoControllerTest.SliceConfig.class})
@MockBean({mx.ferreteria.api.common.security.JwtAuthFilter.class,
           mx.ferreteria.api.common.security.RestAuthEntryPoint.class,
           mx.ferreteria.api.common.security.JwtService.class})
class GastoControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    mx.ferreteria.api.fin.service.GastoService service;

    @org.springframework.boot.test.context.TestConfiguration
    static class SliceConfig {
        @org.springframework.context.annotation.Bean
        mx.ferreteria.api.common.web.RequestIdProperties requestIdProperties() {
            return new mx.ferreteria.api.common.web.RequestIdProperties(
                    mx.ferreteria.api.common.web.RequestIdProperties.Mode.GENERATE);
        }
    }

    // ── GET /api/v1/gastos ─────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/gastos -> 200 con envelope {success, data, meta}")
    void listGastos_returns200() throws Exception {
        var r1 = new FinDtos.GastoResponse(
                1L, "GTO-001", 1, "Papelería", "Compra de papelería",
                new BigDecimal("250"), LocalDate.now(), 1, "Efectivo",
                null, null, null, 10, Instant.now());
        when(service.listGastos(PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(r1), PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/v1/gastos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    // ── POST /api/v1/gastos ────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/gastos válido -> 201")
    void createGasto_ok() throws Exception {
        var resp = new FinDtos.GastoResponse(
                10L, "GTO-010", 1, "Papelería", "Compra papelería",
                new BigDecimal("500"), LocalDate.now(), 1, "Efectivo",
                null, null, null, 10, Instant.now());
        when(service.createGasto(any(FinDtos.GastoRequest.class))).thenReturn(resp);

        mvc.perform(post("/api/v1/gastos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipoGastoId\":1,\"descripcion\":\"Compra papelería\",\"monto\":500,\"formaPagoId\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.gastoId").value(10));
    }

    // ── GET /api/v1/ingresos-otros ─────────────────────────────

    @Test
    @DisplayName("GET /api/v1/ingresos-otros -> 200 con envelope {success, data, meta}")
    void listIngresos_returns200() throws Exception {
        var r1 = new FinDtos.IngresoOtroResponse(
                1L, "Alquiler de equipo", new BigDecimal("3000"),
                LocalDate.now(), 1, "Efectivo", null, 10, Instant.now());
        when(service.listIngresos(PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(r1), PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/v1/ingresos-otros"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    // ── POST /api/v1/ingresos-otros ────────────────────────────

    @Test
    @DisplayName("POST /api/v1/ingresos-otros válido -> 201")
    void createIngreso_ok() throws Exception {
        var resp = new FinDtos.IngresoOtroResponse(
                10L, "Comisión por venta", new BigDecimal("500"),
                LocalDate.now(), 1, "Transferencia", null, 10, Instant.now());
        when(service.createIngreso(any(FinDtos.IngresoOtroRequest.class))).thenReturn(resp);

        mvc.perform(post("/api/v1/ingresos-otros")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"concepto\":\"Comisión por venta\",\"monto\":500,\"formaPagoId\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.ingresoOtroId").value(10));
    }
}
