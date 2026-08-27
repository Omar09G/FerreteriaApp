package mx.ferreteria.api.cat.service;

import java.math.BigDecimal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.dto.CatDtos.ProveedorRequest;
import mx.ferreteria.api.cat.dto.CatDtos.ProveedorResponse;
import mx.ferreteria.api.cat.entity.Proveedor;
import mx.ferreteria.api.cat.repo.ProveedorRepository;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.i18n.ErrorCode;

@Service
@RequiredArgsConstructor
@Transactional
public class ProveedorService {

    private final ProveedorRepository repo;

    @Transactional(readOnly = true)
    public Page<ProveedorResponse> list(String q, Pageable pageable) {
        Page<Proveedor> page = StringUtils.hasText(q)
                ? repo.findByActivoTrueAndRazonSocialContainingIgnoreCase(q, pageable)
                : repo.findByActivoTrue(pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ProveedorResponse getById(Integer id) {
        Proveedor entity = repo.findById(id).orElseThrow(
                () -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        return toResponse(entity);
    }

    public ProveedorResponse create(ProveedorRequest req) {
        Proveedor entity = Proveedor.builder()
                .razonSocial(req.razonSocial())
                .rfc(req.rfc())
                .regimenFiscal(req.regimenFiscal())
                .email(req.email())
                .telefono(req.telefono())
                .diasCredito(req.diasCredito() != null ? req.diasCredito() : 0)
                .limiteCredito(req.limiteCredito() != null ? req.limiteCredito() : BigDecimal.ZERO)
                .build();
        return toResponse(repo.save(entity));
    }

    public ProveedorResponse update(Integer id, ProveedorRequest req) {
        Proveedor entity = repo.findById(id).orElseThrow(
                () -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        entity.setRazonSocial(req.razonSocial());
        entity.setRfc(req.rfc());
        entity.setRegimenFiscal(req.regimenFiscal());
        entity.setEmail(req.email());
        entity.setTelefono(req.telefono());
        if (req.diasCredito() != null) {
            entity.setDiasCredito(req.diasCredito());
        }
        if (req.limiteCredito() != null) {
            entity.setLimiteCredito(req.limiteCredito());
        }
        return toResponse(repo.save(entity));
    }

    public void deactivate(Integer id) {
        Proveedor entity = repo.findById(id).orElseThrow(
                () -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        entity.setActivo(false);
        repo.save(entity);
    }

    private ProveedorResponse toResponse(Proveedor p) {
        return new ProveedorResponse(
                p.getProveedorId(), p.getRazonSocial(), p.getRfc(),
                p.getRegimenFiscal(), p.getEmail(), p.getTelefono(),
                p.getDiasCredito(), p.getLimiteCredito());
    }
}
