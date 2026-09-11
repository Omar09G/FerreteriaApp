package mx.ferreteria.api.ven.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import mx.ferreteria.api.cat.entity.Cliente;
import mx.ferreteria.api.cat.repo.ClienteRepository;
import mx.ferreteria.api.cat.repo.ProductoRepository;
import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.common.error.ValidacionException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.common.security.UserPrincipal;
import mx.ferreteria.api.ven.dto.VenDtos.PromocionEvaluacionResponse;
import mx.ferreteria.api.ven.dto.VenDtos.PromocionEvaluarRequest;
import mx.ferreteria.api.ven.dto.VenDtos.PromocionRequest;
import mx.ferreteria.api.ven.dto.VenDtos.PromocionResponse;
import mx.ferreteria.api.ven.entity.Promocion;
import mx.ferreteria.api.ven.entity.PromocionCategoria;
import mx.ferreteria.api.ven.entity.PromocionCategoriaId;
import mx.ferreteria.api.ven.entity.PromocionProducto;
import mx.ferreteria.api.ven.entity.PromocionProductoId;
import mx.ferreteria.api.ven.repo.PromocionCategoriaRepository;
import mx.ferreteria.api.ven.repo.PromocionProductoRepository;
import mx.ferreteria.api.ven.repo.PromocionRepository;

