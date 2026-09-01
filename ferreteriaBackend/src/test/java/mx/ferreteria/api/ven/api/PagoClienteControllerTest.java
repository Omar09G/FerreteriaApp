package mx.ferreteria.api.ven.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import mx.ferreteria.api.common.error.DbErrorTranslator;
import mx.ferreteria.api.common.web.WebMvcTestProps;
import mx.ferreteria.api.ven.dto.VenDtos;
import mx.ferreteria.api.ven.service.PagoService;

@WebMvcTest(controllers = PagoClienteController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({DbErrorTranslator.class, WebMvcTestProps.class, PagoClienteControllerTest.SliceConfig.class})
@MockBean({mx.ferreteria.api.common.security.JwtAuthFilter.class,
           mx.ferreteria.api.common.security.RestAuthEntryPoint.class,
           mx.ferreteria.api.common.security.JwtService.class})
class PagoClienteControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    PagoService service;

    @org.springframework.boot.test.context.TestConfiguration
    static class SliceConfig {
        @org.springframework.context.annotation.Bean
        mx.ferreteria.api.common.web.RequestIdProperties requestIdProperties() {
            return new mx.ferreteria.api.common.web.RequestIdProperties(
                    mx.ferreteria.api.common.web.RequestIdProperties.Mode.GENERATE);
        }
    }

    // ── POST /api/v1/pagos-cliente ──────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/pagos-cliente válido -> 201 con pagoId")
    void create_ok() throws Exception {
        when(service.create(any(VenDtos.PagoClienteRequest.class)))
                .thenReturn(new VenDtos.PagoResponse(
                        1L, 1, "REF-001", new BigDecimal("100.00"), Instant.now()));

        mvc.perform(post("/api/v1/pagos-cliente")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cuentaCobrarId": 1,
                                  "formaPagoId": 1,
                                  "monto": 100.00,
                                  "referencia": "REF-001"
                                }"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.pagoClienteId").value(1))
                .andExpect(jsonPath("$.data.referencia").value("REF-001"))
                .andExpect(jsonPath("$.data.monto").value(100.00));
    }
}
