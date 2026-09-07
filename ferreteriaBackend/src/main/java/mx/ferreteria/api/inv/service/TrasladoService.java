package mx.ferreteria.api.inv.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import java.util.Set;
import java.util.function.Function;

import org.springframework.data.domain.PageImpl;

import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.common.security.UserPrincipal;
import mx.ferreteria.api.inv.dto.InvDtos.TrasladoDetalleRequest;
import mx.ferreteria.api.inv.dto.InvDtos.TrasladoDetalleResponse;
import mx.ferreteria.api.inv.dto.InvDtos.TrasladoRequest;
import mx.ferreteria.api.inv.dto.InvDtos.TrasladoResponse;
import mx.ferreteria.api.inv.entity.Almacen;
import mx.ferreteria.api.inv.entity.MovimientoInventario;
import mx.ferreteria.api.inv.entity.Traslado;
import mx.ferreteria.api.inv.entity.TrasladoDetalle;
import mx.ferreteria.api.inv.repo.AlmacenRepository;
import mx.ferreteria.api.inv.repo.MovimientoInventarioRepository;
import mx.ferreteria.api.inv.repo.TrasladoDetalleRepository;
import mx.ferreteria.api.inv.repo.TrasladoRepository;
import mx.ferreteria.api.cat.entity.Producto;
import mx.ferreteria.api.cat.repo.ProductoRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class TrasladoService {

        private final TrasladoRepository repo;
        private final TrasladoDetalleRepository detalleRepo;
        private final MovimientoInventarioRepository movimientoRepo;
        private final AlmacenRepository almacenRepo;
        private final ProductoRepository productoRepo;
        private final mx.ferreteria.api.cat.repo.MotivoMovimientoRepository motivoRepo;

        @Transactional(readOnly = true)
        public Page<TrasladoResponse> list(Pageable pageable) {
                Page<Traslado> page = repo.findAllByOrderByCreadoEnDesc(pageable);
                if (page.isEmpty() || page.getContent().size() == 1) {
                        return page.map(t -> {
                                List<TrasladoDetalle> detalles = detalleRepo.findByTrasladoId(t.getTrasladoId());
                                return toResponse(t, detalles);
                        });
                }
                List<Long> trasladoIds = page.getContent().stream().map(Traslado::getTrasladoId).toList();
                List<TrasladoDetalle> allDetalles = detalleRepo.findByTrasladoIdIn(trasladoIds);
                Map<Long, List<TrasladoDetalle>> detallesByTraslado = allDetalles.stream()
                                .collect(Collectors.groupingBy(TrasladoDetalle::getTrasladoId));
                Set<Integer> almacenIds = page.getContent().stream()
                                .flatMap(t -> java.util.stream.Stream.of(t.getAlmacenOrigen(), t.getAlmacenDestino()))
                                .collect(Collectors.toSet());
                Map<Integer, Almacen> almacenes = almacenRepo.findAllById(almacenIds).stream()
                                .collect(Collectors.toMap(Almacen::getAlmacenId, Function.identity()));
                Set<Long> productoIds = allDetalles.stream().map(TrasladoDetalle::getProductoId)
                                .collect(Collectors.toSet());
                Map<Long, mx.ferreteria.api.cat.entity.Producto> productos = productoIds.isEmpty() ? Map.of()
                                : productoRepo.findAllById(productoIds).stream()
                                                .collect(Collectors.toMap(
                                                                mx.ferreteria.api.cat.entity.Producto::getProductoId,
                                                                Function.identity()));
                List<TrasladoResponse> content = page.getContent().stream().map(t -> {
                        List<TrasladoDetalle> detalles = detallesByTraslado.getOrDefault(t.getTrasladoId(), List.of());
                        Almacen origen = almacenes.get(t.getAlmacenOrigen());
                        Almacen destino = almacenes.get(t.getAlmacenDestino());

                        // Reuse productos map already loaded
                        List<TrasladoDetalleResponse> detalleResponses = detalles.stream()
                                        .map(d -> new TrasladoDetalleResponse(
                                                        d.getProductoId(),
                                                        productos.containsKey(d.getProductoId())
                                                                        ? productos.get(d.getProductoId()).getNombre()
                                                                        : null,
                                                        d.getCantidad()))
                                        .toList();
                        return new TrasladoResponse(
                                        t.getTrasladoId(), t.getFolio(),
                                        t.getAlmacenOrigen(), origen != null ? origen.getNombre() : null,
                                        t.getAlmacenDestino(), destino != null ? destino.getNombre() : null,
                                        t.getEstado(), t.getUsuarioId(), null, detalleResponses);
                }).toList();
                return new PageImpl<>(content, page.getPageable(), page.getTotalElements());
        }

        @Transactional(readOnly = true)
        public TrasladoResponse getById(Long id) {
                Traslado entity = repo.findById(id)
                                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
                List<TrasladoDetalle> detalles = detalleRepo.findByTrasladoId(id);
                return toResponse(entity, detalles);
        }

        public TrasladoResponse create(TrasladoRequest req) {
                if (req.almacenOrigen().equals(req.almacenDestino())) {
                        throw new ReglaNegocioException(ErrorCode.VALOR_INVALIDO);
                }
                almacenRepo.findById(req.almacenOrigen())
                                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
                almacenRepo.findById(req.almacenDestino())
                                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));

                // BACK-EST-004: validar todos los productos en una sola query (vs N findById).
                Set<Long> productoIds = req.detalles().stream()
                                .map(TrasladoDetalleRequest::productoId).collect(Collectors.toSet());
                Set<Long> existentes = productoRepo.findAllById(productoIds).stream()
                                .map(Producto::getProductoId).collect(Collectors.toSet());
                Set<Long> faltantes = productoIds.stream()
                                .filter(id -> !existentes.contains(id)).collect(Collectors.toSet());
                if (!faltantes.isEmpty()) {
                        throw new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO,
                                        "productos inexistentes: " + faltantes);
                }

                Traslado traslado = Traslado.builder()
                                .almacenOrigen(req.almacenOrigen())
                                .almacenDestino(req.almacenDestino())
                                .usuarioId(UserPrincipal.actual().usuarioId())
                                .build();
                Traslado savedTraslado = repo.save(traslado);
                repo.flush();
                // folio lo asigna el trigger cfg.fn_siguiente_folio('TRASLADO') -> T-0000000X
                // (WHEN folio IS NULL)
                String folioGenerado = repo.findFolioById(savedTraslado.getTrasladoId());
                savedTraslado.setFolio(folioGenerado);

                // BACK-EST-004: saveAll batch en lugar de save uno a uno. Hibernate
                // sigue flushing al final del metodo (@Transactional) sin N round-trips
                // intermedios. Sin flush periodico explicito: el tamanio max realista es
                // ~50-100 SKUs por traslado (caso de uso ferretero); si crece, agregar
                // flush cada 50 (em.flush()) para acotar memoria del PersistenceContext.
                List<TrasladoDetalle> detallesGuardar = req.detalles().stream()
                                .map(d -> TrasladoDetalle.builder()
                                                .trasladoId(savedTraslado.getTrasladoId())
                                                .productoId(d.productoId())
                                                .cantidad(d.cantidad())
                                                .build())
                                .toList();
                detalleRepo.saveAll(detallesGuardar);

                Integer motivoSalidaId = findMotivoId("TRASLADO_SALIDA");
                Integer motivoEntradaId = findMotivoId("TRASLADO_ENTRADA");

                List<MovimientoInventario> movimientos = new ArrayList<>(req.detalles().size() * 2);
                Integer uid = UserPrincipal.actual().usuarioId();
                Long trasladoId = savedTraslado.getTrasladoId();
                for (TrasladoDetalleRequest d : req.detalles()) {
                        movimientos.add(MovimientoInventario.builder()
                                        .productoId(d.productoId())
                                        .almacenId(req.almacenOrigen())
                                        .tipo("SALIDA")
                                        .cantidad(d.cantidad())
                                        .motivoId(motivoSalidaId)
                                        .refTabla("TRASLADO")
                                        .refId(trasladoId)
                                        .trasladoId(trasladoId)
                                        .usuarioId(uid)
                                        .build());
                        movimientos.add(MovimientoInventario.builder()
                                        .productoId(d.productoId())
                                        .almacenId(req.almacenDestino())
                                        .tipo("ENTRADA")
                                        .cantidad(d.cantidad())
                                        .motivoId(motivoEntradaId)
                                        .refTabla("TRASLADO")
                                        .refId(trasladoId)
                                        .trasladoId(trasladoId)
                                        .usuarioId(uid)
                                        .build());
                }
                movimientoRepo.saveAll(movimientos);

                List<TrasladoDetalle> detalles = detalleRepo.findByTrasladoId(savedTraslado.getTrasladoId());
                return toResponse(savedTraslado, detalles);
        }

        Integer findMotivoId(String clave) {
                return motivoRepo.findByClave(clave)
                                .orElseThrow(() -> new ReglaNegocioException(ErrorCode.VALOR_INVALIDO))
                                .getMotivoId();
        }

        private TrasladoResponse toResponse(Traslado t, List<TrasladoDetalle> detalles) {
                Almacen origen = almacenRepo.findById(t.getAlmacenOrigen()).orElse(null);
                Almacen destino = almacenRepo.findById(t.getAlmacenDestino()).orElse(null);
                List<Long> productoIds = detalles.stream()
                                .map(TrasladoDetalle::getProductoId).distinct().toList();
                Map<Long, Producto> productos = productoIds.isEmpty() ? Map.of()
                                : productoRepo.findAllById(productoIds).stream()
                                                .collect(Collectors.toMap(Producto::getProductoId, p -> p));
                List<TrasladoDetalleResponse> detalleResponses = detalles.stream()
                                .map(d -> new TrasladoDetalleResponse(
                                                d.getProductoId(),
                                                productos.containsKey(d.getProductoId())
                                                                ? productos.get(d.getProductoId()).getNombre()
                                                                : null,
                                                d.getCantidad()))
                                .toList();
                return new TrasladoResponse(
                                t.getTrasladoId(),
                                t.getFolio(),
                                t.getAlmacenOrigen(),
                                origen != null ? origen.getNombre() : null,
                                t.getAlmacenDestino(),
                                destino != null ? destino.getNombre() : null,
                                t.getEstado(),
                                t.getUsuarioId(),
                                null,
                                detalleResponses);
        }
}
