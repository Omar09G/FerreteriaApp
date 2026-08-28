package mx.ferreteria.api.inv.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
import mx.ferreteria.api.inv.dto.InvDtos.MovimientoInventarioRequest;
import mx.ferreteria.api.inv.dto.InvDtos.MovimientoInventarioResponse;
import mx.ferreteria.api.inv.service.MovimientoService;

@WebMvcTest(controllers = MovimientoController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({DbErrorTranslator.class, MovimientoControllerTest.SliceConfig.class})
@MockBean({mx.ferreteria.api.common.security.JwtAuthFilter.class,
           mx.ferreteria.api.common.security.RestAuthEntryPoint.class,
           mx.ferreteria.api.common.security.JwtService.class})
class MovimientoControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    MovimientoService service;

    @org.springframework.boot.test.context.TestConfiguration
    static class SliceConfig {
        @org.springframework.context.annotation.Bean
        mx.ferreteria.api.common.web.RequestIdProperties requestIdProperties() {
            return new mx.ferreteria.api.common.web.RequestIdProperties(
                    mx.ferreteria.api.common.web.RequestIdProperties.Mode.GENERATE);
        }
    }

    // ── GET /api/v1/movimientos?productoId=1 ──────────────────────

    @Test
    @DisplayName("GET /api/v1/movimientos?productoId=1 -> 200 con envelope")
    void listByProducto_returns200() throws Exception {
        MovimientoInventarioResponse r = new MovimientoInventarioResponse(
                1L, 1L, "Taladro", 1, "Almacén Central",
                "ENTRADA", new BigDecimal("10"), null,
                1, null, null, null, null, null, null, null);
        when(service.listByProducto(eq(1L), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(r), PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/v1/movimientos").param("productoId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.meta.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/movimientos con rango de fechas -> 200")
    void list_conRango_returns200() throws Exception {
        MovimientoInventarioResponse r = new MovimientoInventarioResponse(
                1L, 1L, "Taladro", 1, "Almacén Central",
                "ENTRADA", new BigDecimal("10"), null,
                1, null, null, null, null, null, null, null);
        when(service.list(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(r), PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/v1/movimientos")
                        .param("fechaInicio", "2026-01-01")
                        .param("fechaFin", "2026-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/movimientos con rango invertido -> 400 VALOR_INVALIDO")
    void list_rangoInvalido_400() throws Exception {
        mvc.perform(get("/api/v1/movimientos")
                        .param("fechaInicio", "2026-02-01")
                        .param("fechaFin", "2026-01-31"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.codigo").value("VALOR_INVALIDO"));
    }

    // ── POST /api/v1/movimientos ──────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/movimientos válido -> 201 con entidad creada")
    void create_valid() throws Exception {
        MovimientoInventarioResponse resp = new MovimientoInventarioResponse(
                1L, 1L, "Taladro", 1, "Almacén Central",
                "ENTRADA", new BigDecimal("5"), null,
                1, null, null, null, null, null, null, null);
        when(service.create(any(MovimientoInventarioRequest.class)))
                .thenReturn(resp);

        mvc.perform(post("/api/v1/movimientos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productoId\":1,\"almacenId\":1,\"tipo\":\"ENTRADA\",\"cantidad\":5,\"motivoId\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.movimientoId").value(1))
                .andExpect(jsonPath("$.data.tipo").value("ENTRADA"));
    }

    @Test
    @DisplayName("POST /api/v1/movimientos productoId null -> 400 CAMPO_REQUERIDO")
    void create_nullProductoId() throws Exception {
        mvc.perform(post("/api/v1/movimientos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"almacenId\":1,\"tipo\":\"ENTRADA\",\"cantidad\":5,\"motivoId\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value(400))
                .andExpect(jsonPath("$.codigo").value("CAMPO_REQUERIDO"));
    }
}
