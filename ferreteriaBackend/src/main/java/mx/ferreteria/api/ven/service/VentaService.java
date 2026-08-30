package mx.ferreteria.api.ven.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.entity.Cliente;
import mx.ferreteria.api.cat.entity.FormaPago;
import mx.ferreteria.api.cat.entity.Producto;
import mx.ferreteria.api.cat.repo.ClienteRepository;
import mx.ferreteria.api.cat.repo.FormaPagoRepository;
import mx.ferreteria.api.cat.repo.ProductoRepository;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.inv.entity.Almacen;
import mx.ferreteria.api.inv.repo.AlmacenRepository;
import mx.ferreteria.api.common.security.UserPrincipal;
import mx.ferreteria.api.fin.service.CajaService;
import mx.ferreteria.api.ven.dto.VenDtos;
import mx.ferreteria.api.ven.entity.CuentaCobrar;
import mx.ferreteria.api.ven.entity.Venta;
import mx.ferreteria.api.ven.entity.VentaDetalle;
import mx.ferreteria.api.ven.repo.CuentaCobrarRepository;
import mx.ferreteria.api.ven.repo.PagoClienteRepository;
import mx.ferreteria.api.ven.repo.VentaDetalleRepository;
import mx.ferreteria.api.ven.repo.VentaRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class VentaService {

    private final VentaRepository ventaRepo;
    private final VentaDetalleRepository detalleRepo;
    private final AlmacenRepository almacenRepo;
    private final ClienteRepository clienteRepo;
    private final ProductoRepository productoRepo;
    private final FormaPagoRepository formaPagoRepo;
    private final CuentaCobrarRepository cuentaRepo;
    private final CajaService cajaService;
    private final PagoClienteRepository pagoRepo;

    @PersistenceContext
    private EntityManager em;

    @Transactional(readOnly = true)
    public Page<VenDtos.VentaResponse> list(Integer almacenId, Instant desde, Instant hasta, Pageable pageable) {
        Page<Venta> page;
        if (almacenId != null && desde != null && hasta != null) {
            page = ventaRepo.findByAlmacenIdAndFechaBetweenOrderByFechaDesc(almacenId, desde, hasta, pageable);
        } else if (desde != null && hasta != null) {
            page = ventaRepo.findByFechaBetweenOrderByFechaDesc(desde, hasta, pageable);
        } else {
            page = ventaRepo.findAll(pageable);
        }
        return page.map(this::toResponse);
    }

    /**
     * Listado por fecha_local (DATE generada en BD según TZ America/Mexico_City).
     * Variante preferida para queries tipo "ventas del día" — evita el desfase
     * de zona horaria que ocurre al convertir LocalDate → Instant en el backend.
     */
    @Transactional(readOnly = true)
    public Page<VenDtos.VentaResponse> listByFechaLocal(Integer almacenId, LocalDate desde, LocalDate hasta, Pageable pageable) {
        Page<Venta> page;
        if (almacenId != null && desde != null && hasta != null) {
            page = ventaRepo.findByAlmacenIdAndFechaLocalBetweenOrderByFechaDesc(almacenId, desde, hasta, pageable);
        } else if (desde != null && hasta != null) {
            page = ventaRepo.findByFechaLocalBetweenOrderByFechaDesc(desde, hasta, pageable);
        } else {
            page = ventaRepo.findAll(pageable);
        }
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public VenDtos.VentaResponse getById(Long id) {
        Venta v = ventaRepo.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        return toResponse(v);
    }

    public VenDtos.VentaResponse checkout(VenDtos.VentaRequest req) {
        if (!almacenRepo.existsById(req.almacenId())) {
            throw new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO);
        }

        formaPagoRepo.findById(req.formaPagoId())
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));

        Long turnoCajaId = cajaService.resolverTurnoAbierto(req.cajaId(), req.almacenId());

        Venta venta = Venta.builder()
                .almacenId(req.almacenId())
                .clienteId(req.clienteId())
                .cotizacionId(req.cotizacionId())
                .fecha(Instant.now())
                .formaPagoId(req.formaPagoId())
                .subtotal(BigDecimal.ZERO)
                .iva(BigDecimal.ZERO)
                .descuentoTotal(BigDecimal.ZERO)
                .total(BigDecimal.ZERO)
                .usuarioId(UserPrincipal.actual().usuarioId())
                .turnoCajaId(turnoCajaId)
                .notas(req.notas())
                .build();
        Venta savedVenta = ventaRepo.save(venta);

        List<VentaDetalle> detalles = new ArrayList<>();
        for (VenDtos.VentaDetalleRequest d : req.detalles()) {
            VentaDetalle det = VentaDetalle.builder()
                    .ventaId(savedVenta.getVentaId())
                    .productoId(d.productoId())
                    .cantidad(d.cantidad())
                    .precioUnitario(d.precioUnitario())
                    .build();
            detalleRepo.save(det);
            detalles.add(det);
        }

        ventaRepo.flush();
        // Los totales (subtotal/iva/descuento_total/total, folio y total_linea de
        // cada detalle) los calcula la BD vía triggers (trg_folio_ventas y
        // trg_det_venta_totales) y columnas GENERATED. Hibernate no refleja esos
        // valores sobre las instancias gestionadas al insertar, así que recargamos
        // desde BD para devolver montos reales (no ceros).
        em.refresh(savedVenta);
        detalles.forEach(em::refresh);
        return toResponse(savedVenta);
    }

    public VenDtos.VentaResponse cancel(Long id, String motivo) {
        Venta v = ventaRepo.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        if ("CANCELADA".equals(v.getEstado())) {
            throw new ReglaNegocioException(ErrorCode.REGISTRO_DUPLICADO);
        }
        v.setEstado("CANCELADA");
        ventaRepo.save(v);
        return toResponse(v);
    }

    /**
     * Si la petición incluye cajaId, devuelve el id del turno actualmente abierto para esa caja.
     * Sin cajaId, la venta queda sin turno (compatibilidad hacia atrás: ventas que no pasan por caja).
     * Lanza {@link ErrorCode#TURNO_NO_ABIERTO} si la caja no tiene turno ABIERTO.
     * Lanza {@link ErrorCode#CAJA_ALMACEN_INCOMPATIBLE} si la caja del turno no pertenece
     * al mismo almacén que la venta (protege contra errores de captura del POS).
     */
    private VenDtos.VentaResponse toResponse(Venta v) {
        String clienteNombre = null;
        if (v.getClienteId() != null) {
            clienteNombre = clienteRepo.findById(v.getClienteId())
                    .map(Cliente::getRazonSocial).orElse(null);
        }
        String almacenNombre = almacenRepo.findById(v.getAlmacenId())
                .map(Almacen::getNombre).orElse(null);
        String formaPagoNombre = formaPagoRepo.findById(v.getFormaPagoId())
                .map(FormaPago::getNombre).orElse(null);
        List<VenDtos.VentaDetalleResponse> detalles = detalleRepo.findByVentaId(v.getVentaId())
                .stream().map(d -> {
                    String nombre = productoRepo.findById(d.getProductoId())
                            .map(Producto::getNombre).orElse(null);
                    return new VenDtos.VentaDetalleResponse(
                            d.getVentaDetalleId(), d.getProductoId(), nombre,
                            d.getCantidad(), d.getPrecioUnitario(),
                            d.getCostoUnitario(), d.getDescuentoLinea(),
                            d.getTotalLinea());
                }).toList();
        List<VenDtos.PagoResponse> pagos = cuentaRepo.findByVentaId(v.getVentaId())
                .map(cc -> pagoRepo.findByCuentaCobrarIdOrderByFechaDesc(cc.getCuentaCobrarId())
                        .stream()
                        .map(p -> new VenDtos.PagoResponse(
                                p.getPagoClienteId(), p.getFormaPagoId(),
                                p.getReferencia(), p.getMonto(), p.getFecha()))
                        .toList())
                .orElse(List.of());
        return new VenDtos.VentaResponse(
                v.getVentaId(), v.getFolio(),
                v.getClienteId(), clienteNombre,
                v.getAlmacenId(), almacenNombre,
                v.getFecha(), v.getFechaLocal(),
                v.getFormaPagoId(), formaPagoNombre,
                v.getIvaTasa(), v.getIvaIncluido(),
                v.getSubtotal(), v.getIva(),
                v.getDescuentoTotal(), v.getTotal(),
                v.getEstado(), v.getUsuarioId(), v.getTurnoCajaId(),
                v.getNotas(), detalles, pagos);
    }
}
