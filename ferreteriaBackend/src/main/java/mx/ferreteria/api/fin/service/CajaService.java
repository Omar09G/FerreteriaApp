package mx.ferreteria.api.fin.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.common.security.UserPrincipal;
import mx.ferreteria.api.fin.dto.FinDtos;
import mx.ferreteria.api.fin.entity.Caja;
import mx.ferreteria.api.fin.entity.CorteCaja;
import mx.ferreteria.api.fin.entity.MovimientoCaja;
import mx.ferreteria.api.fin.entity.TurnoCaja;
import mx.ferreteria.api.fin.repo.CajaRepository;
import mx.ferreteria.api.fin.repo.CorteCajaRepository;
import mx.ferreteria.api.fin.repo.MovimientoCajaRepository;
import mx.ferreteria.api.fin.repo.TurnoCajaRepository;
import mx.ferreteria.api.inv.entity.Almacen;
import mx.ferreteria.api.inv.repo.AlmacenRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class CajaService {

    private final CajaRepository cajaRepo;
    private final TurnoCajaRepository turnoRepo;
    private final MovimientoCajaRepository movRepo;
    private final CorteCajaRepository corteRepo;
    private final AlmacenRepository almacenRepo;
    private final JdbcTemplate jdbc;

    // ─── Cajas ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<FinDtos.CajaResponse> listCajas() {
        return cajaRepo.findByActivaTrue().stream()
                .map(this::toCajaResponse).toList();
    }

    // ─── Turnos ─────────────────────────────────────────────────────

    public FinDtos.TurnoCajaResponse abrirTurno(FinDtos.TurnoAperturaRequest req) {
        Caja caja = cajaRepo.findById(req.cajaId())
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        turnoRepo.findByCajaIdAndEstado(req.cajaId(), "ABIERTO")
                .ifPresent(t -> { throw new ReglaNegocioException(ErrorCode.TURNO_YA_CERRADO); });

        TurnoCaja turno = TurnoCaja.builder()
                .cajaId(req.cajaId())
                .usuarioId(UserPrincipal.actual().usuarioId())
                .montoApertura(req.montoApertura())
                .estado("ABIERTO")
                .build();
        TurnoCaja saved = turnoRepo.save(turno);
        return toTurnoResponse(saved, caja.getNombre());
    }

    public FinDtos.TurnoCajaResponse getCerradoTurno(Long turnoId) {
        TurnoCaja t = turnoRepo.findById(turnoId)
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        String cajaNombre = cajaRepo.findById(t.getCajaId()).map(Caja::getNombre).orElse(null);
        return toTurnoResponse(t, cajaNombre);
    }

    @Transactional(readOnly = true)
    public Page<FinDtos.TurnoCajaResponse> listTurnos(Integer cajaId, Pageable pageable) {
        return turnoRepo.findByCajaIdOrderByAperturaEnDesc(cajaId, pageable)
                .map(t -> {
                    String nombre = cajaRepo.findById(t.getCajaId()).map(Caja::getNombre).orElse(null);
                    return toTurnoResponse(t, nombre);
                });
    }

    /**
     * Devuelve el turno actualmente ABIERTO de la caja. Si no existe, lanza
     * {@link ErrorCode#RECURSO_NO_ENCONTRADO} (404) — útil para que el POS
     * pregunte antes de permitir ventas.
     */
    @Transactional(readOnly = true)
    public FinDtos.TurnoCajaResponse getTurnoActual(Integer cajaId) {
        if (!cajaRepo.existsById(cajaId)) {
            throw new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO);
        }
        TurnoCaja t = turnoRepo.findByCajaIdAndEstado(cajaId, "ABIERTO")
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        String nombre = cajaRepo.findById(t.getCajaId()).map(Caja::getNombre).orElse(null);
        return toTurnoResponse(t, nombre);
    }

    // ─── Movimientos ────────────────────────────────────────────────

    public FinDtos.MovimientoCajaResponse registrarMovimiento(Long turnoId, FinDtos.MovimientoCajaRequest req) {
        turnoRepo.findById(turnoId)
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        MovimientoCaja mc = MovimientoCaja.builder()
                .turnoCajaId(turnoId)
                .tipo(req.tipo())
                .concepto(req.concepto())
                .monto(req.monto())
                .formaPagoId(req.formaPagoId())
                .refTabla(req.refTabla())
                .refId(req.refId())
                .usuarioId(UserPrincipal.actual().usuarioId())
                .build();
        MovimientoCaja saved = movRepo.save(mc);
        return toMovimientoResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<FinDtos.MovimientoCajaResponse> listMovimientos(Long turnoId) {
        return movRepo.findByTurnoCajaIdOrderByCreadoEnAsc(turnoId).stream()
                .map(this::toMovimientoResponse).toList();
    }

    // ─── Corte de caja ──────────────────────────────────────────────

    public FinDtos.CorteCajaResponse cerrarTurno(Long turnoId, FinDtos.CorteRequest req) {
        int usuarioCierreId = UserPrincipal.actual().usuarioId();
        Long corteId = jdbc.queryForObject(
                "SELECT fin.fn_cerrar_turno(?, ?, ?, ?)",
                Long.class,
                turnoId, req.montoContado(), usuarioCierreId, req.observaciones());

        CorteCaja corte = corteRepo.findById(corteId)
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        return toCorteResponse(corte);
    }

    @Transactional(readOnly = true)
    public Page<FinDtos.CorteCajaResponse> listCortes(Pageable pageable) {
        return corteRepo.findAllByOrderByFechaDescCorteIdDesc(pageable)
                .map(this::toCorteResponse);
    }

    // ─── Mappers ────────────────────────────────────────────────────

    private FinDtos.CajaResponse toCajaResponse(Caja c) {
        String almacenNombre = almacenRepo.findById(c.getAlmacenId())
                .map(Almacen::getNombre).orElse(null);
        return new FinDtos.CajaResponse(
                c.getCajaId(), c.getNombre(), c.getAlmacenId(),
                almacenNombre, c.getActiva());
    }

    private FinDtos.TurnoCajaResponse toTurnoResponse(TurnoCaja t, String cajaNombre) {
        return new FinDtos.TurnoCajaResponse(
                t.getTurnoCajaId(), t.getCajaId(), cajaNombre,
                t.getUsuarioId(), t.getAperturaEn(), t.getMontoApertura(),
                t.getCierreEn(), t.getMontoEsperado(), t.getMontoContado(),
                t.getDiferencia(), t.getEstado(), t.getObservaciones());
    }

    private FinDtos.MovimientoCajaResponse toMovimientoResponse(MovimientoCaja mc) {
        return new FinDtos.MovimientoCajaResponse(
                mc.getMovimientoId(), mc.getTurnoCajaId(), mc.getTipo(),
                mc.getConcepto(), mc.getMonto(),
                mc.getFormaPagoId(), null,
                mc.getRefTabla(), mc.getRefId(), mc.getCreadoEn());
    }

    private FinDtos.CorteCajaResponse toCorteResponse(CorteCaja c) {
        String cajaNombre = cajaRepo.findById(c.getCajaId()).map(Caja::getNombre).orElse(null);
        String almacenNombre = almacenRepo.findById(c.getAlmacenId()).map(Almacen::getNombre).orElse(null);
        String resultado;
        if (c.getDiferencia().compareTo(BigDecimal.ZERO) == 0) resultado = "CUADRADO";
        else if (c.getDiferencia().compareTo(BigDecimal.ZERO) > 0) resultado = "SOBRANTE";
        else resultado = "FALTANTE";
        return new FinDtos.CorteCajaResponse(
                c.getCorteId(), c.getTurnoCajaId(),
                c.getCajaId(), cajaNombre,
                c.getAlmacenId(), almacenNombre,
                c.getUsuarioId(), c.getUsuarioCierreId(),
                c.getFecha(), c.getAperturaEn(), c.getCierreEn(),
                (long) c.getNumVentas(),
                c.getSubtotal(), c.getIva(), c.getDescuentos(),
                c.getTotalVendido(), c.getCostoVentas(),
                c.getUtilidadBruta(), c.getMargenPct(),
                c.getFondoApertura(),
                c.getEntradasEfectivo(), c.getSalidasEfectivo(),
                c.getDineroEsperado(), c.getDineroContado(),
                c.getDiferencia(), resultado,
                c.getIngresosNoEfectivo(), c.getEgresosNoEfectivo(),
                c.getPerdidasInventario(),
                c.getDesgloseEntradas(), c.getDesgloseSalidas(),
                c.getDesgloseFormasPago(), c.getObservaciones());
    }
}
