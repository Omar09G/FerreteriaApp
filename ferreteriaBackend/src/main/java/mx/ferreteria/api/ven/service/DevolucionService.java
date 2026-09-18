package mx.ferreteria.api.ven.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.entity.FormaPago;
import mx.ferreteria.api.cat.entity.Producto;
import mx.ferreteria.api.cat.repo.FormaPagoRepository;
import mx.ferreteria.api.cat.repo.ProductoRepository;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.common.security.UserPrincipal;
import mx.ferreteria.api.fin.dto.FinDtos;
import mx.ferreteria.api.fin.service.CajaService;
import mx.ferreteria.api.ven.dto.VenDtos;
import mx.ferreteria.api.ven.entity.DevolucionDetalle;
import mx.ferreteria.api.ven.entity.DevolucionVenta;
import mx.ferreteria.api.ven.entity.Venta;
import mx.ferreteria.api.ven.entity.VentaDetalle;
import mx.ferreteria.api.ven.repo.DevolucionDetalleRepository;
import mx.ferreteria.api.ven.repo.DevolucionVentaRepository;
import mx.ferreteria.api.ven.repo.VentaDetalleRepository;
import mx.ferreteria.api.ven.repo.VentaRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class DevolucionService {

    private final DevolucionVentaRepository repo;
    private final DevolucionDetalleRepository detalleRepo;
    private final VentaRepository ventaRepo;
    private final VentaDetalleRepository ventaDetalleRepo;
    private final ProductoRepository productoRepo;
    private final FormaPagoRepository formaPagoRepo;
    private final CajaService cajaService;
    // EntityManager contenedor-gestionado (refresh de totales/folio por trigger).
    private final EntityManager em;

    @Transactional(readOnly = true)
    public Page<VenDtos.DevolucionResponse> listByVenta(Long ventaId, Pageable pageable) {
        Page<DevolucionVenta> page = repo.findByVentaIdOrderByFechaDesc(ventaId, pageable);
        if (page.isEmpty() || page.getContent().size() == 1) {
            return page.map(this::toResponse);
        }
        List<Long> devIds = page.getContent().stream().map(DevolucionVenta::getDevolucionId).toList();
        List<DevolucionDetalle> allDetalles = detalleRepo.findByDevolucionIdIn(devIds);
        Map<Long, List<DevolucionDetalle>> detallesByDev = allDetalles.stream()
                .collect(Collectors.groupingBy(DevolucionDetalle::getDevolucionId));
        Set<Long> ventaIds = page.getContent().stream().map(DevolucionVenta::getVentaId).collect(Collectors.toSet());
        Set<Integer> formaIds = page.getContent().stream().map(DevolucionVenta::getFormaDevolucionId)
                .collect(Collectors.toSet());
        Set<Long> productoIds = allDetalles.stream().map(DevolucionDetalle::getProductoId).collect(Collectors.toSet());
        Map<Long, String> ventaFolios = ventaIds.isEmpty() ? Map.of()
                : ventaRepo.findAllById(ventaIds).stream()
                        .collect(Collectors.toMap(Venta::getVentaId, Venta::getFolio));
        Map<Integer, FormaPago> formas = formaPagoRepo.findAllById(formaIds).stream()
                .collect(Collectors.toMap(FormaPago::getFormaPagoId, Function.identity()));
        Map<Long, Producto> productos = productoIds.isEmpty() ? Map.of()
                : productoRepo.findAllById(productoIds).stream()
                        .collect(Collectors.toMap(Producto::getProductoId, Function.identity()));
        List<VenDtos.DevolucionResponse> content = page.getContent().stream().map(d -> {
            String ventaFolio = ventaFolios.get(d.getVentaId());
            FormaPago fp = formas.get(d.getFormaDevolucionId());
            String formaNombre = fp != null ? fp.getNombre() : null;
            List<VenDtos.DevolucionDetalleResponse> detalles = detallesByDev
                    .getOrDefault(d.getDevolucionId(), List.of()).stream()
                    .map(det -> {
                        Producto p = productos.get(det.getProductoId());
                        String nombre = p != null ? p.getNombre() : null;
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
        }).toList();
        return new PageImpl<>(content, page.getPageable(), page.getTotalElements());
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
        if ("CANCELADA".equals(venta.getEstado()) || "DEVUELTA_TOTAL".equals(venta.getEstado())) {
            throw new ReglaNegocioException(ErrorCode.VALOR_INVALIDO, venta.getEstado());
        }
        // Vendido por línea y acumulado ya devuelto: ninguna partida puede
        // exceder su remanente (devolución completa o parcial, nunca de más).
        Map<Long, BigDecimal> vendido = ventaDetalleRepo.findByVentaId(venta.getVentaId()).stream()
                .collect(Collectors.toMap(VentaDetalle::getVentaDetalleId, VentaDetalle::getCantidad));
        if (vendido.isEmpty()) {
            throw new ReglaNegocioException(ErrorCode.VALOR_INVALIDO, "venta sin líneas");
        }
        List<Long> devIds = repo.findByVentaId(venta.getVentaId()).stream()
                .map(DevolucionVenta::getDevolucionId).toList();
        Map<Long, BigDecimal> devuelto = devIds.isEmpty() ? Map.of()
                : detalleRepo.findByDevolucionIdIn(devIds).stream()
                        .filter(d -> d.getVentaDetalleId() != null)
                        .collect(Collectors.groupingBy(DevolucionDetalle::getVentaDetalleId,
                                Collectors.mapping(DevolucionDetalle::getCantidad,
                                        Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));
        for (VenDtos.DevolucionDetalleRequest d : req.detalles()) {
            BigDecimal linea = vendido.get(d.ventaDetalleId());
            if (linea == null) {
                throw new ReglaNegocioException(ErrorCode.VALOR_INVALIDO, "línea " + d.ventaDetalleId());
            }
            BigDecimal remanente = linea.subtract(devuelto.getOrDefault(d.ventaDetalleId(), BigDecimal.ZERO));
            if (d.cantidad().compareTo(remanente) > 0) {
                throw new ReglaNegocioException(ErrorCode.VALOR_INVALIDO,
                        d.cantidad() + " > remanente " + remanente);
            }
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
        // Los triggers calculan folio (BEFORE INSERT) y total (AFTER INSERT
        // en detalles): recargar para no responder con los ceros iniciales.
        em.refresh(saved);
        // Estado por cobertura acumulada (incluye esta devolución).
        Map<Long, BigDecimal> cubierto = new java.util.HashMap<>(devuelto);
        for (VenDtos.DevolucionDetalleRequest d : req.detalles()) {
            cubierto.merge(d.ventaDetalleId(), d.cantidad(), BigDecimal::add);
        }
        boolean total = vendido.entrySet().stream()
                .allMatch(e -> cubierto.getOrDefault(e.getKey(), BigDecimal.ZERO).compareTo(e.getValue()) >= 0);
        venta.setEstado(total ? "DEVUELTA_TOTAL" : "DEVUELTA_PARCIAL");
        ventaRepo.save(venta);

        // Reembolso en caja solo si el turno de la venta sigue ABIERTO y el
        // total es positivo; si ya cerró, la devolución queda sin turno
        // (reembolso manual) pero el estado/stock sí avanzan.
        if (saved.getTotal() != null && saved.getTotal().signum() > 0
                && cajaService.turnoAbierto(venta.getTurnoCajaId())) {
            cajaService.registrarMovimiento(venta.getTurnoCajaId(),
                    new FinDtos.MovimientoCajaRequest("SALIDA", "DEVOLUCION_CLIENTE",
                            saved.getTotal(), req.formaDevolucionId(),
                            "ven.devoluciones_venta", saved.getDevolucionId()));
            saved.setTurnoCajaId(venta.getTurnoCajaId());
        }
        return toResponse(saved);
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
