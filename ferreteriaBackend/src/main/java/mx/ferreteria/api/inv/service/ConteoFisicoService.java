package mx.ferreteria.api.inv.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
import mx.ferreteria.api.cat.entity.Producto;
import mx.ferreteria.api.cat.repo.ProductoRepository;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.common.security.UserPrincipal;
import mx.ferreteria.api.inv.dto.InvDtos.ConteoFisicoDetalleRequest;
import mx.ferreteria.api.inv.dto.InvDtos.ConteoFisicoDetalleResponse;
import mx.ferreteria.api.inv.dto.InvDtos.ConteoFisicoRequest;
import mx.ferreteria.api.inv.dto.InvDtos.ConteoFisicoResponse;
import mx.ferreteria.api.inv.entity.Almacen;
import mx.ferreteria.api.inv.entity.ConteoFisico;
import mx.ferreteria.api.inv.entity.ConteoFisicoDetalle;
import mx.ferreteria.api.inv.entity.Inventario;
import mx.ferreteria.api.inv.entity.InventarioId;
import mx.ferreteria.api.inv.repo.AlmacenRepository;
import mx.ferreteria.api.inv.repo.ConteoFisicoDetalleRepository;
import mx.ferreteria.api.inv.repo.ConteoFisicoRepository;
import mx.ferreteria.api.inv.repo.InventarioRepository;
import mx.ferreteria.api.seg.service.SegAdminGateway;

@Service
@RequiredArgsConstructor
@Transactional
public class ConteoFisicoService {

    private final ConteoFisicoRepository repo;
    private final ConteoFisicoDetalleRepository detalleRepo;
    private final InventarioRepository inventarioRepo;
    private final AlmacenRepository almacenRepo;
    private final ProductoRepository productoRepo;
    private final SegAdminGateway usuarios;

    private static final Instant DESDE_MIN = Instant.parse("1970-01-01T00:00:00Z");
    private static final Instant HASTA_MAX = Instant.parse("2999-12-31T00:00:00Z");

    @Transactional(readOnly = true)
    public Page<ConteoFisicoResponse> list(Integer almacenId, String estado, Long productoId,
            LocalDate desde, LocalDate hasta, Pageable pageable) {
        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw new ReglaNegocioException(ErrorCode.VALOR_INVALIDO);
        }
        Instant desdeI = inicioDelDia(desde);
        Instant hastaI = inicioDelDiaSiguiente(hasta);
        Page<ConteoFisico> page = (productoId != null)
                ? repo.filtrarPorProducto(
                        normalizarAlmacen(almacenId), normalizarEstado(estado), productoId,
                        desdeI, hastaI, pageable)
                : repo.filtrar(
                        normalizarAlmacen(almacenId), normalizarEstado(estado),
                        desdeI, hastaI, pageable);
        if (page.isEmpty()) {
            return Page.empty(pageable);
        }
        Contexto ctx = cargarContexto(page.getContent());
        List<ConteoFisicoResponse> content = page.getContent().stream()
                .map(c -> toResponse(c, ctx))
                .toList();
        return new PageImpl<>(content, pageable, page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ConteoFisicoResponse getById(Long id) {
        ConteoFisico entity = repo.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        return toResponse(entity, cargarContexto(List.of(entity)));
    }

    public ConteoFisicoResponse create(ConteoFisicoRequest req) {
        if (!almacenRepo.existsById(req.almacenId())) {
            throw new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO);
        }
        if (req.detalles() == null || req.detalles().isEmpty()) {
            throw new ReglaNegocioException(ErrorCode.VALOR_INVALIDO);
        }
        // Validar productos en una sola query (vs N findById).
        Set<Long> productoIds = req.detalles().stream()
                .map(ConteoFisicoDetalleRequest::productoId).collect(Collectors.toSet());
        Set<Long> existentes = productoRepo.findAllById(productoIds).stream()
                .map(Producto::getProductoId).collect(Collectors.toSet());
        Set<Long> faltantes = productoIds.stream()
                .filter(pid -> !existentes.contains(pid)).collect(Collectors.toSet());
        if (!faltantes.isEmpty()) {
            throw new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO,
                    "productos inexistentes: " + faltantes);
        }

        ConteoFisico conteo = ConteoFisico.builder()
                .almacenId(req.almacenId())
                .observaciones(req.observaciones())
                .usuarioId(UserPrincipal.actual().usuarioId())
                .build();
        ConteoFisico savedConteo = repo.save(conteo);

        List<ConteoFisicoDetalle> detalles = req.detalles().stream()
                .map(d -> ConteoFisicoDetalle.builder()
                        .conteoId(savedConteo.getConteoId())
                        .productoId(d.productoId())
                        .cantidadSistema(stockSistema(d.productoId(), req.almacenId()))
                        .cantidadFisica(d.cantidadFisica())
                        .build())
                .toList();
        detalleRepo.saveAll(detalles);

