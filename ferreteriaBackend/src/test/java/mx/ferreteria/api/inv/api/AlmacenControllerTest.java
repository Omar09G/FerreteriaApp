package mx.ferreteria.api.inv.api;

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
import mx.ferreteria.api.inv.dto.InvDtos.AlmacenRequest;
import mx.ferreteria.api.inv.dto.InvDtos.AlmacenResponse;
import mx.ferreteria.api.inv.service.AlmacenService;

@WebMvcTest(controllers = AlmacenController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({DbErrorTranslator.class, WebMvcTestProps.class, AlmacenControllerTest.SliceConfig.class})
@MockBean({mx.ferreteria.api.common.security.JwtAuthFilter.class,
           mx.ferreteria.api.common.security.RestAuthEntryPoint.class,
           mx.ferreteria.api.common.security.JwtService.class})
class AlmacenControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    AlmacenService service;

    @org.springframework.boot.test.context.TestConfiguration
    static class SliceConfig {
        @org.springframework.context.annotation.Bean
        mx.ferreteria.api.common.web.RequestIdProperties requestIdProperties() {
            return new mx.ferreteria.api.common.web.RequestIdProperties(
                    mx.ferreteria.api.common.web.RequestIdProperties.Mode.GENERATE);
        }
    }

    // ── GET /api/v1/almacenes ──────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/almacenes -> 200 con envelope {success, data, meta}")
    void list_returns200WithEnvelope() throws Exception {
        AlmacenResponse r1 = new AlmacenResponse(1, "Almacén Central", null, null, true, true);
        AlmacenResponse r2 = new AlmacenResponse(2, "Almacén Norte", null, null, true, true);
        when(service.list(eq(null), eq(false), any()))
                .thenReturn(new PageImpl<>(List.of(r1, r2), PageRequest.of(0, 20), 2));

        mvc.perform(get("/api/v1/almacenes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.meta.page").value(0))
                .andExpect(jsonPath("$.meta.size").value(20))
                .andExpect(jsonPath("$.meta.totalElements").value(2));
    }

    // ── GET /api/v1/almacenes/{id} ────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/almacenes/1 -> 200 con entidad")
    void getById_found() throws Exception {
        when(service.getById(1))
                .thenReturn(new AlmacenResponse(1, "Almacén Central", null, null, true, true));

        mvc.perform(get("/api/v1/almacenes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.almacenId").value(1))
                .andExpect(jsonPath("$.data.nombre").value("Almacén Central"));
    }

    // ── POST /api/v1/almacenes ────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/almacenes válido -> 201 con entidad creada")
    void create_valid() throws Exception {
        when(service.create(any(AlmacenRequest.class)))
                .thenReturn(new AlmacenResponse(10, "Nuevo", null, null, true, true));

        mvc.perform(post("/api/v1/almacenes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Nuevo\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.almacenId").value(10))
                .andExpect(jsonPath("$.data.nombre").value("Nuevo"));
    }

    @Test
    @DisplayName("POST /api/v1/almacenes nombre blank -> 400 CAMPO_REQUERIDO")
    void create_blankNombre() throws Exception {
        mvc.perform(post("/api/v1/almacenes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value(400))
                .andExpect(jsonPath("$.codigo").value("CAMPO_REQUERIDO"));
    }

    // ── DELETE /api/v1/almacenes/{id} ─────────────────────────────

    @Test
    @DisplayName("DELETE /api/v1/almacenes/1 -> 204 No Content")
    void deactivate() throws Exception {
        doNothing().when(service).deactivate(1);

        mvc.perform(delete("/api/v1/almacenes/1"))
                .andExpect(status().isNoContent());
    }

    // ── PUT /api/v1/almacenes/{id}/estado ──────────────────────────

    @Test
    @DisplayName("PUT /api/v1/almacenes/1/estado -> 200 con activo actualizado")
    void actualizarEstado_ok() throws Exception {
        when(service.actualizarEstado(1, false))
                .thenReturn(new AlmacenResponse(1, "Almacén Central", null, null, true, false));

        mvc.perform(put("/api/v1/almacenes/1/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.activo").value(false));
    }
}
