package mx.ferreteria.api.ven.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.entity.FormaPago;
import mx.ferreteria.api.cat.entity.Producto;
import mx.ferreteria.api.cat.repo.FormaPagoRepository;
import mx.ferreteria.api.cat.repo.ProductoRepository;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.common.security.UserPrincipal;
import mx.ferreteria.api.ven.dto.VenDtos;
import mx.ferreteria.api.ven.entity.DevolucionDetalle;
import mx.ferreteria.api.ven.entity.DevolucionVenta;
import mx.ferreteria.api.ven.entity.Venta;
import mx.ferreteria.api.ven.repo.DevolucionDetalleRepository;
import mx.ferreteria.api.ven.repo.DevolucionVentaRepository;
import mx.ferreteria.api.ven.repo.VentaRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class DevolucionService {

    private final DevolucionVentaRepository repo;
    private final DevolucionDetalleRepository detalleRepo;
    private final VentaRepository ventaRepo;
    private final ProductoRepository productoRepo;
    private final FormaPagoRepository formaPagoRepo;

    @Transactional(readOnly = true)
    public Page<VenDtos.DevolucionResponse> listByVenta(Long ventaId, Pageable pageable) {
        Page<DevolucionVenta> page = repo.findByVentaIdOrderByFechaDesc(ventaId, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public VenDtos.DevolucionResponse getById(Long id) {
        DevolucionVenta d = repo.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        return toResponse(d);
    }

    public VenDtos.DevolucionResponse create(VenDtos.DevolucionRequest req) {
        Venta venta = ventaRepo.findById(req.ventaId())
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        if ("CANCELADA".equals(venta.getEstado())) {
            throw new ReglaNegocioException(ErrorCode.VALOR_INVALIDO);
        }
        DevolucionVenta dev = DevolucionVenta.builder()
                .ventaId(req.ventaId())
                .motivo(req.motivo())
                .total(BigDecimal.ZERO)
                .formaDevolucionId(req.formaDevolucionId())
                .usuarioId(UserPrincipal.actual().usuarioId())
                .build();
        DevolucionVenta saved = repo.save(dev);

        for (VenDtos.DevolucionDetalleRequest d : req.detalles()) {
            DevolucionDetalle det = DevolucionDetalle.builder()
                    .devolucionId(saved.getDevolucionId())
                    .productoId(d.productoId())
                    .ventaDetalleId(d.ventaDetalleId())
                    .cantidad(d.cantidad())
                    .precioUnitario(d.precioUnitario())
                    .build();
            detalleRepo.save(det);
        }

        repo.flush();
        DevolucionVenta refreshed = repo.findById(saved.getDevolucionId()).orElse(saved);
        return toResponse(refreshed);
    }

    private VenDtos.DevolucionResponse toResponse(DevolucionVenta d) {
        String ventaFolio = ventaRepo.findById(d.getVentaId())
                .map(Venta::getFolio).orElse(null);
        String formaNombre = formaPagoRepo.findById(d.getFormaDevolucionId())
                .map(FormaPago::getNombre).orElse(null);
        List<VenDtos.DevolucionDetalleResponse> detalles = detalleRepo.findByDevolucionId(d.getDevolucionId())
                .stream().map(det -> {
                    String nombre = productoRepo.findById(det.getProductoId())
                            .map(Producto::getNombre).orElse(null);
                    return new VenDtos.DevolucionDetalleResponse(
                            det.getProductoId(), nombre,
                            det.getVentaDetalleId(),
                            det.getCantidad(), det.getPrecioUnitario(),
                            det.getImporteLinea());
                }).toList();
        return new VenDtos.DevolucionResponse(
                d.getDevolucionId(), d.getFolio(),
                d.getVentaId(), ventaFolio,
                d.getFecha(), d.getMotivo(),
                d.getTotal(),
                d.getFormaDevolucionId(), formaNombre,
                d.getUsuarioId(), detalles);
    }
}