/**
 * CRUD de promociones (PLAN §7 ven).
 *
 * <p>
 * Reglas de negocio:
 * <ul>
 * <li>Solo ADMINISTRADOR o GERENTE pueden crear/editar/eliminar.</li>
 * <li>Una promoción con {@code usos_actual > 0} NO se elimina: ya quedó
 * reflejada en ventas; borrarla rompería reportes. Se devuelve
 * {@code REGISTRO_NO_MODIFICABLE} (HTTP 409) con mensaje claro.</li>
 * <li>El {@code usuario_id} creador se conserva en update (trazabilidad).</li>
 * <li>Validación de coherencia por tipo (NXM requiere lleva/paga; el resto
 * exige al menos un valor de descuento). Las CHECKs de BD son red de
 * seguridad y devuelven 422 / 409 vía DbErrorTranslator.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PromocionService {

    private static final Set<String> TIPOS = Set.of(
            "DESCUENTO_PRODUCTO", "DESCUENTO_TOTAL_VENTA", "POR_CANTIDAD", "NXM", "PRECIO_ESPECIAL");
    private static final Set<String> ESTADOS = Set.of("ACTIVA", "PROGRAMADA", "FINALIZADA", "CANCELADA");

    private final PromocionRepository repo;
    private final PromocionProductoRepository productosRepo;
    private final PromocionCategoriaRepository categoriasRepo;
    private final ClienteRepository clienteRepo;
    private final ProductoRepository productoRepo;

    @PersistenceContext
    private EntityManager em;

    public Page<PromocionResponse> listar(String nombre, String tipo, String estado,
            Instant desde, Instant hasta, Pageable pageable) {
        Specification<Promocion> spec = (root, q, cb) -> cb.conjunction();

        if (nombre != null && !nombre.isBlank()) {
            String patron = "%" + nombre.trim().toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("nombre")), patron));
        }
        if (tipo != null && !tipo.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("tipo"), tipo));
        }
        if (estado != null && !estado.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("estado"), estado));
        }
        if (desde != null) {
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.isNull(root.get("vigenciaHasta")),
                    cb.greaterThanOrEqualTo(root.get("vigenciaHasta"), desde)));
        }
        if (hasta != null) {
            spec = spec.and((root, q, cb) -> cb.lessThanOrEqualTo(root.get("vigenciaDesde"), hasta));
        }
        // Pageable order by en todos los casos por fecha desde desc
        pageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "vigenciaDesde"));

        Page<Promocion> page = repo.findAll(spec, pageable);
        if (page.isEmpty() || page.getContent().size() == 1) {
            List<PromocionResponse> content = page.getContent().stream().map(this::toResponse).toList();
            return new PageImpl<>(content, pageable, page.getTotalElements());
        }
        // Batch: 2 queries vs 2*N
        List<Long> pids = page.getContent().stream().map(Promocion::getPromocionId).toList();
        Map<Long, List<Long>> productosByPromo = productosRepo.findByPromocionIdIn(pids).stream()
                .collect(Collectors.groupingBy(PromocionProducto::getPromocionId,
                        Collectors.mapping(PromocionProducto::getProductoId, Collectors.toList())));
        Map<Long, List<Integer>> categoriasByPromo = categoriasRepo.findByPromocionIdIn(pids).stream()
                .collect(Collectors.groupingBy(PromocionCategoria::getPromocionId,
                        Collectors.mapping(PromocionCategoria::getCategoriaId, Collectors.toList())));
        List<PromocionResponse> content = page.getContent().stream()
                .map(p -> toResponse(p, productosByPromo, categoriasByPromo)).toList();
        return new PageImpl<>(content, pageable, page.getTotalElements());
    }

    public PromocionResponse obtener(long promocionId) {
        return toResponse(exigir(promocionId));
    }

    @Transactional
    public PromocionResponse crear(PromocionRequest req) {
        validar(req);
        int usuarioId = UserPrincipal.actual().usuarioId();
        Promocion p = Promocion.builder()
                .nombre(req.nombre().trim())
                .descripcion(req.descripcion())
                .tipo(req.tipo())
                .valorPct(req.valorPct())
                .valorMonto(req.valorMonto())
                .precioEspecial(req.precioEspecial())
                .compraMinTotal(req.compraMinTotal())
                .compraMinCantidad(req.compraMinCantidad())
                .lleva(req.lleva())
                .paga(req.paga())
                .maxUsosTotal(req.maxUsosTotal())
                .maxUsosCliente(req.maxUsosCliente())
                .usosActual(0)
                .vigenciaDesde(req.vigenciaDesde() != null ? req.vigenciaDesde() : Instant.now())
                .vigenciaHasta(req.vigenciaHasta())
                .diasSemana(req.diasSemana() == null || req.diasSemana().isEmpty()
                        ? List.of((short) 1, (short) 2, (short) 3, (short) 4, (short) 5, (short) 6, (short) 7)
                        : req.diasSemana())
                .horaDesde(req.horaDesde())
                .horaHasta(req.horaHasta())
                .soloMayoristas(Boolean.TRUE.equals(req.soloMayoristas()))
                .estado(req.estado() == null ? "ACTIVA" : req.estado())
                .usuarioId(usuarioId)
                .creadoEn(Instant.now())
                .build();
        p = repo.save(p);
        guardarRelaciones(p.getPromocionId(), req.productos(), req.categorias());
        return toResponse(p);
    }

    @Transactional
    public PromocionResponse actualizar(long promocionId, PromocionRequest req) {
        Promocion p = exigir(promocionId);
        validar(req);
        p.setNombre(req.nombre().trim());
        p.setDescripcion(req.descripcion());
        p.setTipo(req.tipo());
        p.setValorPct(req.valorPct());
        p.setValorMonto(req.valorMonto());
        p.setPrecioEspecial(req.precioEspecial());
        p.setCompraMinTotal(req.compraMinTotal());
        p.setCompraMinCantidad(req.compraMinCantidad());
        p.setLleva(req.lleva());
        p.setPaga(req.paga());
        p.setMaxUsosTotal(req.maxUsosTotal());
        p.setMaxUsosCliente(req.maxUsosCliente());
        if (req.vigenciaDesde() != null)
            p.setVigenciaDesde(req.vigenciaDesde());
        p.setVigenciaHasta(req.vigenciaHasta());
        if (req.diasSemana() != null && !req.diasSemana().isEmpty())
            p.setDiasSemana(req.diasSemana());
        p.setHoraDesde(req.horaDesde());
        p.setHoraHasta(req.horaHasta());
        p.setSoloMayoristas(Boolean.TRUE.equals(req.soloMayoristas()));
        if (req.estado() != null)
            p.setEstado(req.estado());
        // usuarioId, usosActual, creadoEn son inmutables.
        repo.save(p);
        guardarRelaciones(promocionId, req.productos(), req.categorias());
        return toResponse(p);
    }

    @Transactional
    public void eliminar(long promocionId) {
        Promocion p = exigir(promocionId);
        if (p.getUsosActual() != null && p.getUsosActual() > 0) {
            throw new ReglaNegocioException(ErrorCode.REGISTRO_NO_MODIFICABLE);
        }
        productosRepo.deleteByPromocionId(promocionId);
        categoriasRepo.deleteByPromocionId(promocionId);
        repo.delete(p);
    }

    /* ---------- helpers ---------- */

    private Promocion exigir(long promocionId) {
        return repo.findById(promocionId)
                .orElseThrow(() -> new ReglaNegocioException(ErrorCode.RECURSO_NO_ENCONTRADO));
    }

    private void validar(PromocionRequest req) {
        if (!TIPOS.contains(req.tipo())) {
            throw new ValidacionException(ErrorCode.VALOR_INVALIDO);
        }
        if (req.estado() != null && !ESTADOS.contains(req.estado())) {
            throw new ValidacionException(ErrorCode.VALOR_INVALIDO);
        }
        switch (req.tipo()) {
            case "NXM" -> {
                if (req.lleva() == null || req.paga() == null
                        || req.lleva().signum() <= 0
                        || req.paga().signum() <= 0
                        || req.paga().compareTo(req.lleva()) >= 0) {
                    throw new ValidacionException(ErrorCode.VALOR_INVALIDO);
                }
            }
            case "DESCUENTO_PRODUCTO", "DESCUENTO_TOTAL_VENTA", "POR_CANTIDAD" -> {
                if (req.valorPct() == null && req.valorMonto() == null) {
                    throw new ValidacionException(ErrorCode.VALOR_INVALIDO);
                }
            }
            case "PRECIO_ESPECIAL" -> {
                if (req.precioEspecial() == null || req.precioEspecial().signum() < 0) {
                    throw new ValidacionException(ErrorCode.VALOR_INVALIDO);
                }
            }
            default -> {
            }
        }
        if (req.diasSemana() != null) {
            for (Short d : req.diasSemana()) {
                if (d == null || d < 1 || d > 7) {
                    throw new ValidacionException(ErrorCode.VALOR_INVALIDO);
                }
            }
        }
    }

    private void guardarRelaciones(long promocionId, List<Long> productos, List<Integer> categorias) {
        Set<Long> nuevosProductos = productos == null ? Set.of() : new HashSet<>(productos);
        Set<Integer> nuevasCategorias = categorias == null ? Set.of() : new HashSet<>(categorias);

        Set<Long> actualesProductos = productosRepo.findByPromocionId(promocionId).stream()
                .map(PromocionProducto::getProductoId).collect(java.util.stream.Collectors.toSet());
        Set<Integer> actualesCategorias = categoriasRepo.findByPromocionId(promocionId).stream()
                .map(PromocionCategoria::getCategoriaId).collect(java.util.stream.Collectors.toSet());

        for (Long id : nuevosProductos) {
            if (!actualesProductos.contains(id)) {
                productosRepo.save(PromocionProducto.builder()
                        .promocionId(promocionId).productoId(id).build());
            }
        }
        for (Long id : actualesProductos) {
            if (!nuevosProductos.contains(id)) {
                productosRepo.deleteById(new PromocionProductoId(promocionId, id));
            }
        }
        for (Integer id : nuevasCategorias) {
            if (!actualesCategorias.contains(id)) {
                categoriasRepo.save(PromocionCategoria.builder()
                        .promocionId(promocionId).categoriaId(id).build());
            }
        }
        for (Integer id : actualesCategorias) {
            if (!nuevasCategorias.contains(id)) {
                categoriasRepo.deleteById(new PromocionCategoriaId(promocionId, id));
            }
        }
    }

    private PromocionResponse toResponse(Promocion p) {
        List<Long> productos = productosRepo.findByPromocionId(p.getPromocionId()).stream()
                .map(PromocionProducto::getProductoId).toList();
        List<Integer> categorias = categoriasRepo.findByPromocionId(p.getPromocionId()).stream()
                .map(PromocionCategoria::getCategoriaId).toList();
        return toResponse(p, Map.of(p.getPromocionId(), productos), Map.of(p.getPromocionId(), categorias));
    }

    private PromocionResponse toResponse(Promocion p,
            Map<Long, List<Long>> productosByPromo, Map<Long, List<Integer>> categoriasByPromo) {
        List<Long> productos = productosByPromo.getOrDefault(p.getPromocionId(), List.of());
        List<Integer> categorias = categoriasByPromo.getOrDefault(p.getPromocionId(), List.of());
        return new PromocionResponse(
                p.getPromocionId(), p.getNombre(), p.getDescripcion(), p.getTipo(),
                p.getValorPct(), p.getValorMonto(), p.getPrecioEspecial(),
                p.getCompraMinTotal(), p.getCompraMinCantidad(), p.getLleva(), p.getPaga(),
                p.getMaxUsosTotal(), p.getMaxUsosCliente(), p.getUsosActual(),
                p.getVigenciaDesde(), p.getVigenciaHasta(),
                p.getDiasSemana(), p.getHoraDesde(), p.getHoraHasta(),
                p.getSoloMayoristas(), p.getEstado(),
                productos, categorias, p.getUsuarioId(), p.getCreadoEn());
    }

    /* ─── Evaluación para POS (diagnóstico) ─────────────────────────── */

    @Transactional(readOnly = true)
    public List<PromocionEvaluacionResponse> evaluar(PromocionEvaluarRequest req) {
        List<Promocion> todas = repo.findAll();
        if (todas.isEmpty())
            return List.of();

        // Batch de relaciones
        List<Long> pids = todas.stream().map(Promocion::getPromocionId).toList();
        Map<Long, List<Long>> productosByPromo = productosRepo.findByPromocionIdIn(pids).stream()
                .collect(Collectors.groupingBy(PromocionProducto::getPromocionId,
                        Collectors.mapping(PromocionProducto::getProductoId, Collectors.toList())));
        Map<Long, List<Integer>> categoriasByPromo = categoriasRepo.findByPromocionIdIn(pids).stream()
                .collect(Collectors.groupingBy(PromocionCategoria::getPromocionId,
                        Collectors.mapping(PromocionCategoria::getCategoriaId, Collectors.toList())));

        // Datos del carrito
        BigDecimal total = req.items().stream()
                .map(it -> it.precioUnitario().multiply(it.cantidad()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCantidad = req.items().stream()
                .map(it -> it.cantidad())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Set<Long> productosEnCarrito = req.items().stream().map(it -> it.productoId()).collect(Collectors.toSet());
        // Mapa producto -> categoria
        Map<Long, Integer> categoriaPorProducto = Map.of();
        if (!productosEnCarrito.isEmpty()) {
            categoriaPorProducto = productoRepo.findAllById(productosEnCarrito).stream()
                    .filter(p -> p.getCategoria() != null)
                    .collect(Collectors.toMap(p -> p.getProductoId(), p -> p.getCategoria().getCategoriaId()));
        }

        Cliente cliente = null;
        if (req.clienteId() != null) {
            cliente = clienteRepo.findById(req.clienteId()).orElse(null);
        }

        ZoneId zona = ZoneId.of("America/Mexico_City");
        ZonedDateTime ahoraZoned = ZonedDateTime.now(zona);
        Instant ahora = ahoraZoned.toInstant();
        int dow = ahoraZoned.getDayOfWeek().getValue(); // 1=Lunes .. 7=Domingo = ISODOW
        LocalTime horaActual = ahoraZoned.toLocalTime();

        List<PromocionEvaluacionResponse> out = new ArrayList<>();
        for (Promocion p : todas) {
            List<Long> prods = productosByPromo.getOrDefault(p.getPromocionId(), List.of());
            List<Integer> cats = categoriasByPromo.getOrDefault(p.getPromocionId(), List.of());
            List<String> fallos = new ArrayList<>();
            boolean aplica = true;

            if (!"ACTIVA".equals(p.getEstado())) {
                fallos.add("Estado no ACTIVA (" + p.getEstado() + ")");
                aplica = false;
            }
            if (p.getVigenciaDesde() != null && ahora.isBefore(p.getVigenciaDesde())) {
                fallos.add("Aún no inicia (desde " + p.getVigenciaDesde() + ")");
                aplica = false;
            }
            if (p.getVigenciaHasta() != null && ahora.isAfter(p.getVigenciaHasta())) {
                fallos.add("Vigencia vencida (hasta " + p.getVigenciaHasta() + ")");
                aplica = false;
            }
            if (p.getDiasSemana() != null && !p.getDiasSemana().isEmpty() && !p.getDiasSemana().contains((short) dow)) {
                fallos.add("Día no permitido (hoy " + dow + " ∉ " + p.getDiasSemana() + ")");
                aplica = false;
            }
            if (p.getHoraDesde() != null) {
                LocalTime desde = p.getHoraDesde();
                LocalTime hasta = p.getHoraHasta() != null ? p.getHoraHasta() : LocalTime.of(23, 59, 59);
                boolean enHora = !horaActual.isBefore(desde) && !horaActual.isAfter(hasta);
                if (!enHora) {
                    fallos.add("Fuera de horario (" + desde + "–" + hasta + ", ahora "
                            + horaActual.withSecond(0).withNano(0) + ")");
                    aplica = false;
                }
            }
            if (Boolean.TRUE.equals(p.getSoloMayoristas())) {
                boolean esMayorista = cliente != null && Boolean.TRUE.equals(cliente.getEsMayorista());
                if (!esMayorista) {
                    fallos.add("Solo mayoristas" + (cliente == null ? " (sin cliente)" : ""));
                    aplica = false;
                }
            }
            if (p.getMaxUsosTotal() != null && p.getUsosActual() != null && p.getUsosActual() >= p.getMaxUsosTotal()) {
                fallos.add("Límite total alcanzado (" + p.getUsosActual() + "/" + p.getMaxUsosTotal() + ")");
                aplica = false;
            }
            if (p.getMaxUsosCliente() != null && req.clienteId() != null) {
                long usosCliente = contarUsosCliente(p.getPromocionId(), req.clienteId());
                if (usosCliente >= p.getMaxUsosCliente()) {
                    fallos.add("Límite por cliente alcanzado (" + usosCliente + "/" + p.getMaxUsosCliente() + ")");
                    aplica = false;
                }
            }
            if (p.getCompraMinTotal() != null && total.compareTo(p.getCompraMinTotal()) < 0) {
                fallos.add("Compra mínima $" + p.getCompraMinTotal() + " no alcanzada (actual $"
                        + total.setScale(2, RoundingMode.HALF_UP) + ")");
                aplica = false;
            }
            if (p.getCompraMinCantidad() != null && totalCantidad.compareTo(p.getCompraMinCantidad()) < 0) {
                fallos.add("Cantidad mínima " + p.getCompraMinCantidad() + " no alcanzada (actual "
                        + totalCantidad.stripTrailingZeros().toPlainString() + ")");
                aplica = false;
            }
            // Filtro producto/categoría: si listas vacías, considerar que aplica a todo
            // solo para DESCUENTO_TOTAL_VENTA
            boolean requiereProducto = !prods.isEmpty() || !cats.isEmpty();
            boolean hayMatch = false;
            if (!requiereProducto) {
                // Vacío = aplica a todos solo si es total venta; para tipos de producto
                // consideramos que falta configuración
                if ("DESCUENTO_TOTAL_VENTA".equals(p.getTipo())) {
                    hayMatch = true;
                } else {
                    // Para tipos de producto sin productos/categorías, no puede aplicar (no hay
                    // matching explícito)
                    // No marcamos fallo duro, pero el beneficio será 0 si no hay líneas que
                    // califiquen
                    hayMatch = !req.items().isEmpty();
                }
            } else {
                for (var it : req.items()) {
                    Long pid = it.productoId();
                    Integer cat = categoriaPorProducto.get(pid);
                    if (prods.contains(pid) || (cat != null && cats.contains(cat))) {
                        hayMatch = true;
                        break;
                    }
                }
                if (!hayMatch) {
                    fallos.add("Ningún producto/categoría del ticket coincide (requiere prod " + prods + " o cat "
                            + cats + ")");
                    aplica = false;
                }
            }

            BigDecimal beneficio = BigDecimal.ZERO;
            if (aplica && hayMatch) {
                beneficio = calcularBeneficio(p, req.items(), categoriaPorProducto, total, totalCantidad, prods, cats);
                if (beneficio.compareTo(BigDecimal.ZERO) <= 0) {
                    // Si el cálculo da 0, marcar como no aplica con motivo
                    fallos.add("Cálculo de beneficio dio $0 (verifica lleva/paga o precio especial)");
                    aplica = false;
                }
            }

            String motivo;
            if (aplica) {
                motivo = "Aplica — beneficio estimado $" + beneficio.setScale(2, RoundingMode.HALF_UP);
            } else {
                motivo = String.join("; ", fallos);
            }

            out.add(new PromocionEvaluacionResponse(
                    p.getPromocionId(), p.getNombre(), p.getTipo(), p.getEstado(),
                    aplica, motivo, beneficio,
                    p.getValorPct(), p.getValorMonto(),
                    p.getCompraMinTotal(), p.getCompraMinCantidad(),
                    p.getMaxUsosTotal(), p.getMaxUsosCliente(), p.getUsosActual(),
                    p.getVigenciaDesde(), p.getVigenciaHasta(),
                    p.getDiasSemana(), p.getHoraDesde(), p.getHoraHasta(),
                    p.getSoloMayoristas(),
                    prods, cats));
        }
        // Orden: las que aplican primero, luego por beneficio desc
        out.sort((a, b) -> {
            int cmp = Boolean.compare(b.aplica(), a.aplica());
            if (cmp != 0)
                return cmp;
            return b.beneficioEstimado().compareTo(a.beneficioEstimado());
        });
        return out;
    }

    private long contarUsosCliente(Long promocionId, Long clienteId) {
        try {
            Object res = em
                    .createNativeQuery(
                            "SELECT COUNT(*) FROM ven.promocion_usos WHERE promocion_id = :pid AND cliente_id = :cid")
                    .setParameter("pid", promocionId)
                    .setParameter("cid", clienteId)
                    .getSingleResult();
            if (res instanceof Number n)
                return n.longValue();
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private BigDecimal calcularBeneficio(Promocion p,
            List<mx.ferreteria.api.ven.dto.VenDtos.PromocionEvaluarItem> items,
            Map<Long, Integer> catPorProd, BigDecimal total, BigDecimal totalCant,
            List<Long> prods, List<Integer> cats) {
        return switch (p.getTipo()) {
            case "DESCUENTO_TOTAL_VENTA" -> {
                BigDecimal base = total;
                BigDecimal pctBenef = p.getValorPct() != null
                        ? base.multiply(p.getValorPct()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                        : null;
                BigDecimal montoBenef = p.getValorMonto();
                BigDecimal elegido;
                if (pctBenef != null && montoBenef != null) {
                    // Si ambos, usar el menor (monto como tope)
                    elegido = pctBenef.min(montoBenef);
                } else if (pctBenef != null) {
                    elegido = pctBenef;
                } else {
                    elegido = montoBenef != null ? montoBenef : BigDecimal.ZERO;
                }
                yield elegido;
            }
            case "DESCUENTO_PRODUCTO" -> {
                BigDecimal sum = BigDecimal.ZERO;
                for (var it : items) {
                    boolean match = prods.contains(it.productoId()) || cats.contains(catPorProd.get(it.productoId()));
                    if (!match && (!prods.isEmpty() || !cats.isEmpty()))
                        continue;
                    BigDecimal lineaTotal = it.precioUnitario().multiply(it.cantidad());
                    BigDecimal b = p.getValorPct() != null
                            ? lineaTotal.multiply(p.getValorPct()).divide(BigDecimal.valueOf(100), 2,
                                    RoundingMode.HALF_UP)
                            : (p.getValorMonto() != null ? p.getValorMonto() : BigDecimal.ZERO);
                    sum = sum.add(b);
                }
                yield sum;
            }
            case "POR_CANTIDAD" -> {
                boolean cumpleCant = p.getCompraMinCantidad() == null
                        || totalCant.compareTo(p.getCompraMinCantidad()) >= 0;
                if (!cumpleCant)
                    yield BigDecimal.ZERO;
                BigDecimal sum = BigDecimal.ZERO;
                for (var it : items) {
                    boolean match = prods.contains(it.productoId()) || cats.contains(catPorProd.get(it.productoId()));
                    if (!match && (!prods.isEmpty() || !cats.isEmpty()))
                        continue;
                    BigDecimal lineaTotal = it.precioUnitario().multiply(it.cantidad());
                    BigDecimal b = p.getValorPct() != null
                            ? lineaTotal.multiply(p.getValorPct()).divide(BigDecimal.valueOf(100), 2,
                                    RoundingMode.HALF_UP)
                            : (p.getValorMonto() != null ? p.getValorMonto() : BigDecimal.ZERO);
                    sum = sum.add(b);
                }
                yield sum;
            }
            case "PRECIO_ESPECIAL" -> {
                BigDecimal sum = BigDecimal.ZERO;
                BigDecimal precioEsp = p.getPrecioEspecial() != null ? p.getPrecioEspecial() : BigDecimal.ZERO;
                for (var it : items) {
                    boolean match = prods.contains(it.productoId()) || cats.contains(catPorProd.get(it.productoId()));
                    if (!match && (!prods.isEmpty() || !cats.isEmpty()))
                        continue;
                    BigDecimal ahorroUnit = it.precioUnitario().subtract(precioEsp);
                    if (ahorroUnit.compareTo(BigDecimal.ZERO) < 0)
                        ahorroUnit = BigDecimal.ZERO;
                    sum = sum.add(ahorroUnit.multiply(it.cantidad()));
                }
                yield sum;
            }
            case "NXM" -> {
                BigDecimal sum = BigDecimal.ZERO;
                BigDecimal lleva = p.getLleva();
                BigDecimal paga = p.getPaga();
                if (lleva == null || paga == null || lleva.compareTo(BigDecimal.ZERO) <= 0)
                    yield BigDecimal.ZERO;
                for (var it : items) {
                    boolean match = prods.contains(it.productoId()) || cats.contains(catPorProd.get(it.productoId()));
                    if (!match && (!prods.isEmpty() || !cats.isEmpty()))
                        continue;
                    long veces = it.cantidad().divide(lleva, 0, RoundingMode.FLOOR).longValue();
                    if (veces <= 0)
                        continue;
                    BigDecimal gratis = lleva.subtract(paga);
                    sum = sum.add(gratis.multiply(BigDecimal.valueOf(veces)).multiply(it.precioUnitario()));
                }
                yield sum;
            }
            default -> BigDecimal.ZERO;
        };
    }
}