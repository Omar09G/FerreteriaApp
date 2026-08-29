package mx.ferreteria.api.ven.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.common.error.ValidacionException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.common.security.UserPrincipal;
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
 * <p>Reglas de negocio:
 * <ul>
 *   <li>Solo ADMINISTRADOR o GERENTE pueden crear/editar/eliminar.</li>
 *   <li>Una promoción con {@code usos_actual > 0} NO se elimina: ya quedó
 *       reflejada en ventas; borrarla rompería reportes. Se devuelve
 *       {@code REGISTRO_NO_MODIFICABLE} (HTTP 409) con mensaje claro.</li>
 *   <li>El {@code usuario_id} creador se conserva en update (trazabilidad).</li>
 *   <li>Validación de coherencia por tipo (NXM requiere lleva/paga; el resto
 *       exige al menos un valor de descuento). Las CHECKs de BD son red de
 *       seguridad y devuelven 422 / 409 vía DbErrorTranslator.</li>
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

        Page<Promocion> page = repo.findAll(spec, pageable);
        List<PromocionResponse> content = page.getContent().stream().map(this::toResponse).toList();
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
        if (req.vigenciaDesde() != null) p.setVigenciaDesde(req.vigenciaDesde());
        p.setVigenciaHasta(req.vigenciaHasta());
        if (req.diasSemana() != null && !req.diasSemana().isEmpty()) p.setDiasSemana(req.diasSemana());
        p.setHoraDesde(req.horaDesde());
        p.setHoraHasta(req.horaHasta());
        p.setSoloMayoristas(Boolean.TRUE.equals(req.soloMayoristas()));
        if (req.estado() != null) p.setEstado(req.estado());
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
            default -> {}
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
}