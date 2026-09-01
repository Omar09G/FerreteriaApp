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

import mx.ferreteria.api.cat.dto.CatDtos.UnidadMedidaRequest;
import mx.ferreteria.api.cat.dto.CatDtos.UnidadMedidaResponse;
import mx.ferreteria.api.cat.service.UnidadMedidaService;
import mx.ferreteria.api.common.error.DbErrorTranslator;
import mx.ferreteria.api.common.web.WebMvcTestProps;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.i18n.ErrorCode;

@WebMvcTest(controllers = UnidadMedidaController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({DbErrorTranslator.class, WebMvcTestProps.class, UnidadMedidaControllerTest.SliceConfig.class})
@MockBean({mx.ferreteria.api.common.security.JwtAuthFilter.class,
           mx.ferreteria.api.common.security.RestAuthEntryPoint.class,
           mx.ferreteria.api.common.security.JwtService.class})
class UnidadMedidaControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    UnidadMedidaService service;

    @org.springframework.boot.test.context.TestConfiguration
    static class SliceConfig {
        @org.springframework.context.annotation.Bean
        mx.ferreteria.api.common.web.RequestIdProperties requestIdProperties() {
            return new mx.ferreteria.api.common.web.RequestIdProperties(
                    mx.ferreteria.api.common.web.RequestIdProperties.Mode.GENERATE);
        }
    }

    // ── GET /api/v1/unidades-medida ────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/unidades-medida -> 200 con envelope {success, data, meta}")
    void list_returns200WithEnvelope() throws Exception {
        UnidadMedidaResponse r1 = new UnidadMedidaResponse(1, "PZA", "Pieza", false);
        UnidadMedidaResponse r2 = new UnidadMedidaResponse(2, "KG", "Kilogramo", true);
        when(service.list(eq(null), any()))
                .thenReturn(new PageImpl<>(List.of(r1, r2), PageRequest.of(0, 20), 2));

        mvc.perform(get("/api/v1/unidades-medida"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].clave").value("PZA"))
                .andExpect(jsonPath("$.data[0].nombre").value("Pieza"))
                .andExpect(jsonPath("$.meta.page").value(0))
                .andExpect(jsonPath("$.meta.size").value(20))
                .andExpect(jsonPath("$.meta.totalElements").value(2));
    }

    // ── GET /api/v1/unidades-medida/{id} ──────────────────────────

    @Test
    @DisplayName("GET /api/v1/unidades-medida/1 -> 200 con entidad")
    void getById_found() throws Exception {
        when(service.getById(1)).thenReturn(new UnidadMedidaResponse(1, "PZA", "Pieza", false));

        mvc.perform(get("/api/v1/unidades-medida/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.unidadId").value(1))
                .andExpect(jsonPath("$.data.clave").value("PZA"));
    }

    @Test
    @DisplayName("GET /api/v1/unidades-medida/999 -> 404 RECURSO_NO_ENCONTRADO")
    void getById_notFound() throws Exception {
        when(service.getById(999))
                .thenThrow(new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));

        mvc.perform(get("/api/v1/unidades-medida/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value(404))
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    // ── POST /api/v1/unidades-medida ───────────────────────────────

    @Test
    @DisplayName("POST /api/v1/unidades-medida valido -> 201 con entidad creada")
    void create_valid() throws Exception {
        when(service.create(any(UnidadMedidaRequest.class)))
                .thenReturn(new UnidadMedidaResponse(10, "LT", "Litro", true));

        mvc.perform(post("/api/v1/unidades-medida")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clave\":\"LT\",\"nombre\":\"Litro\",\"permiteFraccion\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.unidadId").value(10))
                .andExpect(jsonPath("$.data.clave").value("LT"));
    }

    // ── PUT /api/v1/unidades-medida/{id} ──────────────────────────

    @Test
    @DisplayName("PUT /api/v1/unidades-medida/1 -> 200 con entidad actualizada")
    void update_found() throws Exception {
        when(service.update(eq(1), any(UnidadMedidaRequest.class)))
                .thenReturn(new UnidadMedidaResponse(1, "M2", "Metro Cuadrado", false));

        mvc.perform(put("/api/v1/unidades-medida/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clave\":\"M2\",\"nombre\":\"Metro Cuadrado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.clave").value("M2"));
    }

    // ── DELETE /api/v1/unidades-medida/{id} ────────────────────────

    @Test
    @DisplayName("DELETE /api/v1/unidades-medida/1 -> 204 No Content")
    void deactivate() throws Exception {
        doNothing().when(service).deactivate(1);

        mvc.perform(delete("/api/v1/unidades-medida/1"))
                .andExpect(status().isNoContent());
    }
}
