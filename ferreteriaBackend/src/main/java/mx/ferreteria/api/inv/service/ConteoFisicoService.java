package mx.ferreteria.api.inv.service;

import java.math.BigDecimal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.inv.dto.InvDtos.ConteoFisicoDetalleRequest;
import mx.ferreteria.api.inv.dto.InvDtos.ConteoFisicoRequest;
import mx.ferreteria.api.inv.dto.InvDtos.ConteoFisicoResponse;
import mx.ferreteria.api.inv.entity.Almacen;
import mx.ferreteria.api.inv.entity.ConteoFisico;
import mx.ferreteria.api.inv.entity.ConteoFisicoDetalle;
import mx.ferreteria.api.inv.entity.Inventario;
import mx.ferreteria.api.inv.entity.InventarioId;
import mx.ferreteria.api.inv.repo.AlmacenRepository;
import mx.ferreteria.api.inv.repo.ConteoFisicoDetalleRepository;
import mx.ferreteria.api.inv.repo.ConteoFisicoRepository;
import mx.ferreteria.api.inv.repo.InventarioRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class ConteoFisicoService {

    private final ConteoFisicoRepository repo;
    private final ConteoFisicoDetalleRepository detalleRepo;
    private final InventarioRepository inventarioRepo;
    private final AlmacenRepository almacenRepo;

    @Transactional(readOnly = true)
    public Page<ConteoFisicoResponse> list(Integer almacenId, Pageable pageable) {
        Page<ConteoFisico> page = repo.findByAlmacenId(almacenId, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ConteoFisicoResponse getById(Long id) {
        ConteoFisico entity = repo.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        return toResponse(entity);
    }

    public ConteoFisicoResponse create(ConteoFisicoRequest req) {
        Almacen almacen = almacenRepo.findById(req.almacenId())
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));

        ConteoFisico conteo = ConteoFisico.builder()
                .almacenId(req.almacenId())
                .observaciones(req.observaciones())
                .usuarioId(1)
                .build();
        ConteoFisico savedConteo = repo.save(conteo);

        for (ConteoFisicoDetalleRequest d : req.detalles()) {
            InventarioId invId = new InventarioId(d.productoId(), req.almacenId());
            Inventario inventario = inventarioRepo.findById(invId).orElse(null);
            BigDecimal cantidadSistema = inventario != null ? inventario.getStock() : BigDecimal.ZERO;

            ConteoFisicoDetalle detalle = ConteoFisicoDetalle.builder()
                    .conteoId(savedConteo.getConteoId())
                    .productoId(d.productoId())
                    .cantidadSistema(cantidadSistema)
                    .cantidadFisica(d.cantidadFisica())
                    .build();
            detalleRepo.save(detalle);
        }

        return toResponse(savedConteo);
    }

    private ConteoFisicoResponse toResponse(ConteoFisico c) {
        Almacen almacen = almacenRepo.findById(c.getAlmacenId()).orElse(null);
        return new ConteoFisicoResponse(
                c.getConteoId(),
                c.getAlmacenId(),
                almacen != null ? almacen.getNombre() : null,
                c.getEstado(),
                c.getUsuarioId(),
                c.getObservaciones());
    }
}
