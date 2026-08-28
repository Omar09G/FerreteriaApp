package mx.ferreteria.api.com.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
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
import mx.ferreteria.api.common.i18n.ErrorCode;
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

    // ─── Lectura ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ComDtos.CompraResponse> list(Integer almacenId, Integer proveedorId,
            Instant desde, Instant hasta, Pageable pageable) {
        Page<Compra> page;
        if (almacenId != null && desde != null && hasta != null) {
            page = compraRepo.findByAlmacenIdAndFechaBetweenOrderByFechaDesc(almacenId, desde, hasta, pageable);
        } else if (almacenId != null) {
            page = compraRepo.findByAlmacenIdOrderByFechaDesc(almacenId, pageable);
        } else if (proveedorId != null) {
            page = compraRepo.findByProveedorIdOrderByFechaDesc(proveedorId, pageable);
        } else if (desde != null && hasta != null) {
            page = compraRepo.findByFechaBetweenOrderByFechaDesc(desde, hasta, pageable);
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
        formaPagoRepo.findById(req.formaPagoId())
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));

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
                .usuarioId(1)
                .turnoCajaId(req.turnoCajaId())
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
                    new BeanPropertyRowMapper<>(ComDtos.CuentasPagarResponse.class), estado);
        }
        return jdbc.query(sql,
                new BeanPropertyRowMapper<>(ComDtos.CuentasPagarResponse.class));
    }

    @Transactional(readOnly = true)
    public List<ComDtos.FacturaVencidaResponse> facturasVencidas() {
        return jdbc.query("SELECT * FROM com.vw_facturas_vencidas",
                new BeanPropertyRowMapper<>(ComDtos.FacturaVencidaResponse.class));
    }

    @Transactional(readOnly = true)
    public List<ComDtos.FacturaPendienteResponse> facturasPendientes() {
        return jdbc.query("SELECT * FROM com.vw_facturas_pendientes",
                new BeanPropertyRowMapper<>(ComDtos.FacturaPendienteResponse.class));
    }

    @Transactional(readOnly = true)
    public List<ComDtos.FacturaProveedorResponse> facturasProveedor(Integer proveedorId) {
        return jdbc.query("SELECT * FROM com.vw_ultimas_facturas_proveedor WHERE proveedor_id = ?",
                new BeanPropertyRowMapper<>(ComDtos.FacturaProveedorResponse.class), proveedorId);
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