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

import mx.ferreteria.api.cat.dto.CatDtos.MarcaRequest;
import mx.ferreteria.api.cat.dto.CatDtos.MarcaResponse;
import mx.ferreteria.api.cat.service.MarcaService;
import mx.ferreteria.api.common.error.DbErrorTranslator;
import mx.ferreteria.api.common.web.WebMvcTestProps;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.i18n.ErrorCode;

@WebMvcTest(controllers = MarcaController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({DbErrorTranslator.class, WebMvcTestProps.class, MarcaControllerTest.SliceConfig.class})
@MockBean({mx.ferreteria.api.common.security.JwtAuthFilter.class,
           mx.ferreteria.api.common.security.RestAuthEntryPoint.class,
           mx.ferreteria.api.common.security.JwtService.class})
class MarcaControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    MarcaService service;

    @org.springframework.boot.test.context.TestConfiguration
    static class SliceConfig {
        @org.springframework.context.annotation.Bean
        mx.ferreteria.api.common.web.RequestIdProperties requestIdProperties() {
            return new mx.ferreteria.api.common.web.RequestIdProperties(
                    mx.ferreteria.api.common.web.RequestIdProperties.Mode.GENERATE);
        }
    }

    // ── GET /api/v1/marcas ──────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/marcas -> 200 con envelope {success, data, meta}")
    void list_returns200WithEnvelope() throws Exception {
        MarcaResponse r1 = new MarcaResponse(1, "Acme");
        MarcaResponse r2 = new MarcaResponse(2, "Bosch");
        when(service.list(eq(null), any()))
                .thenReturn(new PageImpl<>(List.of(r1, r2), PageRequest.of(0, 20), 2));

        mvc.perform(get("/api/v1/marcas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].nombre").value("Acme"))
                .andExpect(jsonPath("$.meta.page").value(0))
                .andExpect(jsonPath("$.meta.size").value(20))
                .andExpect(jsonPath("$.meta.totalElements").value(2));
    }

    @Test
    @DisplayName("GET /api/v1/marcas?q=Acme -> 200 con resultados filtrados")
    void list_withQuery_returns200() throws Exception {
        MarcaResponse r = new MarcaResponse(1, "Acme Corp");
        when(service.list(eq("Acme"), any()))
                .thenReturn(new PageImpl<>(List.of(r), PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/v1/marcas").param("q", "Acme"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].nombre").value("Acme Corp"));
    }

    // ── GET /api/v1/marcas/{id} ────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/marcas/1 -> 200 con entidad")
    void getById_found() throws Exception {
        when(service.getById(1)).thenReturn(new MarcaResponse(1, "Acme"));

        mvc.perform(get("/api/v1/marcas/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.marcaId").value(1))
                .andExpect(jsonPath("$.data.nombre").value("Acme"));
    }

    @Test
    @DisplayName("GET /api/v1/marcas/999 -> 404 RECURSO_NO_ENCONTRADO")
    void getById_notFound() throws Exception {
        when(service.getById(999))
                .thenThrow(new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));

        mvc.perform(get("/api/v1/marcas/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value(404))
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    // ── POST /api/v1/marcas ─────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/marcas valido -> 201 con entidad creada")
    void create_valid() throws Exception {
        when(service.create(any(MarcaRequest.class)))
                .thenReturn(new MarcaResponse(10, "NuevaMarca"));

        mvc.perform(post("/api/v1/marcas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"NuevaMarca\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.marcaId").value(10))
                .andExpect(jsonPath("$.data.nombre").value("NuevaMarca"));
    }

    @Test
    @DisplayName("POST /api/v1/marcas nombre blank -> 400 CAMPO_REQUERIDO")
    void create_blankNombre() throws Exception {
        mvc.perform(post("/api/v1/marcas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value(400))
                .andExpect(jsonPath("$.codigo").value("CAMPO_REQUERIDO"));
    }

    // ── PUT /api/v1/marcas/{id} ────────────────────────────────────

    @Test
    @DisplayName("PUT /api/v1/marcas/1 -> 200 con entidad actualizada")
    void update_found() throws Exception {
        when(service.update(eq(1), any(MarcaRequest.class)))
                .thenReturn(new MarcaResponse(1, "Actualizada"));

        mvc.perform(put("/api/v1/marcas/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Actualizada\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nombre").value("Actualizada"));
    }

    // ── DELETE /api/v1/marcas/{id} ─────────────────────────────────

    @Test
    @DisplayName("DELETE /api/v1/marcas/1 -> 204 No Content")
    void deactivate() throws Exception {
        doNothing().when(service).deactivate(1);

        mvc.perform(delete("/api/v1/marcas/1"))
                .andExpect(status().isNoContent());
    }
}
