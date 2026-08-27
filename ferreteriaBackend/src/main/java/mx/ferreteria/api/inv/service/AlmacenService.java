package mx.ferreteria.api.inv.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.inv.dto.InvDtos.AlmacenRequest;
import mx.ferreteria.api.inv.dto.InvDtos.AlmacenResponse;
import mx.ferreteria.api.inv.entity.Almacen;
import mx.ferreteria.api.inv.repo.AlmacenRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class AlmacenService {

    private final AlmacenRepository repo;

    @Transactional(readOnly = true)
    public Page<AlmacenResponse> list(String q, Pageable pageable) {
        Page<Almacen> page = StringUtils.hasText(q)
                ? repo.findByNombreContainingIgnoreCase(q, pageable)
                : repo.findByActivoTrue(pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public AlmacenResponse getById(Integer id) {
        Almacen entity = repo.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        return toResponse(entity);
    }

    public AlmacenResponse create(AlmacenRequest req) {
        Almacen entity = Almacen.builder()
                .nombre(req.nombre())
                .direccion(req.direccion())
                .telefono(req.telefono())
                .esPuntoVenta(req.esPuntoVenta() != null ? req.esPuntoVenta() : true)
                .build();
        return toResponse(repo.save(entity));
    }

    public AlmacenResponse update(Integer id, AlmacenRequest req) {
        Almacen entity = repo.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        entity.setNombre(req.nombre());
        entity.setDireccion(req.direccion());
        entity.setTelefono(req.telefono());
        if (req.esPuntoVenta() != null) {
            entity.setEsPuntoVenta(req.esPuntoVenta());
        }
        return toResponse(repo.save(entity));
    }

    public void deactivate(Integer id) {
        Almacen entity = repo.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        entity.setActivo(false);
        repo.save(entity);
    }

    private AlmacenResponse toResponse(Almacen a) {
        return new AlmacenResponse(
                a.getAlmacenId(), a.getNombre(), a.getDireccion(),
                a.getTelefono(), a.getEsPuntoVenta(), a.getActivo());
    }
}
