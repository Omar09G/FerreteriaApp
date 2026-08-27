package mx.ferreteria.api.fin.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
import mx.ferreteria.api.fin.dto.FinDtos;

@WebMvcTest(controllers = CorteController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({DbErrorTranslator.class, CorteControllerTest.SliceConfig.class})
@MockBean({mx.ferreteria.api.common.security.JwtAuthFilter.class,
           mx.ferreteria.api.common.security.RestAuthEntryPoint.class,
           mx.ferreteria.api.common.security.JwtService.class})
class CorteControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    mx.ferreteria.api.fin.service.CajaService service;

    @org.springframework.boot.test.context.TestConfiguration
    static class SliceConfig {
        @org.springframework.context.annotation.Bean
        mx.ferreteria.api.common.web.RequestIdProperties requestIdProperties() {
            return new mx.ferreteria.api.common.web.RequestIdProperties(
                    mx.ferreteria.api.common.web.RequestIdProperties.Mode.GENERATE);
        }
    }

    // ── GET /api/v1/cortes-caja ────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/cortes-caja -> 200 con envelope {success, data, meta}")
    void list_returns200() throws Exception {
        var r1 = new FinDtos.CorteCajaResponse(
                1L, 1L, 1, "Caja Principal", 1, "Almacén Central",
                10, 10, LocalDate.now(), null, null, 5L,
                new BigDecimal("10000"), new BigDecimal("1600"), BigDecimal.ZERO,
                new BigDecimal("10000"), new BigDecimal("7000"), new BigDecimal("3000"),
                new BigDecimal("30"), new BigDecimal("5000"),
                new BigDecimal("10000"), BigDecimal.ZERO,
                new BigDecimal("10000"), new BigDecimal("10000"), BigDecimal.ZERO,
                "CUADRADO", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null, null, null, null);
        var r2 = new FinDtos.CorteCajaResponse(
                2L, 2L, 2, "Caja Norte", 2, "Almacén Norte",
                11, 11, LocalDate.now(), null, null, 3L,
                new BigDecimal("5000"), new BigDecimal("800"), BigDecimal.ZERO,
                new BigDecimal("5000"), new BigDecimal("3500"), new BigDecimal("1500"),
                new BigDecimal("30"), new BigDecimal("2000"),
                new BigDecimal("5000"), BigDecimal.ZERO,
                new BigDecimal("5000"), new BigDecimal("5000"), BigDecimal.ZERO,
                "CUADRADO", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null, null, null, null);
        when(service.listCortes(PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(r1, r2), PageRequest.of(0, 20), 2));

        mvc.perform(get("/api/v1/cortes-caja"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.meta.page").value(0));
    }
}
