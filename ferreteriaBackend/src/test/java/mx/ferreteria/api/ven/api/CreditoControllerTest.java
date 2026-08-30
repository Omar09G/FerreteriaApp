package mx.ferreteria.api.ven.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MockMvc;

import mx.ferreteria.api.common.error.DbErrorTranslator;
import mx.ferreteria.api.ven.dto.VenDtos;
import mx.ferreteria.api.ven.service.CreditoService;

@WebMvcTest(controllers = CreditoController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({DbErrorTranslator.class, CreditoControllerTest.SliceConfig.class})
@MockBean({mx.ferreteria.api.common.security.JwtAuthFilter.class,
           mx.ferreteria.api.common.security.RestAuthEntryPoint.class,
           mx.ferreteria.api.common.security.JwtService.class})
class CreditoControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    CreditoService service;

    @org.springframework.boot.test.context.TestConfiguration
    static class SliceConfig {
        @org.springframework.context.annotation.Bean
        mx.ferreteria.api.common.web.RequestIdProperties requestIdProperties() {
            return new mx.ferreteria.api.common.web.RequestIdProperties(
                    mx.ferreteria.api.common.web.RequestIdProperties.Mode.GENERATE);
        }
    }

    private VenDtos.CuentaCobrarResponse sampleResp() {
        return new VenDtos.CuentaCobrarResponse(
                1L, 1L, "V-001", 1L, "Cliente",
                new BigDecimal("116.00"), BigDecimal.ZERO,
                new BigDecimal("116.00"), LocalDate.now().plusDays(15),
                "VIGENTE", Instant.now(), List.of());
    }

    // ── GET /api/v1/creditos/cobranza ───────────────────────────────

    @Test
    @DisplayName("GET /api/v1/creditos/cobranza -> 200 con array")
    void listCuentas_returns200() throws Exception {
        when(service.listCuentas(eq(null), eq(null), eq(null), any()))
                .thenReturn(new PageImpl<>(List.of(sampleResp()), PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/v1/creditos/cobranza"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    // ── GET /api/v1/creditos/{clienteId} ────────────────────────────

    @Test
    @DisplayName("GET /api/v1/creditos/1 -> 200 con array del cliente")
    void listByCliente_returns200() throws Exception {
        when(service.listCuentasByCliente(eq(1L), eq(null), eq(null), eq(null), any()))
                .thenReturn(new PageImpl<>(List.of(sampleResp()), PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/v1/creditos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].clienteId").value(1));
    }
}
