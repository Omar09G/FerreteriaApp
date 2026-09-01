package mx.ferreteria.api.cat.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import mx.ferreteria.api.cat.dto.CatDtos.ProductoRequest;
import mx.ferreteria.api.cat.dto.CatDtos.ProductoResponse;
import mx.ferreteria.api.cat.service.ProductoService;
import mx.ferreteria.api.common.error.DbErrorTranslator;
import mx.ferreteria.api.common.web.WebMvcTestProps;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.i18n.ErrorCode;

@WebMvcTest(controllers = ProductoController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({DbErrorTranslator.class, WebMvcTestProps.class, ProductoControllerTest.SliceConfig.class})
@MockBean({mx.ferreteria.api.common.security.JwtAuthFilter.class,
           mx.ferreteria.api.common.security.RestAuthEntryPoint.class,
           mx.ferreteria.api.common.security.JwtService.class})
class ProductoControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    ProductoService service;

    @org.springframework.boot.test.context.TestConfiguration
    static class SliceConfig {
        @org.springframework.context.annotation.Bean
        mx.ferreteria.api.common.web.RequestIdProperties requestIdProperties() {
            return new mx.ferreteria.api.common.web.RequestIdProperties(
                    mx.ferreteria.api.common.web.RequestIdProperties.Mode.GENERATE);
        }
    }

    private ProductoResponse sampleProducto() {
        return new ProductoResponse(1L, "P001", "PRODUCTO", "Taladro", "desc",
                1, "Herramientas", 1, "Acme", 1, "PZA",
                new BigDecimal("100.00"), new BigDecimal("150.00"), null, true);
    }

    // ── GET /api/v1/productos ───────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/productos -> 200 con envelope {success, data, meta}")
    void list_returns200WithEnvelope() throws Exception {
        ProductoResponse p = sampleProducto();
        when(service.list(eq(null), eq(null), eq(null), eq(null), any()))
                .thenReturn(new PageImpl<>(List.of(p), PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/v1/productos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].nombre").value("Taladro"))
                .andExpect(jsonPath("$.meta.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/productos?categoriaId=1 -> 200 filtrado por categoria")
    void list_withCategoriaId() throws Exception {
        ProductoResponse p = sampleProducto();
        when(service.list(eq(null), eq(1), eq(null), eq(null), any()))
                .thenReturn(new PageImpl<>(List.of(p), PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/v1/productos").param("categoriaId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    // ── GET /api/v1/productos/{id} ─────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/productos/1 -> 200 con entidad")
    void getById_found() throws Exception {
        when(service.getById(1L)).thenReturn(sampleProducto());

        mvc.perform(get("/api/v1/productos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.productoId").value(1))
                .andExpect(jsonPath("$.data.nombre").value("Taladro"));
    }

    @Test
    @DisplayName("GET /api/v1/productos/999 -> 404 RECURSO_NO_ENCONTRADO")
    void getById_notFound() throws Exception {
        when(service.getById(999L))
                .thenThrow(new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));

        mvc.perform(get("/api/v1/productos/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value(404))
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    // ── POST /api/v1/productos ──────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/productos valido -> 201 con entidad creada")
    void create_valid() throws Exception {
        when(service.create(any(ProductoRequest.class))).thenReturn(sampleProducto());

        mvc.perform(post("/api/v1/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"PRODUCTO","nombre":"Taladro","categoriaId":1,
                                 "unidadMedidaId":1,"marcaId":1}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nombre").value("Taladro"));
    }

    @Test
    @DisplayName("POST /api/v1/productos sin categoriaId -> 400 CAMPO_REQUERIDO")
    void create_nullCategoriaId() throws Exception {
        mvc.perform(post("/api/v1/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"PRODUCTO","nombre":"X","unidadMedidaId":1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value(400))
                .andExpect(jsonPath("$.codigo").value("CAMPO_REQUERIDO"));
    }

    // ── PUT /api/v1/productos/{id} ─────────────────────────────────

    @Test
    @DisplayName("PUT /api/v1/productos/1 -> 200 con entidad actualizada")
    void update_found() throws Exception {
        when(service.update(eq(1L), any(ProductoRequest.class))).thenReturn(sampleProducto());

        mvc.perform(put("/api/v1/productos/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"PRODUCTO","nombre":"Taladro","categoriaId":1,
                                 "unidadMedidaId":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nombre").value("Taladro"));
    }

    // ── DELETE /api/v1/productos/{id} ──────────────────────────────

    @Test
    @DisplayName("DELETE /api/v1/productos/1 -> 204 No Content")
    void deactivate() throws Exception {
        doNothing().when(service).deactivate(1L);

        mvc.perform(delete("/api/v1/productos/1"))
                .andExpect(status().isNoContent());
    }
}
