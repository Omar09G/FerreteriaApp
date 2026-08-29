package mx.ferreteria.api.seg.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.common.web.EnvelopeAdvice;
import mx.ferreteria.api.seg.dto.AuditoriaDtos.AuditoriaResponse;
import mx.ferreteria.api.seg.dto.AuditoriaDtos.TablaAuditoriaResponse;
import mx.ferreteria.api.seg.service.AuditoriaService;

class AuditoriaControllerTest {

    @Mock AuditoriaService service;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        mvc = MockMvcBuilders
                .standaloneSetup(new AuditoriaController(service))
                .setControllerAdvice(new EnvelopeAdvice())
                .build();
    }

    @Test
    @DisplayName("GET /auditoria: pasa filtros al service y serializa Page")
    void listarFiltros() throws Exception {
        var row = new AuditoriaResponse(
                1L, "ven", "promociones", 7L, "INSERT",
                null, "{\"nombre\":\"Promo\"}", 1, "admin", Instant.parse("2026-08-29T12:00:00Z"));
        Page<AuditoriaResponse> page = new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1);
        when(service.buscar(eq("ven"), eq("promociones"), eq("INSERT"), eq("admin"),
                eq(7L), any(), any(), eq("Promo"), any(Pageable.class)))
                .thenReturn(page);

        mvc.perform(get("/api/v1/auditoria")
                        .param("esquema", "ven")
                        .param("tabla", "promociones")
                        .param("accion", "INSERT")
                        .param("usuario", "admin")
                        .param("registroId", "7")
                        .param("fechaInicio", "2026-08-01")
                        .param("fechaFin", "2026-08-30")
                        .param("texto", "Promo")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].auditoriaId").value(1))
                .andExpect(jsonPath("$.data[0].esquema").value("ven"))
                .andExpect(jsonPath("$.data[0].tabla").value("promociones"))
                .andExpect(jsonPath("$.data[0].accion").value("INSERT"))
                .andExpect(jsonPath("$.data[0].usuario").value("admin"))
                .andExpect(jsonPath("$.meta.totalElements").value(1));

        verify(service).buscar(eq("ven"), eq("promociones"), eq("INSERT"), eq("admin"),
                eq(7L), any(), any(), eq("Promo"), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /auditoria: defaults y respuesta vacía")
    void listarVacio() throws Exception {
        Page<AuditoriaResponse> empty = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(service.buscar(any(), any(), any(), any(), any(),
                any(), any(), any(), any(Pageable.class)))
                .thenReturn(empty);

        mvc.perform(get("/api/v1/auditoria"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0))
                .andExpect(jsonPath("$.meta.totalElements").value(0));
    }

    @Test
    @DisplayName("GET /auditoria/tablas: devuelve esquemas distintos")
    void tablas() throws Exception {
        when(service.tablas()).thenReturn(List.of(
                new TablaAuditoriaResponse("ven", "promociones"),
                new TablaAuditoriaResponse("inv", "productos")));

        mvc.perform(get("/api/v1/auditoria/tablas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].esquema").value("ven"))
                .andExpect(jsonPath("$.data[0].tabla").value("promociones"))
                .andExpect(jsonPath("$.data[1].tabla").value("productos"));
    }

    @Test
    @DisplayName("ErrorCode.RECURSO_NO_ENCONTRADO sigue en el catálogo")
    void errorCode() {
        org.junit.jupiter.api.Assertions.assertEquals("error.negocio.recurso-no-encontrado",
                ErrorCode.RECURSO_NO_ENCONTRADO.key());
    }
}