package mx.ferreteria.api.ven.service;

import java.math.BigDecimal;
import java.time.Instant;
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
import mx.ferreteria.api.ven.dto.VenDtos;
import mx.ferreteria.api.ven.entity.Cotizacion;
import mx.ferreteria.api.ven.entity.CotizacionDetalle;
import mx.ferreteria.api.ven.repo.CotizacionDetalleRepository;
import mx.ferreteria.api.ven.repo.CotizacionRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class CotizacionService {

    private final CotizacionRepository repo;
    private final CotizacionDetalleRepository detalleRepo;
    private final ClienteRepository clienteRepo;
    private final ProductoRepository productoRepo;

    @Transactional(readOnly = true)
    public Page<VenDtos.CotizacionResponse> list(String estado, Pageable pageable) {
        Page<Cotizacion> page = (estado != null)
                ? repo.findByEstadoOrderByFechaDesc(estado, pageable)
                : repo.findAll(pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public VenDtos.CotizacionResponse getById(Long id) {
        Cotizacion c = repo.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        return toResponse(c);
    }

    public VenDtos.CotizacionResponse create(VenDtos.CotizacionRequest req) {
        Cotizacion entity = Cotizacion.builder()
                .fecha(Instant.now())
                .vigenciaHasta(req.vigenciaHasta())
                .subtotal(BigDecimal.ZERO)
                .iva(BigDecimal.ZERO)
                .total(BigDecimal.ZERO)
                .usuarioId(1)
                .build();
        Cotizacion saved = repo.save(entity);
        for (VenDtos.CotizacionDetalleRequest d : req.detalles()) {
            CotizacionDetalle det = CotizacionDetalle.builder()
                    .cotizacionId(saved.getCotizacionId())
                    .productoId(d.productoId())
                    .cantidad(d.cantidad())
                    .precioUnitario(d.precioUnitario())
                    .build();
            detalleRepo.save(det);
        }
        return toResponse(saved);
    }

    public VenDtos.CotizacionResponse convertirAVenta(Long cotizacionId, Integer almacenId, Integer formaPagoId) {
        Cotizacion cot = repo.findById(cotizacionId)
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        if (!"VIGENTE".equals(cot.getEstado())) {
            throw new ReglaNegocioException(ErrorCode.REGISTRO_DUPLICADO);
        }
        cot.setEstado("CONVERTIDA");
        repo.save(cot);
        return toResponse(cot);
    }

    private VenDtos.CotizacionResponse toResponse(Cotizacion c) {
        String clienteNombre = null;
        if (c.getClienteId() != null) {
            clienteNombre = clienteRepo.findById(c.getClienteId())
                    .map(Cliente::getRazonSocial).orElse(null);
        }
        List<VenDtos.CotizacionDetalleResponse> detalles = detalleRepo.findByCotizacionId(c.getCotizacionId())
                .stream().map(d -> {
                    String nombre = productoRepo.findById(d.getProductoId())
                            .map(Producto::getNombre).orElse(null);
                    return new VenDtos.CotizacionDetalleResponse(
                            d.getProductoId(), nombre,
                            d.getCantidad(), d.getPrecioUnitario(),
                            d.getImporteLinea());
                }).toList();
        return new VenDtos.CotizacionResponse(
                c.getCotizacionId(), c.getFolio(),
                c.getClienteId(), clienteNombre,
                c.getFecha(), c.getVigenciaHasta(),
                c.getSubtotal(), c.getIva(), c.getTotal(),
                c.getEstado(), c.getVentaGeneradaId(),
                c.getUsuarioId(), detalles);
    }
}
