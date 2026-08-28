package mx.ferreteria.api.fis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.fis.dto.FisDtos.FacturaFisRequest;
import mx.ferreteria.api.fis.entity.FacturaFis;
import mx.ferreteria.api.fis.repo.FacturaFisRepository;

@ExtendWith(MockitoExtension.class)
class FacturaFisServiceTest {

    @Mock FacturaFisRepository facturaRepo;

    @InjectMocks
    FacturaFisService service;

    private FacturaFis sampleFactura(Long id) {
        return FacturaFis.builder().facturaId(id).tipo("EMITIDA")
                .serie("A").folio("A-0001").uuid("uuid-123")
                .emisorRfc("XFX010101000").receptorRfc("HEGO8011267N8")
                .subtotal(new BigDecimal("1000.00")).iva(new BigDecimal("160.00"))
                .total(new BigDecimal("1160.00"))
                .fechaTimbrado(Instant.parse("2026-01-15T12:00:00Z"))
                .cfdiXml("<cfdi:Comprobante/>").estado("ACTIVA")
                .usuarioId(1).ventaId(5L).build();
    }

    @Test
    @DisplayName("list: filtra por tipo de CFDI")
    void list_porTipo() {
        Pageable pg = PageRequest.of(0, 20);
        when(facturaRepo.findByTipoOrderByFechaTimbradoDesc("EMITIDA", pg))
                .thenReturn(new PageImpl<>(List.of(sampleFactura(1L)), pg, 1));

        var result = service.list("EMITIDA", null, null, pg);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).tipo()).isEqualTo("EMITIDA");
        assertThat(result.getContent().get(0).total()).isEqualByComparingTo("1160.00");
    }

    @Test
    @DisplayName("getById: factura inexistente -> RecursoNoEncontradoException")
    void getById_notFound() {
        when(facturaRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("getXml: retorna el XML del CFDI")
    void getXml_ok() {
        when(facturaRepo.findById(1L)).thenReturn(Optional.of(sampleFactura(1L)));

        var resp = service.getXml(1L);

        assertThat(resp.folio()).isEqualTo("A-0001");
        assertThat(resp.cfdiXml()).isEqualTo("<cfdi:Comprobante/>");
    }

    @Test
    @DisplayName("create: persiste CFDI con total calculado por BD")
    void create_ok() {
        when(facturaRepo.save(any(FacturaFis.class))).thenReturn(sampleFactura(1L));

        FacturaFisRequest req = new FacturaFisRequest(
                "EMITIDA", "A", "A-0001", "uuid-123",
                "XFX010101000", "HEGO8011267N8",
                new BigDecimal("1000.00"), new BigDecimal("160.00"),
                "<cfdi:Comprobante/>", 5L);

        var resp = service.create(req);

        assertThat(resp.facturaId()).isEqualTo(1L);
        assertThat(resp.estado()).isEqualTo("ACTIVA");
        assertThat(resp.ventaId()).isEqualTo(5L);
    }
}