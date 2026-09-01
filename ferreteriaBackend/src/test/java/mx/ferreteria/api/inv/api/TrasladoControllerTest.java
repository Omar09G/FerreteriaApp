package mx.ferreteria.api.inv.api;

import static org.mockito.ArgumentMatchers.any;
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
import mx.ferreteria.api.common.web.WebMvcTestProps;
import mx.ferreteria.api.inv.dto.InvDtos.TrasladoDetalleResponse;
import mx.ferreteria.api.inv.dto.InvDtos.TrasladoRequest;
import mx.ferreteria.api.inv.dto.InvDtos.TrasladoResponse;
import mx.ferreteria.api.inv.service.TrasladoService;

@WebMvcTest(controllers = TrasladoController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({DbErrorTranslator.class, WebMvcTestProps.class, TrasladoControllerTest.SliceConfig.class})
@MockBean({mx.ferreteria.api.common.security.JwtAuthFilter.class,
           mx.ferreteria.api.common.security.RestAuthEntryPoint.class,
           mx.ferreteria.api.common.security.JwtService.class})
class TrasladoControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    TrasladoService service;

    @org.springframework.boot.test.context.TestConfiguration
    static class SliceConfig {
        @org.springframework.context.annotation.Bean
        mx.ferreteria.api.common.web.RequestIdProperties requestIdProperties() {
            return new mx.ferreteria.api.common.web.RequestIdProperties(
                    mx.ferreteria.api.common.web.RequestIdProperties.Mode.GENERATE);
        }
    }

    // ── GET /api/v1/traslados ─────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/traslados -> 200 con envelope")
    void list_returns200() throws Exception {
        TrasladoResponse r = new TrasladoResponse(
                1L, "TR-100", 1, "Origen", 2, "Destino",
                "PENDIENTE", 1, null,
                List.of(new TrasladoDetalleResponse(1L, "Taladro", new BigDecimal("2"))));
        when(service.list(any()))
                .thenReturn(new PageImpl<>(List.of(r), PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/v1/traslados"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.meta.totalElements").value(1));
    }

    // ── GET /api/v1/traslados/{id} ───────────────────────────────

    @Test
    @DisplayName("GET /api/v1/traslados/1 -> 200 con entidad")
    void getById_found() throws Exception {
        TrasladoResponse r = new TrasladoResponse(
                1L, "TR-100", 1, "Origen", 2, "Destino",
                "PENDIENTE", 1, null,
                List.of(new TrasladoDetalleResponse(1L, "Taladro", new BigDecimal("2"))));
        when(service.getById(1L)).thenReturn(r);

        mvc.perform(get("/api/v1/traslados/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.trasladoId").value(1))
                .andExpect(jsonPath("$.data.folio").value("TR-100"))
                .andExpect(jsonPath("$.data.detalles.length()").value(1));
    }

    // ── POST /api/v1/traslados ────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/traslados válido -> 201 con entidad creada")
    void create_valid() throws Exception {
        TrasladoResponse resp = new TrasladoResponse(
                1L, "TR-200", 1, "Origen", 2, "Destino",
                "PENDIENTE", 1, null, List.of());
        when(service.create(any(TrasladoRequest.class)))
                .thenReturn(resp);

        mvc.perform(post("/api/v1/traslados")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"almacenOrigen\":1,\"almacenDestino\":2,\"detalles\":[{\"productoId\":1,\"cantidad\":5}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.trasladoId").value(1))
                .andExpect(jsonPath("$.data.folio").value("TR-200"));
    }
}
