package mx.ferreteria.api.inv.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
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
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public Page<TrasladoResponse> list(Pageable pageable) {
        Page<Traslado> page = repo.findAllByOrderByCreadoEnDesc(pageable);
        return page.map(t -> {
            List<TrasladoDetalle> detalles = detalleRepo.findByTrasladoId(t.getTrasladoId());
            return toResponse(t, detalles);
        });
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

        for (TrasladoDetalleRequest d : req.detalles()) {
            productoRepo.findById(d.productoId())
                    .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        }

        Traslado traslado = Traslado.builder()
                .folio("TR-" + System.currentTimeMillis())
                .almacenOrigen(req.almacenOrigen())
                .almacenDestino(req.almacenDestino())
                .usuarioId(UserPrincipal.actual().usuarioId())
                .build();
        Traslado savedTraslado = repo.save(traslado);

        for (TrasladoDetalleRequest d : req.detalles()) {
            TrasladoDetalle detalle = TrasladoDetalle.builder()
                    .trasladoId(savedTraslado.getTrasladoId())
                    .productoId(d.productoId())
                    .cantidad(d.cantidad())
                    .build();
            detalleRepo.save(detalle);
        }

        Integer motivoSalidaId = findMotivoId("TRASLADO_SALIDA");
        Integer motivoEntradaId = findMotivoId("TRASLADO_ENTRADA");

        for (TrasladoDetalleRequest d : req.detalles()) {
            movimientoRepo.save(MovimientoInventario.builder()
                    .productoId(d.productoId())
                    .almacenId(req.almacenOrigen())
                    .tipo("SALIDA")
                    .cantidad(d.cantidad())
                    .motivoId(motivoSalidaId)
                    .refTabla("TRASLADO")
                    .refId(savedTraslado.getTrasladoId())
                    .trasladoId(savedTraslado.getTrasladoId())
                    .usuarioId(UserPrincipal.actual().usuarioId())
                    .build());

            movimientoRepo.save(MovimientoInventario.builder()
                    .productoId(d.productoId())
                    .almacenId(req.almacenDestino())
                    .tipo("ENTRADA")
                    .cantidad(d.cantidad())
                    .motivoId(motivoEntradaId)
                    .refTabla("TRASLADO")
                    .refId(savedTraslado.getTrasladoId())
                    .trasladoId(savedTraslado.getTrasladoId())
                    .usuarioId(UserPrincipal.actual().usuarioId())
                    .build());
        }

        List<TrasladoDetalle> detalles = detalleRepo.findByTrasladoId(savedTraslado.getTrasladoId());
        return toResponse(savedTraslado, detalles);
    }

    private Integer findMotivoId(String clave) {
        Integer id = jdbc.queryForObject(
                "SELECT motivo_id FROM cat.motivos_movimiento WHERE clave = ?",
                Integer.class, clave);
        if (id == null) {
            throw new ReglaNegocioException(ErrorCode.VALOR_INVALIDO);
        }
        return id;
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
                                ? productos.get(d.getProductoId()).getNombre() : null,
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
