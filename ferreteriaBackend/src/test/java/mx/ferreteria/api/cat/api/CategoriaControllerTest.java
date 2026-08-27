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

import mx.ferreteria.api.cat.dto.CatDtos.CategoriaRequest;
import mx.ferreteria.api.cat.dto.CatDtos.CategoriaResponse;
import mx.ferreteria.api.cat.service.CategoriaService;
import mx.ferreteria.api.common.error.DbErrorTranslator;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.i18n.ErrorCode;

@WebMvcTest(controllers = CategoriaController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({DbErrorTranslator.class, CategoriaControllerTest.SliceConfig.class})
@MockBean({mx.ferreteria.api.common.security.JwtAuthFilter.class,
           mx.ferreteria.api.common.security.RestAuthEntryPoint.class,
           mx.ferreteria.api.common.security.JwtService.class})
class CategoriaControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    CategoriaService service;

    @org.springframework.boot.test.context.TestConfiguration
    static class SliceConfig {
        @org.springframework.context.annotation.Bean
        mx.ferreteria.api.common.web.RequestIdProperties requestIdProperties() {
            return new mx.ferreteria.api.common.web.RequestIdProperties(
                    mx.ferreteria.api.common.web.RequestIdProperties.Mode.GENERATE);
        }
    }

    // ── GET /api/v1/categorias ─────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/categorias -> 200 con envelope {success, data, meta}")
    void list_returns200WithEnvelope() throws Exception {
        CategoriaResponse r1 = new CategoriaResponse(1, "Ferreteria", null, "/Ferreteria", (short) 0, List.of());
        CategoriaResponse r2 = new CategoriaResponse(2, "Herramientas", null, "/Herramientas", (short) 0, List.of());
        when(service.list(eq(null), any()))
                .thenReturn(new PageImpl<>(List.of(r1, r2), PageRequest.of(0, 20), 2));

        mvc.perform(get("/api/v1/categorias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].nombre").value("Ferreteria"))
                .andExpect(jsonPath("$.meta.page").value(0))
                .andExpect(jsonPath("$.meta.size").value(20))
                .andExpect(jsonPath("$.meta.totalElements").value(2));
    }

    // ── GET /api/v1/categorias/arbol ───────────────────────────────

    @Test
    @DisplayName("GET /api/v1/categorias/arbol -> 200 con lista de arbol")
    void listTree_returns200() throws Exception {
        CategoriaResponse child = new CategoriaResponse(2, "Martillos", 1, "/Ferreteria/Martillos", (short) 1, List.of());
        CategoriaResponse root = new CategoriaResponse(1, "Ferreteria", null, "/Ferreteria", (short) 0, List.of(child));
        when(service.listTree()).thenReturn(List.of(root));

        mvc.perform(get("/api/v1/categorias/arbol"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].nombre").value("Ferreteria"))
                .andExpect(jsonPath("$.data[0].hijos.length()").value(1))
                .andExpect(jsonPath("$.data[0].hijos[0].nombre").value("Martillos"));
    }

    // ── GET /api/v1/categorias/{id} ───────────────────────────────

    @Test
    @DisplayName("GET /api/v1/categorias/1 -> 200 con entidad")
    void getById_found() throws Exception {
        when(service.getById(1))
                .thenReturn(new CategoriaResponse(1, "Ferreteria", null, "/Ferreteria", (short) 0, List.of()));

        mvc.perform(get("/api/v1/categorias/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.categoriaId").value(1))
                .andExpect(jsonPath("$.data.nombre").value("Ferreteria"));
    }

    @Test
    @DisplayName("GET /api/v1/categorias/999 -> 404 RECURSO_NO_ENCONTRADO")
    void getById_notFound() throws Exception {
        when(service.getById(999))
                .thenThrow(new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));

        mvc.perform(get("/api/v1/categorias/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value(404))
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    // ── POST /api/v1/categorias ────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/categorias valido -> 201 con entidad creada")
    void create_valid() throws Exception {
        when(service.create(any(CategoriaRequest.class)))
                .thenReturn(new CategoriaResponse(10, "Nueva Categoria", null, "/Nueva Categoria", (short) 0, List.of()));

        mvc.perform(post("/api/v1/categorias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Nueva Categoria\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.categoriaId").value(10))
                .andExpect(jsonPath("$.data.nombre").value("Nueva Categoria"));
    }

    // ── PUT /api/v1/categorias/{id} ───────────────────────────────

    @Test
    @DisplayName("PUT /api/v1/categorias/1 -> 200 con entidad actualizada")
    void update_found() throws Exception {
        when(service.update(eq(1), any(CategoriaRequest.class)))
                .thenReturn(new CategoriaResponse(1, "Actualizada", null, "/Actualizada", (short) 0, List.of()));

        mvc.perform(put("/api/v1/categorias/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Actualizada\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nombre").value("Actualizada"));
    }

    // ── DELETE /api/v1/categorias/{id} ─────────────────────────────

    @Test
    @DisplayName("DELETE /api/v1/categorias/1 -> 204 No Content")
    void deactivate() throws Exception {
        doNothing().when(service).deactivate(1);

        mvc.perform(delete("/api/v1/categorias/1"))
                .andExpect(status().isNoContent());
    }
}
