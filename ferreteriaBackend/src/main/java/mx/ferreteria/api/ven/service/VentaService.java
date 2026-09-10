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

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

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
import mx.ferreteria.api.common.error.ValidacionException;
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
import mx.ferreteria.api.ven.repo.PromocionRepository;
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
    private final PromocionRepository promocionRepo;
    private final PromocionService promocionService;

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

        // ── Promoción opcional (validación + cálculo descuento) ──
        BigDecimal beneficioTotal = BigDecimal.ZERO;
        Long promoIdAAplicar = null;
        String promoTipo = null;
        Map<Long, BigDecimal> descuentoPorProducto = Map.of();
        if (req.promocionId() != null) {
            var promo = promocionRepo.findById(req.promocionId())
                    .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
            promoTipo = promo.getTipo();
            // Validar que la promo realmente aplica al carrito actual
            var evalReq = new VenDtos.PromocionEvaluarRequest(
                    req.clienteId(),
                    req.detalles().stream()
                            .map(d -> new VenDtos.PromocionEvaluarItem(d.productoId(), d.cantidad(),
                                    d.precioUnitario()))
                            .toList());
            var evals = promocionService.evaluar(evalReq);
            var eval = evals.stream().filter(e -> e.promocionId().equals(req.promocionId())).findFirst()
                    .orElseThrow(() -> new ValidacionException(ErrorCode.VALOR_INVALIDO));
            if (!eval.aplica()) {
                throw new ValidacionException(ErrorCode.VALOR_INVALIDO);
            }
            beneficioTotal = eval.beneficioEstimado() != null ? eval.beneficioEstimado() : BigDecimal.ZERO;
            if (beneficioTotal.compareTo(BigDecimal.ZERO) > 0) {
                promoIdAAplicar = req.promocionId();
                // Distribuir descuento por línea según tipo
                descuentoPorProducto = distribuirDescuento(promo, req.detalles(), beneficioTotal);
            }
        }

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
            BigDecimal desc = BigDecimal.ZERO;
            Long pidPromo = null;
            if (promoIdAAplicar != null) {
                desc = descuentoPorProducto.getOrDefault(d.productoId(), BigDecimal.ZERO);
                if (desc.compareTo(BigDecimal.ZERO) > 0)
                    pidPromo = promoIdAAplicar;
                // Para NXM/POR_CANTIDAD con mismo producto varias veces, el map por productoId
                // puede colisionar;
                // si hay duplicados, usar primer match; no hay duplicados típicos en POS
                // (agrupa).
            }
            // Clamp descuento no mayor a importe línea
            BigDecimal importe = d.precioUnitario().multiply(d.cantidad());
            if (desc.compareTo(importe) > 0)
                desc = importe;
            VentaDetalle det = VentaDetalle.builder()
                    .ventaId(savedVenta.getVentaId())
                    .productoId(d.productoId())
                    .cantidad(d.cantidad())
                    .precioUnitario(d.precioUnitario())
                    .descuentoLinea(desc)
                    .promocionId(pidPromo)
                    .build();
            detalleRepo.save(det);
            detalles.add(det);
        }
        ventaRepo.flush();

        // Registrar uso de promoción (incrementa usos_actual + inserta
        // ven.promocion_usos)
        if (promoIdAAplicar != null && beneficioTotal.compareTo(BigDecimal.ZERO) > 0) {
            try {
                em.createNativeQuery(
                        "SELECT ven.fn_registrar_uso_promo(:p_promo, :p_venta, :p_cliente, :p_desc, :p_usuario)")
                        .setParameter("p_promo", promoIdAAplicar)
                        .setParameter("p_venta", savedVenta.getVentaId())
                        .setParameter("p_cliente", req.clienteId())
                        .setParameter("p_desc", beneficioTotal)
                        .setParameter("p_usuario", UserPrincipal.actual().usuarioId())
                        .getSingleResult();
                em.flush();
            } catch (Exception e) {
                // Si la promo ya agotó límite entre evaluación y registro (concurrencia), el
                // trigger lanza P0400/P0401
                throw new ReglaNegocioException(ErrorCode.REGISTRO_NO_MODIFICABLE);
            }
        }

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

    private Map<Long, BigDecimal> distribuirDescuento(mx.ferreteria.api.ven.entity.Promocion promo,
            List<VenDtos.VentaDetalleRequest> detalles,
            BigDecimal beneficioTotal) {
        Map<Long, BigDecimal> out = new java.util.HashMap<>();
        String tipo = promo.getTipo();
        if ("DESCUENTO_TOTAL_VENTA".equals(tipo)) {
            BigDecimal total = detalles.stream()
                    .map(d -> d.precioUnitario().multiply(d.cantidad()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (total.compareTo(BigDecimal.ZERO) == 0)
                return out;
            BigDecimal acumulado = BigDecimal.ZERO;
            for (int i = 0; i < detalles.size(); i++) {
                var d = detalles.get(i);
                BigDecimal lineaTotal = d.precioUnitario().multiply(d.cantidad());
                BigDecimal parte;
                if (i == detalles.size() - 1) {
                    parte = beneficioTotal.subtract(acumulado);
                } else {
                    parte = beneficioTotal.multiply(lineaTotal)
                            .divide(total, 2, java.math.RoundingMode.HALF_UP);
                    acumulado = acumulado.add(parte);
                }
                if (parte.compareTo(BigDecimal.ZERO) > 0)
                    out.merge(d.productoId(), parte, BigDecimal::add);
            }
            return out;
        }
        // Para tipos por producto, el beneficio ya viene sumado en evaluación;
        // replicamos cálculo por línea
        // Necesitamos categoria por producto para filtrar
        Set<Long> pids = detalles.stream().map(VenDtos.VentaDetalleRequest::productoId).collect(Collectors.toSet());
        Map<Long, Integer> catPorProd = productoRepo.findAllById(pids).stream()
                .filter(p -> p.getCategoria() != null)
                .collect(Collectors.toMap(p -> p.getProductoId(), p -> p.getCategoria().getCategoriaId()));
        // Cargar relaciones de la promo (podría reutilizar pero simple: query repo)
        // Para no hacer N queries extra, asumimos que si la promo tiene listas vacías
        // aplica a todo
        // y si no, solo líneas que matchean
        boolean promoTieneFiltro = false; // se determinará por existencia de relaciones, pero sin acceso directo
        // Truco: inferir por beneficioTotal: si es producto y no hay match, evaluación
        // habría dado 0; como estamos aquí, hay al menos un match
        // Calculamos línea a línea
        for (var d : detalles) {
            Integer cat = catPorProd.get(d.productoId());
            BigDecimal lineaTotal = d.precioUnitario().multiply(d.cantidad());
            BigDecimal b = BigDecimal.ZERO;
            switch (tipo) {
                case "DESCUENTO_PRODUCTO" -> b = promo.getValorPct() != null
                        ? lineaTotal.multiply(promo.getValorPct()).divide(BigDecimal.valueOf(100), 2,
                                java.math.RoundingMode.HALF_UP)
                        : (promo.getValorMonto() != null ? promo.getValorMonto() : BigDecimal.ZERO);
                case "POR_CANTIDAD" -> {
                    if (promo.getCompraMinCantidad() != null
                            && d.cantidad().compareTo(promo.getCompraMinCantidad()) < 0)
                        b = BigDecimal.ZERO;
                    else
                        b = promo.getValorPct() != null
                                ? lineaTotal.multiply(promo.getValorPct()).divide(BigDecimal.valueOf(100), 2,
                                        java.math.RoundingMode.HALF_UP)
                                : (promo.getValorMonto() != null ? promo.getValorMonto() : BigDecimal.ZERO);
                }
                case "PRECIO_ESPECIAL" -> {
                    BigDecimal pe = promo.getPrecioEspecial() != null ? promo.getPrecioEspecial() : BigDecimal.ZERO;
                    BigDecimal ahorro = d.precioUnitario().subtract(pe);
                    if (ahorro.compareTo(BigDecimal.ZERO) < 0)
                        ahorro = BigDecimal.ZERO;
                    b = ahorro.multiply(d.cantidad());
                }
                case "NXM" -> {
                    if (promo.getLleva() == null || promo.getPaga() == null
                            || promo.getLleva().compareTo(BigDecimal.ZERO) <= 0)
                        b = BigDecimal.ZERO;
                    else {
                        long veces = d.cantidad().divide(promo.getLleva(), 0, java.math.RoundingMode.FLOOR).longValue();
                        if (veces <= 0)
                            b = BigDecimal.ZERO;
                        else
                            b = promo.getLleva().subtract(promo.getPaga()).multiply(BigDecimal.valueOf(veces))
                                    .multiply(d.precioUnitario());
                    }
                }
                default -> b = BigDecimal.ZERO;
            }
            if (b.compareTo(BigDecimal.ZERO) > 0)
                out.merge(d.productoId(), b, BigDecimal::add);
        }
        // Ajustar suma a beneficioTotal si hay discrepancia por redondeo
        BigDecimal suma = out.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (suma.compareTo(beneficioTotal) != 0 && !out.isEmpty()) {
            Long first = out.keySet().iterator().next();
            out.put(first, out.get(first).add(beneficioTotal.subtract(suma)));
        }
        return out;
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
                    ? almacenes.get(v.getAlmacenId()).getNombre()
                    : null;
            String formaPagoNombre = formasPago.containsKey(v.getFormaPagoId())
                    ? formasPago.get(v.getFormaPagoId()).getNombre()
                    : null;

            List<VenDtos.VentaDetalleResponse> detalles = detallesByVenta
                    .getOrDefault(v.getVentaId(), List.of()).stream()
                    .map(d -> new VenDtos.VentaDetalleResponse(
                            d.getVentaDetalleId(), d.getProductoId(),
                            productos.containsKey(d.getProductoId())
                                    ? productos.get(d.getProductoId()).getNombre()
                                    : null,
                            d.getCantidad(), d.getPrecioUnitario(),
                            d.getCostoUnitario(), d.getDescuentoLinea(),
                            d.getTotalLinea(), d.getPromocionId()))
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
                            d.getTotalLinea(), d.getPromocionId());
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
        if (c == null)
            return null;
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
