package mx.ferreteria.api.com.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.entity.FormaPago;
import mx.ferreteria.api.cat.entity.Producto;
import mx.ferreteria.api.cat.entity.Proveedor;
import mx.ferreteria.api.cat.repo.FormaPagoRepository;
import mx.ferreteria.api.cat.repo.ProductoRepository;
import mx.ferreteria.api.cat.repo.ProveedorRepository;
import mx.ferreteria.api.com.dto.ComDtos;
import mx.ferreteria.api.com.entity.Compra;
import mx.ferreteria.api.com.entity.CompraDetalle;
import mx.ferreteria.api.com.repo.CompraDetalleRepository;
import mx.ferreteria.api.com.repo.CompraRepository;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.common.security.UserPrincipal;
import mx.ferreteria.api.fin.service.CajaService;
import mx.ferreteria.api.inv.entity.Almacen;
import mx.ferreteria.api.inv.repo.AlmacenRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class CompraService {

    private final CompraRepository compraRepo;
    private final CompraDetalleRepository detalleRepo;
    private final ProveedorRepository proveedorRepo;
    private final AlmacenRepository almacenRepo;
    private final FormaPagoRepository formaPagoRepo;
    private final ProductoRepository productoRepo;
    private final JdbcTemplate jdbc;
    private final CajaService cajaService;

    /**
     * Mapper seguro para Java records. A diferencia de
     * {@link org.springframework.jdbc.core.BeanPropertyRowMapper}, que requiere
     * constructor sin argumentos + setters, usa el constructor canónico y resuelve
     * snake_case a camelCase automáticamente.
     */
    private static <T> RowMapper<T> mapper(Class<T> tipo) {
        return DataClassRowMapper.newInstance(tipo);
    }

    // ─── Lectura ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ComDtos.CompraResponse> list(Integer almacenId, Integer proveedorId,
            LocalDate desde, LocalDate hasta, Pageable pageable) {
        Page<Compra> page;
        if (almacenId != null && desde != null && hasta != null) {
            page = compraRepo.findByAlmacenIdAndFechaLocalBetweenOrderByFechaDesc(almacenId, desde, hasta, pageable);
        } else if (almacenId != null) {
            page = compraRepo.findByAlmacenIdOrderByFechaDesc(almacenId, pageable);
        } else if (proveedorId != null) {
            page = compraRepo.findByProveedorIdOrderByFechaDesc(proveedorId, pageable);
        } else if (desde != null && hasta != null) {
            page = compraRepo.findByFechaLocalBetweenOrderByFechaDesc(desde, hasta, pageable);
        } else {
            page = compraRepo.findAll(pageable);
        }
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ComDtos.CompraResponse getById(Long id) {
        Compra c = compraRepo.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        return toResponse(c);
    }

    // ─── Recepción de compra (POST) ──────────────────────────────────
    // Java solo orquesta: inserta cabecera + detalles en UNA transacción con
    // folio NULL (trigger de cfg lo asigna); la BD hace kardex ENTRADA, costo
    // promedio y recalcula totales + cuenta por pagar/pago CONTADO.
    public ComDtos.CompraResponse create(ComDtos.CompraRequest req) {
        proveedorRepo.findById(req.proveedorId())
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        almacenRepo.findById(req.almacenId())
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        FormaPago formaPago = formaPagoRepo.findById(req.formaPagoId())
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));

        Long turnoCajaId = null;
        if (!"CREDITO".equals(formaPago.getClave())) {
            if (req.cajaId() == null) {
                throw new ReglaNegocioException(ErrorCode.CAMPO_REQUERIDO);
            }
            turnoCajaId = cajaService.resolverTurnoAbierto(req.cajaId(), req.almacenId());
        }

        Compra compra = Compra.builder()
                .facturaProveedor(req.facturaProveedor())
                .proveedorId(req.proveedorId())
                .ordenCompraId(req.ordenCompraId())
                .almacenId(req.almacenId())
                .fecha(Instant.now())
                .formaPagoId(req.formaPagoId())
                .subtotal(BigDecimal.ZERO)
                .iva(BigDecimal.ZERO)
                .descuentoTotal(BigDecimal.ZERO)
                .total(BigDecimal.ZERO)
                .estado("RECIBIDA")
                .usuarioId(UserPrincipal.actual().usuarioId())
                .turnoCajaId(turnoCajaId)
                .notas(req.notas())
                .build();
        Compra saved = compraRepo.save(compra);

        for (ComDtos.CompraDetalleRequest d : req.detalles()) {
            CompraDetalle det = CompraDetalle.builder()
                    .compraId(saved.getCompraId())
                    .productoId(d.productoId())
                    .cantidad(d.cantidad())
                    .costoUnitario(d.costoUnitario())
                    .build();
            detalleRepo.save(det);
        }

        compraRepo.flush();
        Compra refreshed = compraRepo.findById(saved.getCompraId()).orElse(saved);
        return toResponse(refreshed);
    }

    // ─── Reportes (vistas com.vw_*) ─────────────────────────────────

    @Transactional(readOnly = true)
    public List<ComDtos.CuentasPagarResponse> cuentasPagar(String estado) {
        String sql = "SELECT * FROM com.vw_cuentas_pagar";
        if (estado != null && !estado.isBlank()) {
            sql += " WHERE estado = ?";
            return jdbc.query(sql,
                    mapper(ComDtos.CuentasPagarResponse.class), estado);
        }
        return jdbc.query(sql,
                mapper(ComDtos.CuentasPagarResponse.class));
    }

    @Transactional(readOnly = true)
    public List<ComDtos.FacturaVencidaResponse> facturasVencidas() {
        return jdbc.query("SELECT * FROM com.vw_facturas_vencidas",
                mapper(ComDtos.FacturaVencidaResponse.class));
    }

    @Transactional(readOnly = true)
    public List<ComDtos.FacturaPendienteResponse> facturasPendientes() {
        return jdbc.query("SELECT * FROM com.vw_facturas_pendientes",
                mapper(ComDtos.FacturaPendienteResponse.class));
    }

    @Transactional(readOnly = true)
    public List<ComDtos.FacturaProveedorResponse> facturasProveedor(Integer proveedorId) {
        return jdbc.query("SELECT * FROM com.vw_ultimas_facturas_proveedor WHERE proveedor_id = ?",
                mapper(ComDtos.FacturaProveedorResponse.class), proveedorId);
    }

    // ─── Abonos a cuentas por pagar (POST) ─────────────────────────────
    // Java orquesta el abono: valida cuenta y monto, resuelve turno/caja y
    // deja que la BD haga el cierre (trigger fn_pago_proveedor_post pasa la
    // cuenta a PARCIAL/LIQUIDADA y registra SALIDA PAGO_PROVEEDOR).
    private static final String SQL_CUENTA_ABONO = """
            SELECT cp.cuenta_pagar_id, c.folio AS compra_folio, cp.estado,
                   cp.monto_total, cp.monto_pagado,
                   cp.monto_total - cp.monto_pagado AS saldo, c.almacen_id
            FROM com.cuentas_pagar cp
            JOIN com.compras c ON c.compra_id = cp.compra_id
            WHERE cp.cuenta_pagar_id = ?
            """;

    @Transactional
    public ComDtos.PagoProveedorResponse abonar(Long cuentaPagarId, ComDtos.PagoProveedorRequest req) {
        List<ComDtos.CuentaPagoDetalle> filas = jdbc.query(SQL_CUENTA_ABONO,
                mapper(ComDtos.CuentaPagoDetalle.class), cuentaPagarId);
        if (filas.isEmpty()) {
            throw new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO);
        }
        ComDtos.CuentaPagoDetalle cuenta = filas.get(0);
        if ("LIQUIDADA".equals(cuenta.estado()) || "CANCELADA".equals(cuenta.estado())) {
            throw new ReglaNegocioException(ErrorCode.ESTADO_INVALIDO, cuenta.estado());
        }
        if (req.monto() == null || req.monto().compareTo(cuenta.saldo()) > 0) {
            throw new ReglaNegocioException(ErrorCode.VALOR_INVALIDO);
        }
        FormaPago formaPago = formaPagoRepo.findById(req.formaPagoId())
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));

        Long turnoCajaId = null;
        if (!"CREDITO".equals(formaPago.getClave())) {
            if (req.cajaId() == null) {
                throw new ReglaNegocioException(ErrorCode.CAMPO_REQUERIDO);
            }
            turnoCajaId = cajaService.resolverTurnoAbierto(req.cajaId(), cuenta.almacenId());
        }

        String referencia = (req.referencia() == null || req.referencia().isBlank())
                ? "ABONO" : req.referencia().trim();
        Long pagoProveedorId = jdbc.queryForObject("""
                INSERT INTO com.pagos_proveedor
                    (cuenta_pagar_id, forma_pago_id, referencia, monto, usuario_id, turno_caja_id)
                VALUES (?, ?, ?, ?, ?, ?)
                RETURNING pago_proveedor_id
                """, Long.class, cuentaPagarId, formaPago.getFormaPagoId(), referencia,
                req.monto(), UserPrincipal.actual().usuarioId(), turnoCajaId);

        ComDtos.CuentaPagoDetalle actualizada = jdbc.query(SQL_CUENTA_ABONO,
                mapper(ComDtos.CuentaPagoDetalle.class), cuentaPagarId).get(0);
        return new ComDtos.PagoProveedorResponse(
                pagoProveedorId, actualizada.estado(), actualizada.montoTotal(),
                actualizada.montoPagado(), actualizada.saldo(), actualizada.compraFolio(),
                formaPago.getFormaPagoId(), turnoCajaId, req.monto());
    }

    // ─── Mapper ─────────────────────────────────────────────────────

    private ComDtos.CompraResponse toResponse(Compra c) {
        String proveedorNombre = proveedorRepo.findById(c.getProveedorId())
                .map(Proveedor::getRazonSocial).orElse(null);
        String almacenNombre = almacenRepo.findById(c.getAlmacenId())
                .map(Almacen::getNombre).orElse(null);
        String formaPagoNombre = formaPagoRepo.findById(c.getFormaPagoId())
                .map(FormaPago::getNombre).orElse(null);
        List<ComDtos.CompraDetalleResponse> detalles = detalleRepo
                .findByCompraIdOrderByCompraDetalleId(c.getCompraId())
                .stream().map(d -> {
                    String nombre = productoRepo.findById(d.getProductoId())
                            .map(Producto::getNombre).orElse(null);
                    return new ComDtos.CompraDetalleResponse(
                            d.getCompraDetalleId(), d.getProductoId(), nombre,
                            d.getCantidad(), d.getCostoUnitario(),
                            d.getImporteLinea());
                }).toList();
        return new ComDtos.CompraResponse(
                c.getCompraId(), c.getFolio(), c.getFacturaProveedor(),
                c.getProveedorId(), proveedorNombre,
                c.getAlmacenId(), almacenNombre,
                c.getFecha(), c.getFormaPagoId(), formaPagoNombre,
                c.getSubtotal(), c.getIva(),
                c.getDescuentoTotal(), c.getTotal(),
                c.getEstado(), c.getUsuarioId(), c.getTurnoCajaId(),
                c.getNotas(), detalles);
    }
}