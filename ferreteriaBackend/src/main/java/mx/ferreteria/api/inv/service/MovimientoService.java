package mx.ferreteria.api.inv.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.inv.dto.InvDtos.MovimientoInventarioRequest;
import mx.ferreteria.api.inv.dto.InvDtos.MovimientoInventarioResponse;
import mx.ferreteria.api.inv.entity.Almacen;
import mx.ferreteria.api.inv.entity.MovimientoInventario;
import mx.ferreteria.api.inv.repo.AlmacenRepository;
import mx.ferreteria.api.inv.repo.MovimientoInventarioRepository;
import mx.ferreteria.api.cat.entity.Producto;
import mx.ferreteria.api.cat.repo.ProductoRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class MovimientoService {

    private final MovimientoInventarioRepository repo;
    private final ProductoRepository productoRepo;
    private final AlmacenRepository almacenRepo;

    @Transactional(readOnly = true)
    public Page<MovimientoInventarioResponse> list(Pageable pageable) {
        Page<MovimientoInventario> page = repo.findAll(pageable);
        Map<Long, Producto> productos = productoRepo.findAllById(extractProductoIds(page.getContent())).stream()
                .collect(Collectors.toMap(Producto::getProductoId, p -> p));
        Map<Integer, Almacen> almacenes = loadAlmacenes(page.getContent());
        return page.map(m -> toResponse(m, productos.get(m.getProductoId()), almacenes.get(m.getAlmacenId())));
    }

    @Transactional(readOnly = true)
    public Page<MovimientoInventarioResponse> listByProducto(Long productoId, Pageable pageable) {
        Page<MovimientoInventario> page = repo.findByProductoIdOrderByCreadoEnDesc(productoId, pageable);
        Map<Long, Producto> productos = productoRepo.findAllById(extractProductoIds(page.getContent())).stream()
                .collect(Collectors.toMap(Producto::getProductoId, p -> p));
        Map<Integer, Almacen> almacenes = loadAlmacenes(page.getContent());
        return page.map(m -> toResponse(m, productos.get(m.getProductoId()), almacenes.get(m.getAlmacenId())));
    }

    @Transactional(readOnly = true)
    public Page<MovimientoInventarioResponse> listByAlmacen(Integer almacenId, Pageable pageable) {
        Page<MovimientoInventario> page = repo.findByAlmacenIdOrderByCreadoEnDesc(almacenId, pageable);
        Map<Long, Producto> productos = productoRepo.findAllById(extractProductoIds(page.getContent())).stream()
                .collect(Collectors.toMap(Producto::getProductoId, p -> p));
        Map<Integer, Almacen> almacenes = loadAlmacenes(page.getContent());
        return page.map(m -> toResponse(m, productos.get(m.getProductoId()), almacenes.get(m.getAlmacenId())));
    }

    public MovimientoInventarioResponse create(MovimientoInventarioRequest req) {
        if (!"ENTRADA".equals(req.tipo()) && !"SALIDA".equals(req.tipo())) {
            throw new ReglaNegocioException(ErrorCode.VALOR_INVALIDO);
        }
        Producto producto = productoRepo.findById(req.productoId())
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        Almacen almacen = almacenRepo.findById(req.almacenId())
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));

        MovimientoInventario entity = MovimientoInventario.builder()
                .productoId(req.productoId())
                .almacenId(req.almacenId())
                .tipo(req.tipo())
                .cantidad(req.cantidad())
                .costoUnitario(req.costoUnitario())
                .motivoId(req.motivoId())
                .refTabla(req.refTabla())
                .refId(req.refId())
                .nota(req.nota())
                .build();
        MovimientoInventario saved = repo.save(entity);
        return toResponse(saved, producto, almacen);
    }

    private List<Long> extractProductoIds(List<MovimientoInventario> movimientos) {
        return movimientos.stream().map(MovimientoInventario::getProductoId).distinct().toList();
    }

    private Map<Integer, Almacen> loadAlmacenes(List<MovimientoInventario> movimientos) {
        List<Integer> ids = movimientos.stream()
                .map(MovimientoInventario::getAlmacenId).distinct().toList();
        return almacenRepo.findAllById(ids).stream()
                .collect(Collectors.toMap(Almacen::getAlmacenId, a -> a));
    }

    private MovimientoInventarioResponse toResponse(MovimientoInventario m, Producto p, Almacen a) {
        return new MovimientoInventarioResponse(
                m.getMovimientoId(),
                m.getProductoId(),
                p != null ? p.getNombre() : null,
                m.getAlmacenId(),
                a != null ? a.getNombre() : null,
                m.getTipo(),
                m.getCantidad(),
                m.getCostoUnitario(),
                m.getMotivoId(),
                null,
                m.getRefTabla(),
                m.getRefId(),
                m.getTrasladoId(),
                m.getNota(),
                m.getUsuarioId(),
                null);
    }
}
