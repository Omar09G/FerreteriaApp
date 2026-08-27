package mx.ferreteria.api.ven.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
import mx.ferreteria.api.ven.dto.VenDtos;
import mx.ferreteria.api.ven.service.RentaService;

@WebMvcTest(controllers = RentaController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({DbErrorTranslator.class, RentaControllerTest.SliceConfig.class})
@MockBean({mx.ferreteria.api.common.security.JwtAuthFilter.class,
           mx.ferreteria.api.common.security.RestAuthEntryPoint.class,
           mx.ferreteria.api.common.security.JwtService.class})
class RentaControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    RentaService service;

    @org.springframework.boot.test.context.TestConfiguration
    static class SliceConfig {
        @org.springframework.context.annotation.Bean
        mx.ferreteria.api.common.web.RequestIdProperties requestIdProperties() {
            return new mx.ferreteria.api.common.web.RequestIdProperties(
                    mx.ferreteria.api.common.web.RequestIdProperties.Mode.GENERATE);
        }
    }

    private VenDtos.RentaResponse sampleResp() {
        return new VenDtos.RentaResponse(
                1L, "R-001", 1L, "Cliente", 1, "Almacen",
                Instant.now(), LocalDate.now().plusDays(7), null,
                new BigDecimal("500.00"), BigDecimal.ZERO,
                "ABIERTA", 1, List.of());
    }

    // ── GET /api/v1/rentas ──────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/rentas -> 200 con array")
    void list_returns200() throws Exception {
        when(service.list(eq(null), any()))
                .thenReturn(new PageImpl<>(List.of(sampleResp()), PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/v1/rentas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    // ── POST /api/v1/rentas ─────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/rentas válido -> 201")
    void create_ok() throws Exception {
        when(service.create(any(VenDtos.RentaRequest.class)))
                .thenReturn(sampleResp());

        mvc.perform(post("/api/v1/rentas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clienteId": 1,
                                  "almacenId": 1,
                                  "fechaDevEsperada": "%s",
                                  "deposito": 500.00,
                                  "detalles": [{"productoId": 1, "cantidad": 1, "costoDia": 50.00}]
                                }""".formatted(LocalDate.now().plusDays(7))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.rentaId").value(1))
                .andExpect(jsonPath("$.data.folio").value("R-001"));
    }
}
