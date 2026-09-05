package mx.ferreteria.api.cat.service;

import java.math.BigDecimal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.dto.CatDtos.ProductoRequest;
import mx.ferreteria.api.cat.dto.CatDtos.ProductoResponse;
import mx.ferreteria.api.cat.entity.Categoria;
import mx.ferreteria.api.cat.entity.Marca;
import mx.ferreteria.api.cat.entity.Producto;
import mx.ferreteria.api.cat.entity.UnidadMedida;
import mx.ferreteria.api.cat.repo.CategoriaRepository;
import mx.ferreteria.api.cat.repo.MarcaRepository;
import mx.ferreteria.api.cat.repo.ProductoRepository;
import mx.ferreteria.api.cat.repo.UnidadMedidaRepository;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.inv.repo.InventarioRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductoService {

    private final ProductoRepository repo;
    private final CategoriaRepository categoriaRepo;
    private final MarcaRepository marcaRepo;
    private final UnidadMedidaRepository unidadMedidaRepo;
    private final InventarioRepository inventarioRepo;

    @Transactional(readOnly = true)
    public Page<ProductoResponse> list(String q, Integer categoriaId,
            Integer marcaId, String tipo, Integer almacenId, Pageable pageable) {
        Page<Producto> page;

        if (StringUtils.hasText(q)) {
            String termino = q.trim();
            // El código es la búsqueda principal (escaneo de código de barras):
            // si coincide exactamente el código, se regresa ese producto; si no
            // existe, se busca por nombre.
            Page<Producto> porCodigo = repo.findByActivoTrueAndCodigoIgnoreCase(termino, pageable);
            page = porCodigo.hasContent() ? porCodigo
                    : repo.findByActivoTrueAndNombreContainingIgnoreCase(termino, pageable);
        } else if (categoriaId != null) {
            page = repo.findByCategoriaCategoriaIdAndActivoTrue(categoriaId, pageable);
        } else if (marcaId != null) {
            page = repo.findByMarcaMarcaIdAndActivoTrue(marcaId, pageable);
        } else if (StringUtils.hasText(tipo)) {
            page = repo.findByTipoAndActivoTrue(tipo, pageable);
        } else {
            page = repo.findByActivoTrue(pageable);
        }

        return page.map(this::toResponse)
                .map(product -> {
                    if (almacenId != null) {
                        var inventario = inventarioRepo.findByAlmacenIdAndProductoId(almacenId, product.productoId());
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
        return toResponse(repo.save(entity));
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

        return toResponse(repo.save(entity));
    }

    public void deactivate(Long id) {
        Producto entity = repo.findById(id).orElseThrow(
                () -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        entity.setActivo(false);
        repo.save(entity);
    }

    private ProductoResponse toResponse(Producto p) {
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
                BigDecimal.ZERO);
    }
}
