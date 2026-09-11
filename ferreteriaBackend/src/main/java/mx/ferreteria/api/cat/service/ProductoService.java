package mx.ferreteria.api.cat.service;

import java.math.BigDecimal;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.dto.CatDtos.CodigoBarrasRequest;
import mx.ferreteria.api.cat.dto.CatDtos.ProductoRequest;
import mx.ferreteria.api.cat.dto.CatDtos.ProductoResponse;
import mx.ferreteria.api.cat.entity.Categoria;
import mx.ferreteria.api.cat.entity.Marca;
import mx.ferreteria.api.cat.entity.Producto;
import mx.ferreteria.api.cat.entity.ProductoCodigoBarras;
import mx.ferreteria.api.cat.entity.UnidadMedida;
import mx.ferreteria.api.cat.repo.CategoriaRepository;
import mx.ferreteria.api.cat.repo.CodigoBarrasRepository;
import mx.ferreteria.api.cat.repo.MarcaRepository;
import mx.ferreteria.api.cat.repo.ProductoListado;
import mx.ferreteria.api.cat.repo.ProductoRepository;
import mx.ferreteria.api.cat.repo.UnidadMedidaRepository;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.inv.entity.Inventario;
import mx.ferreteria.api.inv.repo.InventarioRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductoService {

    private final ProductoRepository repo;
    private final CodigoBarrasRepository barrasRepo;
    private final CategoriaRepository categoriaRepo;
    private final MarcaRepository marcaRepo;
    private final UnidadMedidaRepository unidadMedidaRepo;
    private final InventarioRepository inventarioRepo;

    @Transactional(readOnly = true)
    public Page<ProductoResponse> list(String q, Integer categoriaId,
            Integer marcaId, String tipo, Integer almacenId, Pageable pageable) {
        // BACK-REND-027: cuando filtra por categoriaId, marcaId o sin filtros,
        // usamos la proyeccion ProductoListado (campos del grid) en lugar de la
        // entidad completa (descripcion + especificaciones JSONB + auditoria).
        // Para busqueda por codigo/nombre conservamos la entidad porque el
        // cliente tambien puede necesitar descripcion en el detalle del match.

        Page<Producto> pageFull = null;
        Page<ProductoListado> pageProj = null;
        // factor por producto cuando el match fue por código de barras
        Map<Long, BigDecimal> factorPorProducto = Map.of();

        if (StringUtils.hasText(q)) {
            String termino = q.trim();
            // 1º código de barras exacto (solo productos activos: los dados
            // de baja no deben venderse aunque se escaneen).
            Optional<ProductoCodigoBarras> barra = barrasRepo.findByCodigoBarras(termino);
            if (barra.isPresent() && Boolean.TRUE.equals(barra.get().getProducto().getActivo())) {
                Producto p = barra.get().getProducto();
                pageFull = new PageImpl<>(List.of(p), pageable, 1);
                factorPorProducto = Map.of(p.getProductoId(), barra.get().getFactor());
            } else {
                Page<Producto> porCodigo = repo.findByActivoTrueAndCodigoIgnoreCase(termino, pageable);
                pageFull = porCodigo.hasContent() ? porCodigo
                        : repo.findByActivoTrueAndNombreContainingIgnoreCase(termino, pageable);
            }
        } else if (categoriaId != null) {
            pageProj = repo.findListadoByCategoriaCategoriaIdAndActivoTrue(categoriaId, pageable);
        } else if (marcaId != null) {
            // Marca sin proyeccion dedicada (caso raro en listado): cae a entidad.
            pageFull = repo.findByMarcaMarcaIdAndActivoTrue(marcaId, pageable);
        } else if (StringUtils.hasText(tipo)) {
            pageFull = repo.findByTipoAndActivoTrue(tipo, pageable);
        } else {
            pageProj = repo.findListadoByActivoTrue(pageable);
        }

        Page<ProductoResponse> mapped;
        if (pageProj != null) {
            mapped = pageProj.map(this::toResponseFromListado);
        } else {
            mapped = pageFull.map(this::baseResponse);
        }
        // Adjunta barras en UNA sola consulta (evita N+1) + factor de escaneo.
        if (!mapped.getContent().isEmpty()) {
            List<Long> ids = mapped.getContent().stream().map(ProductoResponse::productoId).toList();
            Map<Long, List<String>> barrasPorProd = barrasRepo.findByProductoProductoIdIn(ids).stream()
                    .collect(Collectors.groupingBy(b -> b.getProducto().getProductoId(),
                            Collectors.mapping(ProductoCodigoBarras::getCodigoBarras, Collectors.toList())));
            final Map<Long, BigDecimal> factores = factorPorProducto;
            mapped = mapped.map(r -> completarBarras(r,
                    barrasPorProd.getOrDefault(r.productoId(), List.of()),
                    factores.get(r.productoId())));
        }
        if (almacenId != null && mapped.getContent().size() > 1) {
            List<Long> pids = mapped.getContent().stream().map(ProductoResponse::productoId).toList();
            Map<Long, Inventario> invByProd = inventarioRepo
                    .findByAlmacenIdAndProductoIdIn(almacenId, pids).stream()
                    .collect(Collectors.toMap(Inventario::getProductoId, Function.identity()));
            List<ProductoResponse> enriched = mapped.getContent().stream().map(product -> {
                Inventario inv = invByProd.get(product.productoId());
                BigDecimal stock = (inv != null && inv.getStock() != null) ? inv.getStock() : BigDecimal.ZERO;
                return product.withStock(stock);
            }).toList();
            // BACK-REND-021: reusar mapped.getPageable()/getTotalElements() evita
            // remapear cada entity->response una segunda vez solo para obtener
            // metadata; Pageable y TotalElements vienen de la Page original.
            return new PageImpl<>(enriched, mapped.getPageable(), mapped.getTotalElements());
        }
        return mapped.map(product -> {
            if (almacenId != null) {
                Inventario inventario = inventarioRepo.findByAlmacenIdAndProductoId(almacenId, product.productoId());
                if (inventario != null) {
                    product = product
                            .withStock(inventario.getStock() != null ? inventario.getStock() : BigDecimal.ZERO);
                } else {
                    product = product.withStock(BigDecimal.ZERO);
                }
            }
            return product;
        });
    }

    /** Mapeo desde la proyeccion BACK-REND-027: no carga descripcion ni JSONB. */
    private ProductoResponse toResponseFromListado(ProductoListado p) {
        return new ProductoResponse(
                p.getProductoId(),
                p.getCodigo(),
                null, // tipo (no esta en proyeccion)
                p.getNombre(),
                null, // descripcion (omitida en grid)
                null, // categoriaId (nombre ya esta proyectado)
                p.getCategoriaNombre(),
                null, // marcaId
                p.getMarcaNombre(),
                null, null, // unidad: no en proyeccion
                p.getCostoActual(),
                p.getPrecioMenudeo(),
                p.getPrecioMayoreo(),
                p.getAplicaIva() != null ? p.getAplicaIva() : true,
                BigDecimal.ZERO, // stock se enriquece via inventarioRepo si almacenId != null
                null, null); // barras/factor se adjuntan en lote en list()
    }

    @Transactional(readOnly = true)
    public ProductoResponse getById(Long id) {
        Producto entity = repo.findById(id).orElseThrow(
                () -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        return toResponse(entity);
    }

    public ProductoResponse create(ProductoRequest req) {
        Categoria cat = categoriaRepo.findById(req.categoriaId()).orElseThrow(
                () -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        UnidadMedida um = unidadMedidaRepo.findById(req.unidadMedidaId()).orElseThrow(
                () -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));

        Marca marca = null;
        if (req.marcaId() != null) {
            marca = marcaRepo.findById(req.marcaId()).orElseThrow(
                    () -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        }

        Producto entity = Producto.builder()
                .codigo(req.codigo())
                .tipo(req.tipo())
                .nombre(req.nombre())
                .descripcion(req.descripcion())
                .categoria(cat)
                .marca(marca)
                .unidadMedida(um)
                .costoActual(req.costoActual() != null ? req.costoActual() : BigDecimal.ZERO)
                .precioMenudeo(req.precioMenudeo() != null ? req.precioMenudeo() : BigDecimal.ZERO)
                .precioMayoreo(req.precioMayoreo())
                .aplicaIva(req.aplicaIva() != null ? req.aplicaIva() : true)
                .build();
        Producto saved = repo.save(entity);
        guardarBarras(saved, req.codigosBarras());
        return toResponse(saved);
    }

    public ProductoResponse update(Long id, ProductoRequest req) {
        Producto entity = repo.findById(id).orElseThrow(
                () -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));

        Categoria cat = categoriaRepo.findById(req.categoriaId()).orElseThrow(
                () -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        UnidadMedida um = unidadMedidaRepo.findById(req.unidadMedidaId()).orElseThrow(
                () -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));

        Marca marca = null;
        if (req.marcaId() != null) {
            marca = marcaRepo.findById(req.marcaId()).orElseThrow(
                    () -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        }

        entity.setCodigo(req.codigo());
        entity.setTipo(req.tipo());
        entity.setNombre(req.nombre());
        entity.setDescripcion(req.descripcion());
        entity.setCategoria(cat);
        entity.setMarca(marca);
        entity.setUnidadMedida(um);
        if (req.costoActual() != null) {
            entity.setCostoActual(req.costoActual());
        }
        if (req.precioMenudeo() != null) {
            entity.setPrecioMenudeo(req.precioMenudeo());
        }
        entity.setPrecioMayoreo(req.precioMayoreo());
        if (req.aplicaIva() != null) {
            entity.setAplicaIva(req.aplicaIva());
        }

        Producto saved = repo.save(entity);
        guardarBarras(saved, req.codigosBarras());
        return toResponse(saved);
    }

    public void deactivate(Long id) {
        Producto entity = repo.findById(id).orElseThrow(
                () -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        entity.setActivo(false);
        repo.save(entity);
    }

    private ProductoResponse toResponse(Producto p) {
        return completarBarras(baseResponse(p), codigosDe(p.getProductoId()), null);
    }

    /** Códigos del producto en una sola consulta (detalle / create / update). */
    private List<String> codigosDe(Long productoId) {
        return barrasRepo.findByProductoProductoId(productoId).stream()
                .map(ProductoCodigoBarras::getCodigoBarras).toList();
    }

    private ProductoResponse completarBarras(ProductoResponse r, List<String> codigos, BigDecimal factor) {
        return new ProductoResponse(
                r.productoId(), r.codigo(), r.tipo(), r.nombre(), r.descripcion(),
                r.categoriaId(), r.categoriaNombre(), r.marcaId(), r.marcaNombre(),
                r.unidadMedidaId(), r.unidadMedidaClave(), r.costoActual(),
                r.precioMenudeo(), r.precioMayoreo(), r.aplicaIva(), r.stockActual(),
                codigos, factor);
    }

    private ProductoResponse baseResponse(Producto p) {
        return new ProductoResponse(
                p.getProductoId(),
                p.getCodigo(),
                p.getTipo(),
                p.getNombre(),
                p.getDescripcion(),
                p.getCategoria().getCategoriaId(),
                p.getCategoria().getNombre(),
                p.getMarca() != null ? p.getMarca().getMarcaId() : null,
                p.getMarca() != null ? p.getMarca().getNombre() : null,
                p.getUnidadMedida().getUnidadId(),
                p.getUnidadMedida().getClave(),
                p.getCostoActual(),
                p.getPrecioMenudeo(),
                p.getPrecioMayoreo(),
                p.getAplicaIva(),
                BigDecimal.ZERO,
                null, null);
    }

    /**
     * Guarda (create) o reemplaza (update) los códigos de barras.
     * Duplicado contra OTRO producto, o repetido en el request → 409
     * VALOR_DUPLICADO (nunca 500: el traductor BD no mapea el 23505).
     */
    private void guardarBarras(Producto producto, List<CodigoBarrasRequest> codigos) {
        barrasRepo.deleteByProductoProductoId(producto.getProductoId());
        if (codigos == null || codigos.isEmpty()) {
            return;
        }
        Set<String> vistos = new HashSet<>();
        for (CodigoBarrasRequest cb : codigos) {
            String codigo = cb.codigo().trim();
            if (!vistos.add(codigo.toLowerCase())) {
                throw new ReglaNegocioException(ErrorCode.VALOR_DUPLICADO, codigo);
            }
            Optional<ProductoCodigoBarras> existente = barrasRepo.findByCodigoBarras(codigo);
            if (existente.isPresent()
                    && !existente.get().getProducto().getProductoId().equals(producto.getProductoId())) {
                throw new ReglaNegocioException(ErrorCode.VALOR_DUPLICADO, codigo);
            }
        }
        try {
            barrasRepo.saveAll(codigos.stream().map(cb -> ProductoCodigoBarras.builder()
                    .codigoBarras(cb.codigo().trim())
                    .producto(producto)
                    .factor(cb.factor() != null ? cb.factor() : BigDecimal.ONE)
                    .build()).toList());
        } catch (DataIntegrityViolationException e) {
            // Carrera contra el pre-chequeo: la PK lo frena igual.
            throw new ReglaNegocioException(ErrorCode.VALOR_DUPLICADO, codigos.get(0).codigo());
        }
    }
}
