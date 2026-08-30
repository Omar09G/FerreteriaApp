package mx.ferreteria.api.ven.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.entity.Cliente;
import mx.ferreteria.api.cat.entity.Producto;
import mx.ferreteria.api.cat.repo.ClienteRepository;
import mx.ferreteria.api.cat.repo.ProductoRepository;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.common.security.UserPrincipal;
import mx.ferreteria.api.ven.dto.VenDtos;
import mx.ferreteria.api.ven.entity.Cotizacion;
import mx.ferreteria.api.ven.entity.CotizacionDetalle;
import mx.ferreteria.api.ven.repo.CotizacionDetalleRepository;
import mx.ferreteria.api.ven.repo.CotizacionRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class CotizacionService {

    /**
     * Tasa de IVA estándar en México. `ven.cotizaciones` no tiene una columna `iva_tasa`
     * como `ven.ventas`, así que la tasa es fija. Si en el futuro se requiere, añadir
     * columna + pasar al DTO.
     */
    private static final BigDecimal IVA_TASA = new BigDecimal("0.16");

    private final CotizacionRepository repo;
    private final CotizacionDetalleRepository detalleRepo;
    private final ClienteRepository clienteRepo;
    private final ProductoRepository productoRepo;
    private final VentaService ventaService;

    /** Suma redondeada de una colección de importes. */
    private static BigDecimal suma(List<CotizacionDetalle> detalles) {
        return detalles.stream()
                .map(d -> d.getCantidad().multiply(d.getPrecioUnitario()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calcula subtotal, iva y total a partir de los importes brutos de los detalles.
     * Convención: los precios ya incluyen IVA (default mexicano), así que se
     * desglosa: base = total / 1.16, iva = total - base.
     */
    private Totales calcularTotales(List<CotizacionDetalle> detalles) {
        BigDecimal total = suma(detalles);
        BigDecimal subtotal = total.divide(BigDecimal.ONE.add(IVA_TASA), 2, RoundingMode.HALF_UP);
        BigDecimal iva = total.subtract(subtotal);
        return new Totales(subtotal, iva, total);
    }

    private record Totales(BigDecimal subtotal, BigDecimal iva, BigDecimal total) {}

    @Transactional(readOnly = true)
    public Page<VenDtos.CotizacionResponse> list(String estado,
            LocalDate desde, LocalDate hasta, Pageable pageable) {
        return repo.filtrar(estado, desde, hasta, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public VenDtos.CotizacionResponse getById(Long id) {
        Cotizacion c = repo.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        return toResponse(c);
    }

    public VenDtos.CotizacionResponse create(VenDtos.CotizacionRequest req) {
        // Construimos primero los detalles en memoria para poder calcular totales
        // antes de persistir la cabecera (las columnas cotizacion.subtotal/iva/total
        // no son GENERATED en BD, hay que escribirlas desde la app).
        List<CotizacionDetalle> detalles = req.detalles().stream()
                .map(d -> CotizacionDetalle.builder()
                        .productoId(d.productoId())
                        .cantidad(d.cantidad())
                        .precioUnitario(d.precioUnitario())
                        .build())
                .toList();
        Totales totales = calcularTotales(detalles);

        Cotizacion entity = Cotizacion.builder()
                .fecha(Instant.now())
                .vigenciaHasta(req.vigenciaHasta())
                .subtotal(totales.subtotal())
                .iva(totales.iva())
                .total(totales.total())
                .usuarioId(UserPrincipal.actual().usuarioId())
                .build();
        Cotizacion saved = repo.save(entity);

        // Persistimos los detalles ya con el cotizacionId asignado.
        for (CotizacionDetalle d : detalles) {
            d.setCotizacionId(saved.getCotizacionId());
            detalleRepo.save(d);
        }
        return toResponse(saved);
    }

    /**
     * Convierte una cotización VIGENTE en una venta real: crea la venta con los
     * detalles de la cotización vía {@link VentaService#checkout} y liga el id de la
     * venta en {@code venta_generada_id} antes de marcarla como CONVERTIDA.
     */
    public VenDtos.CotizacionResponse convertirAVenta(Long cotizacionId, Integer almacenId, Integer formaPagoId, Integer cajaId) {
        Cotizacion cot = repo.findById(cotizacionId)
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        if (!"VIGENTE".equals(cot.getEstado())) {
            throw new ReglaNegocioException(ErrorCode.REGISTRO_DUPLICADO);
        }

        List<VenDtos.VentaDetalleRequest> detalleReqs = detalleRepo.findByCotizacionId(cotizacionId)
                .stream()
                .map(d -> new VenDtos.VentaDetalleRequest(
                        d.getProductoId(), d.getCantidad(), d.getPrecioUnitario()))
                .toList();

        VenDtos.VentaResponse venta = ventaService.checkout(new VenDtos.VentaRequest(
                almacenId, cajaId,
                cot.getClienteId(), cotizacionId,
                formaPagoId, detalleReqs, List.of(),
                "Venta generada de la cotización " + cot.getFolio()));

        cot.setEstado("CONVERTIDA");
        cot.setVentaGeneradaId(venta.ventaId());
        repo.save(cot);
        return toResponse(cot);
    }

    private VenDtos.CotizacionResponse toResponse(Cotizacion c) {
        String clienteNombre = null;
        if (c.getClienteId() != null) {
            clienteNombre = clienteRepo.findById(c.getClienteId())
                    .map(Cliente::getRazonSocial).orElse(null);
        }
        List<CotizacionDetalle> entidades = detalleRepo.findByCotizacionId(c.getCotizacionId());
        List<VenDtos.CotizacionDetalleResponse> detalles = entidades.stream().map(d -> {
            String nombre = productoRepo.findById(d.getProductoId())
                    .map(Producto::getNombre).orElse(null);
            return new VenDtos.CotizacionDetalleResponse(
                    d.getProductoId(), nombre,
                    d.getCantidad(), d.getPrecioUnitario(),
                    d.getImporteLinea());
        }).toList();

        // Defensa: cotizaciones creadas antes de este fix pueden tener subtotal=iva=total=0
        // aunque sus detalles tengan importes. Si los totales están vacíos y hay detalles,
        // recalculamos al vuelo para no devolver ceros al frontend.
        Totales totales;
        if ((c.getSubtotal() == null || c.getSubtotal().signum() == 0)
                && (c.getIva() == null || c.getIva().signum() == 0)
                && (c.getTotal() == null || c.getTotal().signum() == 0)
                && !entidades.isEmpty()) {
            totales = calcularTotales(entidades);
        } else {
            totales = new Totales(c.getSubtotal(), c.getIva(), c.getTotal());
        }

        return new VenDtos.CotizacionResponse(
                c.getCotizacionId(), c.getFolio(),
                c.getClienteId(), clienteNombre,
                c.getFecha(), c.getVigenciaHasta(),
                totales.subtotal(), totales.iva(), totales.total(),
                c.getEstado(), c.getVentaGeneradaId(),
                c.getUsuarioId(), detalles);
    }
}
