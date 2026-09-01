package mx.ferreteria.api.ven.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import mx.ferreteria.api.common.web.WebMvcTestProps;
import mx.ferreteria.api.ven.dto.VenDtos;
import mx.ferreteria.api.ven.service.VentaService;

@WebMvcTest(controllers = VentaController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({DbErrorTranslator.class, WebMvcTestProps.class, VentaControllerTest.SliceConfig.class})
@MockBean({mx.ferreteria.api.common.security.JwtAuthFilter.class,
           mx.ferreteria.api.common.security.RestAuthEntryPoint.class,
           mx.ferreteria.api.common.security.JwtService.class})
class VentaControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    VentaService service;

    @org.springframework.boot.test.context.TestConfiguration
    static class SliceConfig {
        @org.springframework.context.annotation.Bean
        mx.ferreteria.api.common.web.RequestIdProperties requestIdProperties() {
            return new mx.ferreteria.api.common.web.RequestIdProperties(
                    mx.ferreteria.api.common.web.RequestIdProperties.Mode.GENERATE);
        }
    }

    private VenDtos.VentaResponse sampleResp() {
        return new VenDtos.VentaResponse(
                1L, "V-2024-001", null, null, 1, "Almacen Central",
                Instant.now(), LocalDate.now(), 1, "EFECTIVO",
                new BigDecimal("16.00"), true,
                new BigDecimal("100.00"), new BigDecimal("16.00"),
                BigDecimal.ZERO, new BigDecimal("116.00"),
                "COMPLETADA", 1, null, null, List.of(), List.of());
    }

    // ── POST /api/v1/ventas ─────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/ventas válido -> 201 con ventaId")
    void checkout_ok() throws Exception {
        when(service.checkout(any(VenDtos.VentaRequest.class)))
                .thenReturn(sampleResp());

        mvc.perform(post("/api/v1/ventas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "almacenId": 1,
                                  "formaPagoId": 1,
                                  "detalles": [{"productoId": 1, "cantidad": 2, "precioUnitario": 50.00}],
                                  "pagos": [{"formaPagoId": 1, "monto": 116.00}]
                                }"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.ventaId").value(1))
                .andExpect(jsonPath("$.data.folio").value("V-2024-001"));
    }

    // ── GET /api/v1/ventas ──────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/ventas -> 200 con array de ventas")
    void list_returns200() throws Exception {
        when(service.listByFechaLocal(eq(null), eq(null), eq(null), any()))
                .thenReturn(new PageImpl<>(List.of(sampleResp()), PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/v1/ventas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    // ── GET /api/v1/ventas/{id} ─────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/ventas/1 -> 200 con folio")
    void getById_found() throws Exception {
        when(service.getById(1L)).thenReturn(sampleResp());

        mvc.perform(get("/api/v1/ventas/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.folio").value("V-2024-001"));
    }

    // ── PATCH /api/v1/ventas/{id}/cancelar ──────────────────────────

    @Test
    @DisplayName("PATCH /api/v1/ventas/1/cancelar -> 200")
    void cancel_ok() throws Exception {
        VenDtos.VentaResponse cancelled = new VenDtos.VentaResponse(
                1L, "V-2024-001", null, null, 1, "Almacen Central",
                Instant.now(), LocalDate.now(), 1, "EFECTIVO",
                new BigDecimal("16.00"), true,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                "CANCELADA", 1, null, null, List.of(), List.of());

        when(service.cancel(eq(1L), eq("Error"))).thenReturn(cancelled);

        mvc.perform(patch("/api/v1/ventas/1/cancelar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Error\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.estado").value("CANCELADA"));
    }
}
