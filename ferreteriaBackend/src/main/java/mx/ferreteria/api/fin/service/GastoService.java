package mx.ferreteria.api.fin.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

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
