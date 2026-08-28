package mx.ferreteria.api.fin.api;

import static org.mockito.ArgumentMatchers.any;
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
import mx.ferreteria.api.fin.dto.FinDtos.CorteCajaResponse;
import mx.ferreteria.api.fin.service.CajaService;

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
    CajaService service;

    @org.springframework.boot.test.context.TestConfiguration
    static class SliceConfig {
        @org.springframework.context.annotation.Bean
        mx.ferreteria.api.common.web.RequestIdProperties requestIdProperties() {
            return new mx.ferreteria.api.common.web.RequestIdProperties(
                    mx.ferreteria.api.common.web.RequestIdProperties.Mode.GENERATE);
        }
    }

    @Test
    @DisplayName("GET /api/v1/cortes-caja -> 200 pagina de cortes")
    void list_returns200() throws Exception {
        CorteCajaResponse r1 = new CorteCajaResponse(
                1L, 1L, 1, "Caja Central", 1, "Almacen Central",
                1, 1, LocalDate.now(), Instant.now(), Instant.now(), 10L,
                new BigDecimal("1000.00"), new BigDecimal("160.00"), BigDecimal.ZERO,
                new BigDecimal("1160.00"), new BigDecimal("600.00"),
                new BigDecimal("400.00"), new BigDecimal("40.00"),
                new BigDecimal("5000.00"),
                new BigDecimal("1160.00"), BigDecimal.ZERO,
                new BigDecimal("6160.00"), new BigDecimal("6160.00"),
                BigDecimal.ZERO, "CUADRADO",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "{}", "{}", "{}", null);
        when(service.listCortes(any()))
                .thenReturn(new PageImpl<>(List.of(r1), PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/v1/cortes-caja"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].resultadoCaja").value("CUADRADO"))
                .andExpect(jsonPath("$.meta.totalElements").value(1));
    }
}