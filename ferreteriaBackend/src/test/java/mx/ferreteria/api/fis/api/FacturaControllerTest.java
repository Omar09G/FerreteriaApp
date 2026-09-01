package mx.ferreteria.api.fis.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
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
import mx.ferreteria.api.fis.dto.FisDtos.FacturaFisResponse;
import mx.ferreteria.api.fis.dto.FisDtos.FacturaXmlResponse;
import mx.ferreteria.api.fis.service.FacturaFisService;

@WebMvcTest(controllers = FacturaController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({DbErrorTranslator.class, WebMvcTestProps.class, FacturaControllerTest.SliceConfig.class})
@MockBean({mx.ferreteria.api.common.security.JwtAuthFilter.class,
           mx.ferreteria.api.common.security.RestAuthEntryPoint.class,
           mx.ferreteria.api.common.security.JwtService.class})
class FacturaControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    FacturaFisService service;

    @org.springframework.boot.test.context.TestConfiguration
    static class SliceConfig {
        @org.springframework.context.annotation.Bean
        mx.ferreteria.api.common.web.RequestIdProperties requestIdProperties() {
            return new mx.ferreteria.api.common.web.RequestIdProperties(
                    mx.ferreteria.api.common.web.RequestIdProperties.Mode.GENERATE);
        }
    }

    private FacturaFisResponse sampleResponse() {
        return new FacturaFisResponse(
                1L, "EMITIDA", "A", "A-0001", "uuid-123",
                "XFX010101000", "HEGO8011267N8",
                new BigDecimal("1000.00"), new BigDecimal("160.00"),
                new BigDecimal("1160.00"),
                Instant.parse("2026-01-15T12:00:00Z"),
                "ACTIVA", 5L, 1, Instant.parse("2026-01-15T12:00:00Z"));
    }

    @Test
    @DisplayName("GET /api/v1/facturas -> 200 con paginacion")
    void list_returns200() throws Exception {
        when(service.list(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(sampleResponse()),
                        PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/v1/facturas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].folio").value("A-0001"))
                .andExpect(jsonPath("$.data[0].total").value(1160.0));
    }

    @Test
    @DisplayName("GET /api/v1/facturas/1 -> 200")
    void getById_returns200() throws Exception {
        when(service.getById(1L)).thenReturn(sampleResponse());

        mvc.perform(get("/api/v1/facturas/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uuid").value("uuid-123"))
                .andExpect(jsonPath("$.data.estado").value("ACTIVA"));
    }

    @Test
    @DisplayName("GET /api/v1/facturas/1/xml -> 200 con cfdi_xml")
    void getXml_returns200() throws Exception {
        when(service.getXml(1L)).thenReturn(
                new FacturaXmlResponse(1L, "A-0001", "uuid-123", "EMITIDA",
                        "<cfdi:Comprobante/>"));

        mvc.perform(get("/api/v1/facturas/1/xml"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cfdiXml").value("<cfdi:Comprobante/>"));
    }

    @Test
    @DisplayName("POST /api/v1/facturas -> 201 persiste CFDI")
    void create_returns201() throws Exception {
        when(service.create(any())).thenReturn(sampleResponse());

        mvc.perform(post("/api/v1/facturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"EMITIDA","serie":"A","folio":"A-0001","uuid":"uuid-123",
                                 "emisorRfc":"XFX010101000","receptorRfc":"HEGO8011267N8",
                                 "subtotal":1000.00,"iva":160.00,
                                 "cfdiXml":"<cfdi:Comprobante/>","ventaId":5}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.folio").value("A-0001"))
                .andExpect(jsonPath("$.data.estado").value("ACTIVA"));
    }
}