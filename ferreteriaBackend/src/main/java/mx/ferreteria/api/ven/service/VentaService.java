package mx.ferreteria.api.ven.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.entity.Ciudad;
import mx.ferreteria.api.cat.entity.Cliente;
import mx.ferreteria.api.cat.entity.FormaPago;
import mx.ferreteria.api.cat.entity.Producto;
import mx.ferreteria.api.cat.repo.CiudadRepository;
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
import mx.ferreteria.api.ven.entity.PagoCliente;
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
    private final CiudadRepository ciudadRepo;
    private final ProductoRepository productoRepo;
    private final FormaPagoRepository formaPagoRepo;
    private final CuentaCobrarRepository cuentaRepo;
    private final CajaService cajaService;
    private final PagoClienteRepository pagoRepo;

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
        return toResponsePage(page);
    }

    /**
     * Listado por fecha_local (DATE generada en BD según TZ America/Mexico_City).
     * Variante preferida para queries tipo "ventas del día" — evita el desfase
     * de zona horaria que ocurre al convertir LocalDate → Instant en el backend.
     */
    @Transactional(readOnly = true)
    public Page<VenDtos.VentaResponse> listByFechaLocal(Integer almacenId, LocalDate desde, LocalDate hasta,
            Pageable pageable) {
        Page<Venta> page;
        if (almacenId != null && desde != null && hasta != null) {
            page = ventaRepo.findByAlmacenIdAndFechaLocalBetweenOrderByFechaDesc(almacenId, desde, hasta, pageable);
        } else if (desde != null && hasta != null) {
            page = ventaRepo.findByFechaLocalBetweenOrderByFechaDesc(desde, hasta, pageable);
        } else {
            page = ventaRepo.findAll(pageable);
        }
        return toResponsePage(page);
    }

    private Page<VenDtos.VentaResponse> toResponsePage(Page<Venta> page) {
        if (page.isEmpty() || page.getContent().size() == 1) {
            return page.map(this::toResponse);
        }
        List<VenDtos.VentaResponse> content = toResponses(page.getContent());
        return new PageImpl<>(content, page.getPageable(), page.getTotalElements());
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

        if (req.clienteId() != null && !clienteRepo.existsById(req.clienteId())) {
            throw new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO);
        }

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

        // BACK-REND-017: Los totales (subtotal/iva/descuento_total/total, folio y
        // total_linea de cada detalle) los calcula la BD vía triggers y columnas
        // GENERATED. Hibernate no refleja esos valores sobre las instancias
        // gestionadas al insertar. Antes: em.refresh(savedVenta) + N
        // detalles.forEach(em::refresh) = 1 + N queries (51 para 50 SKUs).
        // Ahora: reloadAfterTriggers() encapsula em.clear()+find y devuelve la
        // Venta ya con cabecera y (via toResponse) detalles recalculados.
        // BACK-DIS-002: el EntityManager ya NO vive en el service.
        Venta v = ventaRepo.reloadAfterTriggers(savedVenta.getVentaId())
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        return toResponse(v);
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
     * Batch assembler para páginas: 6 queries fijas en lugar de ~7*N.
     * Usado por list() y listByFechaLocal() cuando la página tiene >1 elemento.
     * Para getById/cancel/checkout (N=1) se mantiene toResponse() simple.
     */
    private List<VenDtos.VentaResponse> toResponses(List<Venta> ventas) {
        List<Long> ventaIds = ventas.stream().map(Venta::getVentaId).toList();

        Set<Long> clienteIds = ventas.stream().map(Venta::getClienteId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Cliente> clientes = clienteIds.isEmpty() ? Map.of()
                : clienteRepo.findAllById(clienteIds).stream()
                        .collect(Collectors.toMap(Cliente::getClienteId, Function.identity()));

        Set<Integer> ciudadIds = clientes.values().stream()
                .map(Cliente::getCiudadId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Integer, Ciudad> ciudades = ciudadIds.isEmpty() ? Map.of()
                : ciudadRepo.findAllById(ciudadIds).stream()
                        .collect(Collectors.toMap(Ciudad::getCiudadId, Function.identity()));

        Set<Integer> almacenIds = ventas.stream().map(Venta::getAlmacenId).collect(Collectors.toSet());
        Map<Integer, Almacen> almacenes = almacenRepo.findAllById(almacenIds).stream()
                .collect(Collectors.toMap(Almacen::getAlmacenId, Function.identity()));

        Set<Integer> formaPagoIds = ventas.stream().map(Venta::getFormaPagoId).collect(Collectors.toSet());
        Map<Integer, FormaPago> formasPago = formaPagoRepo.findAllById(formaPagoIds).stream()
                .collect(Collectors.toMap(FormaPago::getFormaPagoId, Function.identity()));

        List<VentaDetalle> allDetalles = detalleRepo.findByVentaIdIn(ventaIds);
        Map<Long, List<VentaDetalle>> detallesByVenta = allDetalles.stream()
                .collect(Collectors.groupingBy(VentaDetalle::getVentaId));

        Set<Long> productoIds = allDetalles.stream().map(VentaDetalle::getProductoId)
                .collect(Collectors.toSet());
        Map<Long, Producto> productos = productoIds.isEmpty() ? Map.of()
                : productoRepo.findAllById(productoIds).stream()
                        .collect(Collectors.toMap(Producto::getProductoId, Function.identity()));

        List<CuentaCobrar> cuentas = cuentaRepo.findByVentaIdIn(ventaIds);
        Map<Long, CuentaCobrar> cuentaByVenta = cuentas.stream()
                .collect(Collectors.toMap(CuentaCobrar::getVentaId, Function.identity()));

        List<Long> cuentaIds = cuentas.stream().map(CuentaCobrar::getCuentaCobrarId).toList();
        Map<Long, List<PagoCliente>> pagosByCuenta;
        if (cuentaIds.isEmpty()) {
            pagosByCuenta = Map.of();
        } else {
            pagosByCuenta = pagoRepo.findByCuentaCobrarIdIn(cuentaIds).stream()
                    .collect(Collectors.groupingBy(PagoCliente::getCuentaCobrarId));
            pagosByCuenta.replaceAll((k, v) -> v.stream()
                    .sorted(java.util.Comparator.comparing(PagoCliente::getFecha,
                            java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())).reversed())
                    .toList());
        }

        List<VenDtos.VentaResponse> result = new ArrayList<>(ventas.size());
        for (Venta v : ventas) {
            Cliente cli = v.getClienteId() == null ? null : clientes.get(v.getClienteId());
            String clienteNombre = cli != null ? cli.getRazonSocial() : null;
            VenDtos.ClienteVentaInfo clienteInfo = toClienteInfo(cli, ciudades);
            String almacenNombre = almacenes.containsKey(v.getAlmacenId())
                    ? almacenes.get(v.getAlmacenId()).getNombre() : null;
            String formaPagoNombre = formasPago.containsKey(v.getFormaPagoId())
                    ? formasPago.get(v.getFormaPagoId()).getNombre() : null;

            List<VenDtos.VentaDetalleResponse> detalles = detallesByVenta
                    .getOrDefault(v.getVentaId(), List.of()).stream()
                    .map(d -> new VenDtos.VentaDetalleResponse(
                            d.getVentaDetalleId(), d.getProductoId(),
                            productos.containsKey(d.getProductoId())
                                    ? productos.get(d.getProductoId()).getNombre() : null,
                            d.getCantidad(), d.getPrecioUnitario(),
                            d.getCostoUnitario(), d.getDescuentoLinea(),
                            d.getTotalLinea()))
                    .toList();

            List<VenDtos.PagoResponse> pagos = List.of();
            CuentaCobrar cc = cuentaByVenta.get(v.getVentaId());
            if (cc != null) {
                pagos = pagosByCuenta.getOrDefault(cc.getCuentaCobrarId(), List.of()).stream()
                        .map(p -> new VenDtos.PagoResponse(
                                p.getPagoClienteId(), p.getFormaPagoId(),
                                p.getReferencia(), p.getMonto(), p.getFecha()))
                        .toList();
            }

            result.add(new VenDtos.VentaResponse(
                    v.getVentaId(), v.getFolio(),
                    v.getClienteId(), clienteNombre, clienteInfo,
                    v.getAlmacenId(), almacenNombre,
                    v.getFecha(), v.getFechaLocal(),
                    v.getFormaPagoId(), formaPagoNombre,
                    v.getIvaTasa(), v.getIvaIncluido(),
                    v.getSubtotal(), v.getIva(),
                    v.getDescuentoTotal(), v.getTotal(),
                    v.getEstado(), v.getUsuarioId(), v.getTurnoCajaId(),
                    v.getNotas(), detalles, pagos));
        }
        return result;
    }

    private VenDtos.VentaResponse toResponse(Venta v) {
        Cliente cli = v.getClienteId() != null ? clienteRepo.findById(v.getClienteId()).orElse(null) : null;
        String clienteNombre = cli != null ? cli.getRazonSocial() : null;
        VenDtos.ClienteVentaInfo clienteInfo = toClienteInfo(cli, null);
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
                v.getClienteId(), clienteNombre, clienteInfo,
                v.getAlmacenId(), almacenNombre,
                v.getFecha(), v.getFechaLocal(),
                v.getFormaPagoId(), formaPagoNombre,
                v.getIvaTasa(), v.getIvaIncluido(),
                v.getSubtotal(), v.getIva(),
                v.getDescuentoTotal(), v.getTotal(),
                v.getEstado(), v.getUsuarioId(), v.getTurnoCajaId(),
                v.getNotas(), detalles, pagos);
    }

    private VenDtos.ClienteVentaInfo toClienteInfo(Cliente c, Map<Integer, Ciudad> ciudades) {
        if (c == null) return null;
        String ciudadNombre = null;
        if (c.getCiudadId() != null) {
            if (ciudades != null && ciudades.containsKey(c.getCiudadId())) {
                ciudadNombre = ciudades.get(c.getCiudadId()).getNombre();
            } else {
                ciudadNombre = ciudadRepo.findById(c.getCiudadId()).map(Ciudad::getNombre).orElse(null);
            }
        }
        return new VenDtos.ClienteVentaInfo(
                c.getClienteId(), c.getRazonSocial(), c.getNombreComercial(),
                c.getRfc(), c.getCurp(), c.getRegimenFiscal(),
                c.getTelefono(), c.getWhatsapp(), c.getEmail(),
                c.getCalle(), c.getColonia(), c.getCp(), ciudadNombre);
    }
}
