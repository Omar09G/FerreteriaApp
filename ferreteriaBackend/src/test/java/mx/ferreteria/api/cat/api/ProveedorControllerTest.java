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

import mx.ferreteria.api.cat.dto.CatDtos.ProveedorRequest;
import mx.ferreteria.api.cat.dto.CatDtos.ProveedorResponse;
import mx.ferreteria.api.cat.service.ProveedorService;
import mx.ferreteria.api.common.error.DbErrorTranslator;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.i18n.ErrorCode;

@WebMvcTest(controllers = ProveedorController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({DbErrorTranslator.class, ProveedorControllerTest.SliceConfig.class})
@MockBean({mx.ferreteria.api.common.security.JwtAuthFilter.class,
           mx.ferreteria.api.common.security.RestAuthEntryPoint.class,
           mx.ferreteria.api.common.security.JwtService.class})
class ProveedorControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    ProveedorService service;

    @org.springframework.boot.test.context.TestConfiguration
    static class SliceConfig {
        @org.springframework.context.annotation.Bean
        mx.ferreteria.api.common.web.RequestIdProperties requestIdProperties() {
            return new mx.ferreteria.api.common.web.RequestIdProperties(
                    mx.ferreteria.api.common.web.RequestIdProperties.Mode.GENERATE);
        }
    }

    private ProveedorResponse sampleProveedor() {
        return new ProveedorResponse(1, "Proveedor SA", "PRO850101ABC", "601",
                "contacto@proveedor.com", "55998877", 30, new BigDecimal("100000.00"));
    }

    // ── GET /api/v1/proveedores ────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/proveedores -> 200 con envelope {success, data, meta}")
    void list_returns200WithEnvelope() throws Exception {
        ProveedorResponse r1 = sampleProveedor();
        ProveedorResponse r2 = new ProveedorResponse(2, "Otro Proveedor", null, null,
                null, null, 0, BigDecimal.ZERO);
        when(service.list(eq(null), any()))
                .thenReturn(new PageImpl<>(List.of(r1, r2), PageRequest.of(0, 20), 2));

        mvc.perform(get("/api/v1/proveedores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].razonSocial").value("Proveedor SA"))
                .andExpect(jsonPath("$.meta.page").value(0))
                .andExpect(jsonPath("$.meta.size").value(20))
                .andExpect(jsonPath("$.meta.totalElements").value(2));
    }

    // ── GET /api/v1/proveedores/{id} ──────────────────────────────

    @Test
    @DisplayName("GET /api/v1/proveedores/1 -> 200 con entidad")
    void getById_found() throws Exception {
        when(service.getById(1)).thenReturn(sampleProveedor());

        mvc.perform(get("/api/v1/proveedores/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.proveedorId").value(1))
                .andExpect(jsonPath("$.data.razonSocial").value("Proveedor SA"));
    }

    @Test
    @DisplayName("GET /api/v1/proveedores/999 -> 404 RECURSO_NO_ENCONTRADO")
    void getById_notFound() throws Exception {
        when(service.getById(999))
                .thenThrow(new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));

        mvc.perform(get("/api/v1/proveedores/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value(404))
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    // ── POST /api/v1/proveedores ───────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/proveedores valido -> 201 con entidad creada")
    void create_valid() throws Exception {
        when(service.create(any(ProveedorRequest.class)))
                .thenReturn(new ProveedorResponse(10, "Nuevo Proveedor", "NPC850101ABC", "601",
                        "nuevo@test.com", "55112233", 15, new BigDecimal("50000.00")));

        mvc.perform(post("/api/v1/proveedores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"razonSocial\":\"Nuevo Proveedor\",\"rfc\":\"NPC850101ABC\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.proveedorId").value(10))
                .andExpect(jsonPath("$.data.razonSocial").value("Nuevo Proveedor"));
    }

    // ── PUT /api/v1/proveedores/{id} ──────────────────────────────

    @Test
    @DisplayName("PUT /api/v1/proveedores/1 -> 200 con entidad actualizada")
    void update_found() throws Exception {
        when(service.update(eq(1), any(ProveedorRequest.class)))
                .thenReturn(new ProveedorResponse(1, "Proveedor Actualizado", "PRO850101ABC", "601",
                        "contacto@test.com", "55998877", 30, new BigDecimal("100000.00")));

        mvc.perform(put("/api/v1/proveedores/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"razonSocial\":\"Proveedor Actualizado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.razonSocial").value("Proveedor Actualizado"));
    }

    // ── DELETE /api/v1/proveedores/{id} ────────────────────────────

    @Test
    @DisplayName("DELETE /api/v1/proveedores/1 -> 204 No Content")
    void deactivate() throws Exception {
        doNothing().when(service).deactivate(1);

        mvc.perform(delete("/api/v1/proveedores/1"))
                .andExpect(status().isNoContent());
    }
}