        return toResponse(savedConteo, cargarContexto(List.of(savedConteo)));
    }

    // ── Carga batch (evita N+1 en listados) ──────────────────—───────

    private record Contexto(
            Map<Long, List<ConteoFisicoDetalle>> detallesPorConteo,
            Map<Long, Producto> productos,
            Map<Integer, Almacen> almacenes,
            Map<Integer, String> usuariosPorId) {
    }

    private Contexto cargarContexto(List<ConteoFisico> conteos) {
        List<Long> conteoIds = conteos.stream().map(ConteoFisico::getConteoId).toList();
        List<ConteoFisicoDetalle> detalles = detalleRepo.findByConteoIdIn(conteoIds);
        Map<Long, List<ConteoFisicoDetalle>> detallesPorConteo = detalles.stream()
                .collect(Collectors.groupingBy(ConteoFisicoDetalle::getConteoId));

        Set<Long> productoIds = detalles.stream()
                .map(ConteoFisicoDetalle::getProductoId).collect(Collectors.toSet());
        Map<Long, Producto> productos = productoIds.isEmpty() ? Map.of()
                : productoRepo.findAllById(productoIds).stream()
                        .collect(Collectors.toMap(Producto::getProductoId, Function.identity()));

        Set<Integer> almacenIds = conteos.stream()
                .map(ConteoFisico::getAlmacenId).collect(Collectors.toSet());
        Map<Integer, Almacen> almacenes = almacenRepo.findAllById(almacenIds).stream()
                .collect(Collectors.toMap(Almacen::getAlmacenId, Function.identity()));

        Set<Integer> usuarioIds = conteos.stream()
                .map(ConteoFisico::getUsuarioId).collect(Collectors.toSet());
        Map<Integer, String> usuariosPorId = usuarioIds.stream()
                .collect(Collectors.toMap(Function.identity(), this::nombreUsuario));

        return new Contexto(detallesPorConteo, productos, almacenes, usuariosPorId);
    }

    private String nombreUsuario(Integer usuarioId) {
        if (usuarioId == null) {
            return null;
        }
        return usuarios.findUsuarioById(usuarioId).map(SegAdminGateway.UsuarioRow::username).orElse(null);
    }

    private BigDecimal stockSistema(Long productoId, Integer almacenId) {
        Inventario inventario = inventarioRepo.findById(new InventarioId(productoId, almacenId)).orElse(null);
        return inventario != null && inventario.getStock() != null ? inventario.getStock() : BigDecimal.ZERO;
    }

    private ConteoFisicoResponse toResponse(ConteoFisico c, Contexto ctx) {
        Almacen almacen = ctx.almacenes().get(c.getAlmacenId());
        List<ConteoFisicoDetalle> detalles = ctx.detallesPorConteo()
                .getOrDefault(c.getConteoId(), List.of());
        List<ConteoFisicoDetalleResponse> detalleResponses = detalles.stream()
                .map(d -> {
                    Producto p = ctx.productos().get(d.getProductoId());
                    BigDecimal diferencia = d.getCantidadFisica() != null && d.getCantidadSistema() != null
                            ? d.getCantidadFisica().subtract(d.getCantidadSistema())
                            : null;
                    return new ConteoFisicoDetalleResponse(
                            d.getProductoId(),
                            p != null ? p.getCodigo() : null,
                            p != null ? p.getNombre() : null,
                            d.getCantidadSistema(),
                            d.getCantidadFisica(),
                            diferencia);
                })
                .toList();
        BigDecimal diferenciaTotal = detalleResponses.stream()
                .map(ConteoFisicoDetalleResponse::diferencia)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ConteoFisicoResponse(
                c.getConteoId(),
                c.getAlmacenId(),
                almacen != null ? almacen.getNombre() : null,
                c.getFecha(),
                c.getEstado(),
                c.getUsuarioId(),
                ctx.usuariosPorId().get(c.getUsuarioId()),
                c.getObservaciones(),
                detalleResponses.size(),
                diferenciaTotal,
                detalleResponses);
    }

    private Integer normalizarAlmacen(Integer almacenId) {
        return (almacenId == null || almacenId == 0) ? null : almacenId;
    }

    private String normalizarEstado(String estado) {
        return (estado == null || estado.isBlank()) ? null : estado.trim().toUpperCase();
    }

    private Instant inicioDelDia(LocalDate fecha) {
        return fecha == null ? DESDE_MIN : fecha.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private Instant inicioDelDiaSiguiente(LocalDate fecha) {
        return fecha == null ? HASTA_MAX : fecha.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
