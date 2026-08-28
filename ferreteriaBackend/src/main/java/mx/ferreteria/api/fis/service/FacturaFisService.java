package mx.ferreteria.api.fis.service;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.fis.dto.FisDtos;
import mx.ferreteria.api.fis.entity.FacturaFis;
import mx.ferreteria.api.fis.repo.FacturaFisRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class FacturaFisService {

    private final FacturaFisRepository facturaRepo;

    @Transactional(readOnly = true)
    public Page<FisDtos.FacturaFisResponse> list(String tipo, Instant desde, Instant hasta, Pageable pageable) {
        Page<FacturaFis> page;
        if (tipo != null && fromDesdeHasta(desde, hasta)) {
            page = facturaRepo.findByTipoAndFechaTimbradoBetweenOrderByFechaTimbradoDesc(tipo, desde, hasta, pageable);
        } else if (tipo != null) {
            page = facturaRepo.findByTipoOrderByFechaTimbradoDesc(tipo, pageable);
        } else if (fromDesdeHasta(desde, hasta)) {
            page = facturaRepo.findByFechaTimbradoBetweenOrderByFechaTimbradoDesc(desde, hasta, pageable);
        } else {
            page = facturaRepo.findAllByOrderByFechaTimbradoDesc(pageable);
        }
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public FisDtos.FacturaFisResponse getById(Long id) {
        return toResponse(requerida(id));
    }

    @Transactional(readOnly = true)
    public FisDtos.FacturaXmlResponse getXml(Long id) {
        FacturaFis f = requerida(id);
        return new FisDtos.FacturaXmlResponse(
                f.getFacturaId(), f.getFolio(), f.getUuid(),
                f.getTipo(), f.getCfdiXml());
    }

    public FisDtos.FacturaFisResponse create(FisDtos.FacturaFisRequest req) {
        FacturaFis f = FacturaFis.builder()
                .tipo(req.tipo())
                .serie(req.serie())
                .folio(req.folio())
                .uuid(req.uuid())
                .emisorRfc(req.emisorRfc())
                .receptorRfc(req.receptorRfc())
                .subtotal(req.subtotal())
                .iva(req.iva())
                .cfdiXml(req.cfdiXml())
                .ventaId(req.ventaId())
                .usuarioId(1)
                .build();
        return toResponse(facturaRepo.save(f));
    }

    private FacturaFis requerida(Long id) {
        return facturaRepo.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
    }

    private boolean fromDesdeHasta(Instant desde, Instant hasta) {
        return desde != null && hasta != null;
    }

    private FisDtos.FacturaFisResponse toResponse(FacturaFis f) {
        return new FisDtos.FacturaFisResponse(
                f.getFacturaId(), f.getTipo(), f.getSerie(), f.getFolio(),
                f.getUuid(), f.getEmisorRfc(), f.getReceptorRfc(),
                f.getSubtotal(), f.getIva(), f.getTotal(),
                f.getFechaTimbrado(), f.getEstado(), f.getVentaId(),
                f.getUsuarioId(), f.getCreadoEn());
    }
}