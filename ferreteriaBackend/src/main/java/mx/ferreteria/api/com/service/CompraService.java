package mx.ferreteria.api.com.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
import mx.ferreteria.api.com.repo.CompraReportRepository;
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
        private final CompraReportRepository reportRepo;
        private final CajaService cajaService;

        @PersistenceContext
        private EntityManager em;

        // ─── Lectura ────────────────────────────────────────────────────

        @Transactional(readOnly = true)
        public Page<ComDtos.CompraResponse> list(Integer almacenId, Integer proveedorId,
                        LocalDate desde, LocalDate hasta, Pageable pageable) {
                // Fix: findByOrderByFechaDesc(Pageable) con Pageable unsorted no respeta OrderBy en algunas versiones de Spring Data.
                // Normalizamos a Sort por defecto fecha DESC cuando el front no envía `sort` (caso /compras?page=0&size=15).
                if (pageable.getSort().isUnsorted()) {
                        pageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                                        Sort.by(Sort.Direction.DESC, "fecha"));
                }
                Page<Compra> page;
                if (almacenId != null && desde != null && hasta != null) {
                        page = compraRepo.findByAlmacenIdAndFechaLocalBetweenOrderByFechaDesc(almacenId, desde, hasta,
                                        pageable);
                } else if (almacenId != null) {
                        page = compraRepo.findByAlmacenIdOrderByFechaDesc(almacenId, pageable);
                } else if (proveedorId != null) {
                        page = compraRepo.findByProveedorIdOrderByFechaDesc(proveedorId, pageable);
                } else if (desde != null && hasta != null) {
                        page = compraRepo.findByFechaLocalBetweenOrderByFechaDesc(desde, hasta, pageable);
                } else {
                        page = compraRepo.findByOrderByFechaDesc(pageable);
                }
                return toResponsePage(page);
        }

        private Page<ComDtos.CompraResponse> toResponsePage(Page<Compra> page) {
                if (page.isEmpty() || page.getContent().size() == 1) {
                        return page.map(this::toResponse);
                }
                List<ComDtos.CompraResponse> content = toResponses(page.getContent());
                return new PageImpl<>(content, page.getPageable(), page.getTotalElements());
        }

        /**
         * Batch assembler para páginas: 6 queries fijas en lugar de ~4*N + N*detalles.
         * Usado por list() cuando la página tiene >1 elemento.
         * Para getById/create (N=1) se mantiene toResponse() simple.
         */
        private List<ComDtos.CompraResponse> toResponses(List<Compra> compras) {
                List<Long> compraIds = compras.stream().map(Compra::getCompraId).toList();

                Set<Integer> proveedorIds = compras.stream().map(Compra::getProveedorId).collect(Collectors.toSet());
                Map<Integer, Proveedor> proveedores = proveedorRepo.findAllById(proveedorIds).stream()
                                .collect(Collectors.toMap(Proveedor::getProveedorId, Function.identity()));

                Set<Integer> almacenIds = compras.stream().map(Compra::getAlmacenId).collect(Collectors.toSet());
                Map<Integer, Almacen> almacenes = almacenRepo.findAllById(almacenIds).stream()
                                .collect(Collectors.toMap(Almacen::getAlmacenId, Function.identity()));

                Set<Integer> formaPagoIds = compras.stream().map(Compra::getFormaPagoId).collect(Collectors.toSet());
                Map<Integer, FormaPago> formasPago = formaPagoRepo.findAllById(formaPagoIds).stream()
                                .collect(Collectors.toMap(FormaPago::getFormaPagoId, Function.identity()));

                List<CompraDetalle> allDetalles = detalleRepo.findByCompraIdIn(compraIds);
                Map<Long, List<CompraDetalle>> detallesByCompra = allDetalles.stream()
                                .collect(Collectors.groupingBy(CompraDetalle::getCompraId));

                Set<Long> productoIds = allDetalles.stream().map(CompraDetalle::getProductoId)
                                .collect(Collectors.toSet());
                Map<Long, Producto> productos = productoIds.isEmpty() ? Map.of()
                                : productoRepo.findAllById(productoIds).stream()
                                                .collect(Collectors.toMap(Producto::getProductoId,
                                                                Function.identity()));

                List<ComDtos.CompraResponse> result = new ArrayList<>(compras.size());
                for (Compra c : compras) {
                        String proveedorNombre = proveedores.containsKey(c.getProveedorId())
                                        ? proveedores.get(c.getProveedorId()).getRazonSocial()
                                        : null;
                        String almacenNombre = almacenes.containsKey(c.getAlmacenId())
                                        ? almacenes.get(c.getAlmacenId()).getNombre()
                                        : null;
                        String formaPagoNombre = formasPago.containsKey(c.getFormaPagoId())
                                        ? formasPago.get(c.getFormaPagoId()).getNombre()
                                        : null;

                        List<ComDtos.CompraDetalleResponse> detalles = detallesByCompra
                                        .getOrDefault(c.getCompraId(), List.of()).stream()
                                        .map(d -> new ComDtos.CompraDetalleResponse(
                                                        d.getCompraDetalleId(), d.getProductoId(),
                                                        productos.containsKey(d.getProductoId())
                                                                        ? productos.get(d.getProductoId()).getNombre()
                                                                        : null,
                                                        d.getCantidad(), d.getCostoUnitario(), d.getImporteLinea()))
                                        .toList();

                        result.add(new ComDtos.CompraResponse(
                                        c.getCompraId(), c.getFolio(), c.getFacturaProveedor(),
                                        c.getProveedorId(), proveedorNombre,
                                        c.getAlmacenId(), almacenNombre,
                                        c.getFecha(), c.getFormaPagoId(), formaPagoNombre,
                                        c.getSubtotal(), c.getIva(),
                                        c.getDescuentoTotal(), c.getTotal(),
                                        c.getEstado(), c.getUsuarioId(), c.getTurnoCajaId(),
                                        c.getNotas(), detalles));
                }
                return result;
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
                if (estado != null && !estado.isBlank()) {
                        return reportRepo.vwCuentasPagarPorEstado(estado);
                }
                return reportRepo.vwCuentasPagar();
        }

        @Transactional(readOnly = true)
        public List<ComDtos.FacturaVencidaResponse> facturasVencidas() {
                return reportRepo.vwFacturasVencidas();
        }

        @Transactional(readOnly = true)
        public List<ComDtos.FacturaPendienteResponse> facturasPendientes() {
                return reportRepo.vwFacturasPendientes();
        }

        @Transactional(readOnly = true)
        public List<ComDtos.FacturaProveedorResponse> facturasProveedor(Integer proveedorId) {
                return reportRepo.vwUltimasFacturasProveedor(proveedorId);
        }

        // ─── Abonos a cuentas por pagar (POST) ─────────────────────────────
        // Java orquesta el abono: valida cuenta y monto, resuelve turno/caja y
        // deja que la BD haga el cierre (trigger fn_pago_proveedor_post pasa la
        // cuenta a PARCIAL/LIQUIDADA y registra SALIDA PAGO_PROVEEDOR).

        @Transactional
        public ComDtos.PagoProveedorResponse abonar(Long cuentaPagarId, ComDtos.PagoProveedorRequest req) {
                List<ComDtos.CuentaPagoDetalle> filas = reportRepo.findCuentaPagoDetalle(cuentaPagarId);
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
                                ? "ABONO"
                                : req.referencia().trim();
                Long pagoProveedorId = ((Number) em.createNativeQuery(
                                "INSERT INTO com.pagos_proveedor (cuenta_pagar_id, forma_pago_id, referencia, monto, usuario_id, turno_caja_id) "
                                + "VALUES (:cuentaPagarId, :formaPagoId, :referencia, :monto, :usuarioId, :turnoCajaId) RETURNING pago_proveedor_id")
                                .setParameter("cuentaPagarId", cuentaPagarId)
                                .setParameter("formaPagoId", formaPago.getFormaPagoId())
                                .setParameter("referencia", referencia)
                                .setParameter("monto", req.monto())
                                .setParameter("usuarioId", UserPrincipal.actual().usuarioId())
                                .setParameter("turnoCajaId", turnoCajaId)
                                .getSingleResult()).longValue();
                em.flush();

                ComDtos.CuentaPagoDetalle actualizada = reportRepo.findCuentaPagoDetalle(cuentaPagarId).get(0);
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