package mx.ferreteria.api.fin.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.common.security.UserPrincipal;
import mx.ferreteria.api.fin.dto.FinDtos;
import mx.ferreteria.api.fin.entity.Gasto;
import mx.ferreteria.api.fin.entity.IngresoOtro;
import mx.ferreteria.api.fin.repo.GastoRepository;
import mx.ferreteria.api.fin.repo.IngresoOtroRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class GastoService {

    private final GastoRepository gastoRepo;
    private final IngresoOtroRepository ingresoRepo;

    // ─── Gastos ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<FinDtos.GastoResponse> listGastos(Pageable pageable) {
        return gastoRepo.findAllByOrderByCreadoEnDesc(pageable).map(this::toGastoResponse);
    }

    public FinDtos.GastoResponse createGasto(FinDtos.GastoRequest req) {
        Gasto g = Gasto.builder()
                .tipoGastoId(req.tipoGastoId())
                .descripcion(req.descripcion())
                .monto(req.monto())
                .fechaGasto(req.fechaGasto() != null ? req.fechaGasto() : java.time.LocalDate.now())
                .formaPagoId(req.formaPagoId())
                .proveedorId(req.proveedorId())
                .turnoCajaId(req.turnoCajaId())
                .facturaUuid(req.facturaUuid())
                .usuarioId(UserPrincipal.actual().usuarioId())
                .build();
        Gasto saved = gastoRepo.save(g);
        return toGastoResponse(saved);
    }

    /**
     * Un gasto ligado a un turno (trg_gasto_post ya registro el movimiento en la
     * caja) es inmutable: modificarlo o eliminarlo desincronizaria el cuadre.
     */
    public FinDtos.GastoResponse updateGasto(Long id, FinDtos.GastoRequest req) {
        Gasto g = modificarGasto(id);
        g.setTipoGastoId(req.tipoGastoId());
        g.setDescripcion(req.descripcion());
        g.setMonto(req.monto());
        g.setFechaGasto(req.fechaGasto() != null ? req.fechaGasto() : g.getFechaGasto());
        g.setFormaPagoId(req.formaPagoId());
        g.setProveedorId(req.proveedorId());
        g.setFacturaUuid(req.facturaUuid());
        return toGastoResponse(gastoRepo.save(g));
    }

    public void deleteGasto(Long id) {
        Gasto g = modificarGasto(id);
        gastoRepo.delete(g);
    }

    private Gasto modificarGasto(Long id) {
        Gasto g = gastoRepo.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO, id));
        if (g.getTurnoCajaId() != null) {
            throw new ReglaNegocioException(ErrorCode.REGISTRO_NO_MODIFICABLE, g.getFolio());
        }
        return g;
    }

    // ─── Ingresos otros ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<FinDtos.IngresoOtroResponse> listIngresos(Pageable pageable) {
        return ingresoRepo.findAllByOrderByCreadoEnDesc(pageable).map(this::toIngresoResponse);
    }

    public FinDtos.IngresoOtroResponse createIngreso(FinDtos.IngresoOtroRequest req) {
        IngresoOtro io = IngresoOtro.builder()
                .concepto(req.concepto())
                .monto(req.monto())
                .fecha(req.fecha() != null ? req.fecha() : java.time.LocalDate.now())
                .formaPagoId(req.formaPagoId())
                .turnoCajaId(req.turnoCajaId())
                .usuarioId(UserPrincipal.actual().usuarioId())
                .build();
        IngresoOtro saved = ingresoRepo.save(io);
        return toIngresoResponse(saved);
    }

    public FinDtos.IngresoOtroResponse updateIngreso(Long id, FinDtos.IngresoOtroRequest req) {
        IngresoOtro io = modificarIngreso(id);
        io.setConcepto(req.concepto());
        io.setMonto(req.monto());
        io.setFecha(req.fecha() != null ? req.fecha() : io.getFecha());
        io.setFormaPagoId(req.formaPagoId());
        return toIngresoResponse(ingresoRepo.save(io));
    }

    public void deleteIngreso(Long id) {
        IngresoOtro io = modificarIngreso(id);
        ingresoRepo.delete(io);
    }

    private IngresoOtro modificarIngreso(Long id) {
        IngresoOtro io = ingresoRepo.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO, id));
        if (io.getTurnoCajaId() != null) {
            throw new ReglaNegocioException(ErrorCode.REGISTRO_NO_MODIFICABLE, io.getIngresoOtroId());
        }
        return io;
    }

    private FinDtos.GastoResponse toGastoResponse(Gasto g) {
        return new FinDtos.GastoResponse(
                g.getGastoId(), g.getFolio(),
                g.getTipoGastoId(), null,
                g.getDescripcion(), g.getMonto(),
                g.getFechaGasto(),
                g.getFormaPagoId(), null,
                g.getProveedorId(), g.getTurnoCajaId(),
                g.getFacturaUuid(), g.getUsuarioId(), g.getCreadoEn());
    }

    private FinDtos.IngresoOtroResponse toIngresoResponse(IngresoOtro io) {
        return new FinDtos.IngresoOtroResponse(
                io.getIngresoOtroId(), io.getConcepto(),
                io.getMonto(), io.getFecha(),
                io.getFormaPagoId(), null,
                io.getTurnoCajaId(), io.getUsuarioId(), io.getCreadoEn());
    }
}
