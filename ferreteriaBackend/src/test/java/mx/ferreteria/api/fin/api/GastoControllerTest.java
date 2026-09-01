package mx.ferreteria.api.fin.api;

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
import mx.ferreteria.api.fin.dto.FinDtos.GastoResponse;
import mx.ferreteria.api.fin.dto.FinDtos.IngresoOtroResponse;
import mx.ferreteria.api.fin.service.GastoService;

@WebMvcTest(controllers = GastoController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ DbErrorTranslator.class, WebMvcTestProps.class, GastoControllerTest.SliceConfig.class })
@MockBean({ mx.ferreteria.api.common.security.JwtAuthFilter.class,
                mx.ferreteria.api.common.security.RestAuthEntryPoint.class,
                mx.ferreteria.api.common.security.JwtService.class })
class GastoControllerTest {

        @Autowired
        MockMvc mvc;

        @MockBean
        GastoService service;

        @org.springframework.boot.test.context.TestConfiguration
        static class SliceConfig {
                @org.springframework.context.annotation.Bean
                mx.ferreteria.api.common.web.RequestIdProperties requestIdProperties() {
                        return new mx.ferreteria.api.common.web.RequestIdProperties(
                                        mx.ferreteria.api.common.web.RequestIdProperties.Mode.GENERATE);
                }
        }

        @Test
        @DisplayName("GET /api/v1/gastos -> 200")
        void listGastos_returns200() throws Exception {
                GastoResponse r1 = new GastoResponse(
                                1L, "G-001", 1, null, "Renta local",
                                new BigDecimal("15000.00"), LocalDate.now(),
                                1, null, null, null, null, 1, Instant.now());
                when(service.listGastos(eq(null), eq(null), any()))
                                .thenReturn(new PageImpl<>(List.of(r1), PageRequest.of(0, 20), 1));

                mvc.perform(get("/api/v1/gastos"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data[0].descripcion").value("Renta local"))
                                .andExpect(jsonPath("$.meta.totalElements").value(1));
        }

        @Test
        @DisplayName("POST /api/v1/gastos -> 201")
        void createGasto_ok() throws Exception {
                GastoResponse resp = new GastoResponse(
                                1L, "G-001", 1, null, "Renta local",
                                new BigDecimal("15000.00"), LocalDate.now(),
                                1, null, null, null, null, 1, Instant.now());
                when(service.createGasto(any())).thenReturn(resp);

                mvc.perform(post("/api/v1/gastos")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"tipoGastoId\":1,\"descripcion\":\"Renta local\",\"monto\":15000,\"formaPagoId\":1}"))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.gastoId").value(1))
                                .andExpect(jsonPath("$.data.folio").value("G-001"));
        }

        @Test
        @DisplayName("GET /api/v1/ingresos-otros -> 200")
        void listIngresos_returns200() throws Exception {
                IngresoOtroResponse r1 = new IngresoOtroResponse(
                                1L, "Venta de chatarra", new BigDecimal("250.00"),
                                LocalDate.now(), 1, null, null, 1, Instant.now());
                when(service.listIngresos(eq(null), eq(null), any()))
                                .thenReturn(new PageImpl<>(List.of(r1), PageRequest.of(0, 20), 1));

                mvc.perform(get("/api/v1/ingresos-otros"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data[0].concepto").value("Venta de chatarra"));
        }

        @Test
        @DisplayName("POST /api/v1/ingresos-otros -> 201")
        void createIngreso_ok() throws Exception {
                IngresoOtroResponse resp = new IngresoOtroResponse(
                                1L, "Venta de chatarra", new BigDecimal("250.00"),
                                LocalDate.now(), 1, null, null, 1, Instant.now());
                when(service.createIngreso(any())).thenReturn(resp);

                mvc.perform(post("/api/v1/ingresos-otros")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"concepto\":\"Venta de chatarra\",\"monto\":250,\"formaPagoId\":1}"))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.ingresoOtroId").value(1));
        }
}