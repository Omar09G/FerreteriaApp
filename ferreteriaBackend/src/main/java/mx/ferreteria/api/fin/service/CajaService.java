package mx.ferreteria.api.fin.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.entity.FormaPago;
import mx.ferreteria.api.cat.repo.FormaPagoRepository;
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

        /**
         * Conceptos registrables manualmente en caja (los demás los generan los
         * flujos).
         */
        private static final Set<String> CONCEPTOS_MANUALES = Set.of(
                        "OTRO_INGRESO", "COBRANZA_CREDITO", "DEPOSITO_GARANTIA_RENTA",
                        "GASTO_OPERATIVO", "NOMINA", "RETIRO_EFECTIVO",
                        "DEVOLUCION_CLIENTE", "DEVOLUCION_DEPOSITO_RENTA");

        private final CajaRepository cajaRepo;
        private final TurnoCajaRepository turnoRepo;
        private final MovimientoCajaRepository movRepo;
        private final FormaPagoRepository formaPagoRepo;
        private final CorteCajaRepository corteRepo;
        private final AlmacenRepository almacenRepo;
        private final JdbcTemplate jdbc;

        // ─── Cajas ──────────────────────────────────────────────────────

        @Transactional(readOnly = true)
        public List<FinDtos.CajaResponse> listCajas() {
                return cajaRepo.findByActivaTrue().stream()
                                .map(this::toCajaResponse).toList();
        }

        // ─── Cajas Crear Caja ───────────────────────────────────────
        @Transactional
        public FinDtos.CajaResponse crearCaja(FinDtos.CajaRequest req) {
                if (cajaRepo.findByNombre(req.nombre()).isPresent()) {
                        throw new ReglaNegocioException(ErrorCode.VALOR_DUPLICADO, req.nombre());
                }
                Caja caja = Caja.builder()
                                .nombre(req.nombre())
                                .almacenId(req.almacenId())
                                .activa(req.activa())
                                .build();
                Caja saved = cajaRepo.save(caja);
                return toCajaResponse(saved);
        }

        // ─── Actualizar Caja ───────────────────────────────────────
        @Transactional
        public FinDtos.CajaResponse actualizarCaja(Integer cajaId, FinDtos.CajaRequest req) {
                Caja caja = cajaRepo.findById(cajaId)
                                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
                if (!caja.getNombre().equals(req.nombre()) && cajaRepo.findByNombre(req.nombre()).isPresent()) {
                        throw new ReglaNegocioException(ErrorCode.VALOR_DUPLICADO, req.nombre());
                }
                caja.setNombre(req.nombre());
                caja.setAlmacenId(req.almacenId());
                caja.setActiva(req.activa());
                Caja saved = cajaRepo.save(caja);
                return toCajaResponse(saved);
        }

        // ─── Actualizar Estado Caja ───────────────────────────────────────
        @Transactional
        public FinDtos.CajaResponse actualizarCajaEstado(Integer cajaId, FinDtos.CajaRequest req) {
                Caja caja = cajaRepo.findById(cajaId)
                                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
                caja.setActiva(req.activa());
                Caja saved = cajaRepo.save(caja);
                return toCajaResponse(saved);
        }
        // ─── Turnos ─────────────────────────────────────────────────────

        public FinDtos.TurnoCajaResponse abrirTurno(FinDtos.TurnoAperturaRequest req) {
                Caja caja = cajaRepo.findById(req.cajaId())
                                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
                turnoRepo.findByCajaIdAndEstado(req.cajaId(), "ABIERTO")
                                .ifPresent(t -> {
                                        throw new ReglaNegocioException(ErrorCode.TURNO_YA_CERRADO);
                                });

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
                                        String nombre = cajaRepo.findById(t.getCajaId()).map(Caja::getNombre)
                                                        .orElse(null);
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

        /**
         * Resuelve el turno ABIERTO de una caja para ligar una operación (venta/renta).
         * Devuelve {@code null} si {@code cajaId} es {@code null} (la operación queda
         * fuera de caja). Lanza {@link ErrorCode#CAJA_ALMACEN_INCOMPATIBLE} si la caja
         * del turno no pertenece al mismo almacén que la operación.
         */
        @Transactional(readOnly = true)
        public Long resolverTurnoAbierto(Integer cajaId, int almacenId) {
                if (cajaId == null)
                        return null;
                TurnoCaja turno = turnoRepo.findByCajaIdAndEstado(cajaId, "ABIERTO")
                                .orElseThrow(() -> new ReglaNegocioException(ErrorCode.TURNO_NO_ABIERTO, cajaId));
                Caja caja = cajaRepo.findById(cajaId)
                                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
                if (caja.getAlmacenId() != null && caja.getAlmacenId() != almacenId) {
                        throw new ReglaNegocioException(ErrorCode.CAJA_ALMACEN_INCOMPATIBLE,
                                        cajaId, caja.getAlmacenId(), almacenId);
                }
                return turno.getTurnoCajaId();
        }

        // ─── Movimientos ────────────────────────────────────────────────

        public FinDtos.MovimientoCajaResponse registrarMovimiento(Long turnoId, FinDtos.MovimientoCajaRequest req) {
                if (!CONCEPTOS_MANUALES.contains(req.concepto())) {
                        throw new ReglaNegocioException(ErrorCode.VALOR_INVALIDO, "'" + req.concepto() + "'");
                }
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

        @Transactional(readOnly = true)
        public FinDtos.EsperadoCajaResponse obtenerEsperado(Long turnoId) {
                turnoRepo.findById(turnoId)
                                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
                return jdbc.query("""
                                SELECT t.monto_apertura AS montoApertura,
                                       COALESCE(SUM(mc.monto) FILTER (
                                           WHERE mc.tipo = 'ENTRADA'
                                             AND COALESCE(fp.es_efectivo, true)), 0) AS entradasEfectivo,
                                       COALESCE(SUM(mc.monto) FILTER (
                                           WHERE mc.tipo = 'SALIDA'
                                             AND COALESCE(fp.es_efectivo, true)), 0) AS salidasEfectivo
                                FROM fin.turnos_caja t
                                LEFT JOIN fin.movimientos_caja mc ON mc.turno_caja_id = t.turno_caja_id
                                    AND mc.concepto <> 'APERTURA'
                                LEFT JOIN cat.formas_pago fp ON fp.forma_pago_id = mc.forma_pago_id
                                WHERE t.turno_caja_id = ?
                                GROUP BY t.monto_apertura
                                """, rs -> {
                        rs.next();
                        BigDecimal apertura = rs.getBigDecimal("montoApertura");
                        BigDecimal entradas = rs.getBigDecimal("entradasEfectivo");
                        BigDecimal salidas = rs.getBigDecimal("salidasEfectivo");
                        return new FinDtos.EsperadoCajaResponse(
                                        apertura, entradas, salidas,
                                        apertura.add(entradas).subtract(salidas));
                }, turnoId);
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
        public Page<FinDtos.CorteCajaResponse> listCortes(LocalDate desde, LocalDate hasta, Pageable pageable) {
                return corteRepo.findAllByRangoFecha(desde, hasta, pageable)
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
                String formaPagoNombre = mc.getFormaPagoId() == null ? null
                                : formaPagoRepo.findById(mc.getFormaPagoId())
                                                .map(FormaPago::getNombre).orElse(null);
                String refDescripcion = null;
                if ("com.pagos_proveedor".equals(mc.getRefTabla()) && mc.getRefId() != null) {
                        refDescripcion = jdbc.query("""
                                        SELECT c.folio
                                        FROM com.pagos_proveedor p
                                        JOIN com.cuentas_pagar cp ON cp.cuenta_pagar_id = p.cuenta_pagar_id
                                        JOIN com.compras c ON c.compra_id = cp.compra_id
                                        WHERE p.pago_proveedor_id = ?
                                        """, rs -> rs.next() ? rs.getString(1) : null, mc.getRefId());
                }
                return new FinDtos.MovimientoCajaResponse(
                                mc.getMovimientoId(), mc.getTurnoCajaId(), mc.getTipo(),
                                mc.getConcepto(), mc.getMonto(),
                                mc.getFormaPagoId(), formaPagoNombre,
                                mc.getRefTabla(), mc.getRefId(), mc.getCreadoEn(), refDescripcion);
        }

        private FinDtos.CorteCajaResponse toCorteResponse(CorteCaja c) {
                String cajaNombre = cajaRepo.findById(c.getCajaId()).map(Caja::getNombre).orElse(null);
                String almacenNombre = almacenRepo.findById(c.getAlmacenId()).map(Almacen::getNombre).orElse(null);
                String resultado;
                if (c.getDiferencia().compareTo(BigDecimal.ZERO) == 0)
                        resultado = "CUADRADO";
                else if (c.getDiferencia().compareTo(BigDecimal.ZERO) > 0)
                        resultado = "SOBRANTE";
                else
                        resultado = "FALTANTE";
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
