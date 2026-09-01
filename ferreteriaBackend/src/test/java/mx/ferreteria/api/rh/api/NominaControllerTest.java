package mx.ferreteria.api.rh.api;

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
import mx.ferreteria.api.common.web.WebMvcTestProps;
import mx.ferreteria.api.rh.dto.RhDtos.NominaResponse;
import mx.ferreteria.api.rh.service.NominaService;

@WebMvcTest(controllers = NominaController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ DbErrorTranslator.class, WebMvcTestProps.class, NominaControllerTest.SliceConfig.class })
@MockBean({ mx.ferreteria.api.common.security.JwtAuthFilter.class,
                mx.ferreteria.api.common.security.RestAuthEntryPoint.class,
                mx.ferreteria.api.common.security.JwtService.class })
class NominaControllerTest {

        @Autowired
        MockMvc mvc;

        @MockBean
        NominaService service;

        @org.springframework.boot.test.context.TestConfiguration
        static class SliceConfig {
                @org.springframework.context.annotation.Bean
                mx.ferreteria.api.common.web.RequestIdProperties requestIdProperties() {
                        return new mx.ferreteria.api.common.web.RequestIdProperties(
                                        mx.ferreteria.api.common.web.RequestIdProperties.Mode.GENERATE);
                }
        }

        private NominaResponse sampleResponse(String estado) {
                return new NominaResponse(
                                1L, 7, "Juan Perez",
                                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 15),
                                new BigDecimal("15.0"), new BigDecimal("6000.00"),
                                new BigDecimal("800.00"), new BigDecimal("5200.00"),
                                estado, "PAGADA".equals(estado)
                                                ? Instant.parse("2026-01-16T10:00:00Z")
                                                : null,
                                1, "Quincena 1");
        }

        @Test
        @DisplayName("GET /api/v1/nomina -> 200 con paginacion")
        void list_returns200() throws Exception {
                when(service.list(any(), eq(null), eq(null), any())).thenReturn(new PageImpl<>(
                                List.of(sampleResponse("PENDIENTE")), PageRequest.of(0, 20), 1));

                mvc.perform(get("/api/v1/nomina"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.length()").value(1))
                                .andExpect(jsonPath("$.data[0].empleado").value("Juan Perez"))
                                .andExpect(jsonPath("$.data[0].netoPagar").value(5200.0));
        }

        @Test
        @DisplayName("GET /api/v1/nomina/1 -> 200")
        void getById_returns200() throws Exception {
                when(service.getById(1L)).thenReturn(sampleResponse("PENDIENTE"));

                mvc.perform(get("/api/v1/nomina/1"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.periodoFin").value("2026-01-15"));
        }

        @Test
        @DisplayName("POST /api/v1/nomina -> 201 crea nomina")
        void create_returns201() throws Exception {
                when(service.create(any())).thenReturn(sampleResponse("PENDIENTE"));

                mvc.perform(post("/api/v1/nomina")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"empleadoId":7,"periodoIni":"2026-01-01","periodoFin":"2026-01-15",
                                                 "diasPagados":15.0,"percepciones":6000.00,"deducciones":800.00,
                                                 "notas":"Quincena 1"}
                                                """))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.estado").value("PENDIENTE"));
        }

        @Test
        @DisplayName("POST /api/v1/nomina/1/pagar -> 200 marca pagada")
        void pagar_returns200() throws Exception {
                when(service.marcarPagada(1L)).thenReturn(sampleResponse("PAGADA"));

                mvc.perform(post("/api/v1/nomina/1/pagar"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.estado").value("PAGADA"))
                                .andExpect(jsonPath("$.data.fechaPago").isNotEmpty());
        }

        @Test
        @DisplayName("POST /api/v1/nomina/1/cancelar -> 200 cancela")
        void cancelar_returns200() throws Exception {
                when(service.cancelar(1L)).thenReturn(sampleResponse("CANCELADA"));

                mvc.perform(post("/api/v1/nomina/1/cancelar"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.estado").value("CANCELADA"));
        }
}