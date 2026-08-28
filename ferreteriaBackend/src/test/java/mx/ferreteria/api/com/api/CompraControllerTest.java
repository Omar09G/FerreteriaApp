package mx.ferreteria.api.com.api;

import static org.mockito.ArgumentMatchers.any;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import mx.ferreteria.api.common.error.DbErrorTranslator;
import mx.ferreteria.api.com.dto.ComDtos.CompraDetalleResponse;
import mx.ferreteria.api.com.dto.ComDtos.CompraResponse;
import mx.ferreteria.api.com.service.CompraService;

@WebMvcTest(controllers = CompraController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({DbErrorTranslator.class, CompraControllerTest.SliceConfig.class})
@MockBean({mx.ferreteria.api.common.security.JwtAuthFilter.class,
           mx.ferreteria.api.common.security.RestAuthEntryPoint.class,
           mx.ferreteria.api.common.security.JwtService.class})
class CompraControllerTest {

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

    private CompraResponse sampleResponse() {
        return new CompraResponse(
                1L, "COMPRA-0001", "F-0001", 1, "Ferritas SA",
                1, "Bodega Central", Instant.parse("2026-01-15T10:00:00Z"),
                1, "Contado", new BigDecimal("1000.00"), new BigDecimal("160.00"),
                BigDecimal.ZERO, new BigDecimal("1160.00"), "RECIBIDA", 1, null,
                "Primera compra", List.of(new CompraDetalleResponse(
                        1L, 10L, "Taladro", new BigDecimal("10.000"),
                        new BigDecimal("100.00"), new BigDecimal("1000.00"))));
    }

    @Test
    @DisplayName("GET /api/v1/compras -> 200 con paginacion")
    void list_returns200() throws Exception {
        when(service.list(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(sampleResponse()),
                        PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/v1/compras"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].folio").value("COMPRA-0001"))
                .andExpect(jsonPath("$.data[0].proveedor").value("Ferritas SA"))
                .andExpect(jsonPath("$.meta.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/compras/1 -> 200 con detalle")
    void getById_returns200() throws Exception {
        when(service.getById(1L)).thenReturn(sampleResponse());

        mvc.perform(get("/api/v1/compras/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.compraId").value(1))
                .andExpect(jsonPath("$.data.estado").value("RECIBIDA"))
                .andExpect(jsonPath("$.data.detalles.length()").value(1));
    }

    @Test
    @DisplayName("POST /api/v1/compras -> 201 crea compra")
    void create_returns201() throws Exception {
        when(service.create(any())).thenReturn(sampleResponse());

        mvc.perform(post("/api/v1/compras")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"proveedorId":1,"almacenId":1,"formaPagoId":1,
                                 "detalles":[{"productoId":10,"cantidad":10.0,"costoUnitario":100.0}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.folio").value("COMPRA-0001"))
                .andExpect(jsonPath("$.data.total").value(1160.0));
    }
}