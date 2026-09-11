package mx.ferreteria.api.cat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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
import mx.ferreteria.api.cat.repo.ProductoRepository;
import mx.ferreteria.api.cat.repo.UnidadMedidaRepository;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.common.i18n.ErrorCode;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductoServiceTest {

    @Mock
    ProductoRepository repo;
    @Mock
    CodigoBarrasRepository barrasRepo;
    @Mock
    CategoriaRepository categoriaRepo;
    @Mock
    MarcaRepository marcaRepo;
    @Mock
    UnidadMedidaRepository unidadMedidaRepo;

    @InjectMocks
    ProductoService service;

    private Categoria sampleCategoria() {
        return Categoria.builder().categoriaId(1).nombre("Herramientas").nivel((short) 0).activo(true).build();
    }

    private Marca sampleMarca() {
        return Marca.builder().marcaId(1).nombre("Acme").activo(true).build();
    }

    private UnidadMedida sampleUM() {
        return UnidadMedida.builder().unidadId(1).clave("PZA").nombre("Pieza").activo(true).build();
    }

    private Producto sampleProducto() {
        return Producto.builder()
                .productoId(1L).codigo("P001").tipo("PRODUCTO").nombre("Taladro")
                .categoria(sampleCategoria()).marca(sampleMarca()).unidadMedida(sampleUM())
                .costoActual(new BigDecimal("100.00"))
                .precioMenudeo(new BigDecimal("150.00"))
                .aplicaIva(true).activo(true).build();
    }

    // ── list ────────────────────────────────────────────────────────

    @Test
    @DisplayName("list sin filtros: retorna pagina de productos activos (proyeccion BACK-REND-027)")
    void list_all_returnsPage() {
        Pageable pg = PageRequest.of(0, 10);
        mx.ferreteria.api.cat.repo.ProductoListado p = mock(mx.ferreteria.api.cat.repo.ProductoListado.class);
        when(p.getProductoId()).thenReturn(1L);
        when(p.getCodigo()).thenReturn("P001");
        when(p.getNombre()).thenReturn("Taladro");
        when(p.getCategoriaNombre()).thenReturn("Herramientas");
        when(p.getMarcaNombre()).thenReturn("DeWalt");
        when(p.getCostoActual()).thenReturn(new java.math.BigDecimal("100.00"));
        when(p.getPrecioMenudeo()).thenReturn(new java.math.BigDecimal("150.00"));
        when(p.getPrecioMayoreo()).thenReturn(new java.math.BigDecimal("130.00"));
        when(p.getAplicaIva()).thenReturn(true);
        when(p.getActivo()).thenReturn(true);
        when(repo.findListadoByActivoTrue(pg)).thenReturn(new PageImpl<>(List.of(p), pg, 1));

        var result = service.list(null, null, null, null, null, pg);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).nombre()).isEqualTo("Taladro");
    }

    @Test
    @DisplayName("list con query y sin coincidencia de codigo: busca por nombre")
    void list_withQuery() {
        Pageable pg = PageRequest.of(0, 10);
        Producto p = sampleProducto();
        when(repo.findByActivoTrueAndCodigoIgnoreCase("Taladro", pg))
                .thenReturn(new PageImpl<>(List.of(), pg, 0));
        when(repo.findByActivoTrueAndNombreContainingIgnoreCase("Taladro", pg))
                .thenReturn(new PageImpl<>(List.of(p), pg, 1));

        var result = service.list("Taladro", null, null, null, null, pg);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).nombre()).isEqualTo("Taladro");
    }

    @Test
    @DisplayName("list con codigo exacto: busca por codigo y no cae al nombre")
    void list_codigoExacto_returnsByCode() {
        Pageable pg = PageRequest.of(0, 10);
        Producto p = sampleProducto();
        when(repo.findByActivoTrueAndCodigoIgnoreCase("P001", pg))
                .thenReturn(new PageImpl<>(List.of(p), pg, 1));

        var result = service.list("P001", null, null, null, null, pg);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).codigo()).isEqualTo("P001");
        verify(repo, never()).findByActivoTrueAndNombreContainingIgnoreCase(any(), any());
    }

    @Test
    @DisplayName("list con codigo inexistente: cae a la busqueda por nombre")
    void list_codigoInexistente_caeANombre() {
        Pageable pg = PageRequest.of(0, 10);
        Producto p = sampleProducto();
        when(repo.findByActivoTrueAndCodigoIgnoreCase("ZZZ999", pg))
                .thenReturn(new PageImpl<>(List.of(), pg, 0));
        when(repo.findByActivoTrueAndNombreContainingIgnoreCase("ZZZ999", pg))
                .thenReturn(new PageImpl<>(List.of(p), pg, 1));

        var result = service.list(" ZZZ999 ", null, null, null, null, pg);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).codigo()).isEqualTo("P001");
    }

    @Test
    @DisplayName("list con categoriaId: usa findListadoByCategoriaCategoriaIdAndActivoTrue (proyeccion BACK-REND-027)")
    void list_withCategoriaId() {
        Pageable pg = PageRequest.of(0, 10);
        mx.ferreteria.api.cat.repo.ProductoListado p = mock(mx.ferreteria.api.cat.repo.ProductoListado.class);
        when(p.getProductoId()).thenReturn(1L);
        when(p.getCodigo()).thenReturn("P001");
        when(p.getNombre()).thenReturn("Taladro");
        when(p.getCategoriaNombre()).thenReturn("Herramientas");
        when(p.getMarcaNombre()).thenReturn(null);
        when(p.getCostoActual()).thenReturn(new java.math.BigDecimal("100.00"));
        when(p.getPrecioMenudeo()).thenReturn(new java.math.BigDecimal("150.00"));
        when(p.getPrecioMayoreo()).thenReturn(new java.math.BigDecimal("130.00"));
        when(p.getAplicaIva()).thenReturn(true);
        when(p.getActivo()).thenReturn(true);
        when(repo.findListadoByCategoriaCategoriaIdAndActivoTrue(1, pg))
                .thenReturn(new PageImpl<>(List.of(p), pg, 1));

        var result = service.list(null, 1, null, null, null, pg);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).categoriaNombre()).isEqualTo("Herramientas");
    }

    @Test
    @DisplayName("list con marcaId: usa findByMarcaMarcaIdAndActivoTrue")
    void list_withMarcaId() {
        Pageable pg = PageRequest.of(0, 10);
        Producto p = sampleProducto();
        when(repo.findByMarcaMarcaIdAndActivoTrue(1, pg))
                .thenReturn(new PageImpl<>(List.of(p), pg, 1));

        var result = service.list(null, null, 1, null, null, pg);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("list con tipo: usa findByTipoAndActivoTrue")
    void list_withTipo() {
        Pageable pg = PageRequest.of(0, 10);
        Producto p = sampleProducto();
        when(repo.findByTipoAndActivoTrue("SERVICIO", pg))
                .thenReturn(new PageImpl<>(List.of(p), pg, 1));

        var result = service.list(null, null, null, "SERVICIO", null, pg);

        assertThat(result.getContent()).hasSize(1);
    }

    // ── getById ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getById encontrado: retorna ProductoResponse")
    void getById_found() {
        when(repo.findById(1L)).thenReturn(Optional.of(sampleProducto()));

        ProductoResponse resp = service.getById(1L);

        assertThat(resp.productoId()).isEqualTo(1L);
        assertThat(resp.nombre()).isEqualTo("Taladro");
        assertThat(resp.categoriaId()).isEqualTo(1);
        assertThat(resp.marcaId()).isEqualTo(1);
        assertThat(resp.unidadMedidaId()).isEqualTo(1);
    }

    @Test
    @DisplayName("getById inexistente: RecursoNoEncontradoException")
    void getById_notFound() {
        when(repo.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(999L))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ── create ──────────────────────────────────────────────────────

    @Test
    @DisplayName("create: resuelve todas las FK y guarda")
    void create_ok() {
        Categoria cat = sampleCategoria();
        Marca marca = sampleMarca();
        UnidadMedida um = sampleUM();
        when(categoriaRepo.findById(1)).thenReturn(Optional.of(cat));
        when(marcaRepo.findById(1)).thenReturn(Optional.of(marca));
        when(unidadMedidaRepo.findById(1)).thenReturn(Optional.of(um));

        Producto saved = sampleProducto();
        when(repo.save(any(Producto.class))).thenReturn(saved);

        ProductoRequest req = new ProductoRequest(
                "P001", "PRODUCTO", "Taladro", "desc", 1, 1, 1,
                new BigDecimal("100"), new BigDecimal("150"), null, true, null);

        ProductoResponse resp = service.create(req);

        assertThat(resp.productoId()).isEqualTo(1L);
        assertThat(resp.categoriaNombre()).isEqualTo("Herramientas");
        assertThat(resp.marcaNombre()).isEqualTo("Acme");
        verify(repo).save(any(Producto.class));
    }

    @Test
    @DisplayName("create con categoriaId inexistente: RecursoNoEncontradoException")
    void create_categoriaNotFound() {
        when(categoriaRepo.findById(999)).thenReturn(Optional.empty());

        ProductoRequest req = new ProductoRequest(
                null, "PRODUCTO", "X", null, 999, null, 1,
                null, null, null, null, null);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("create con unidadMedidaId inexistente: RecursoNoEncontradoException")
    void create_unidadMedidaNotFound() {
        when(categoriaRepo.findById(1)).thenReturn(Optional.of(sampleCategoria()));
        when(unidadMedidaRepo.findById(999)).thenReturn(Optional.empty());

        ProductoRequest req = new ProductoRequest(
                null, "PRODUCTO", "X", null, 1, null, 999,
                null, null, null, null, null);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("create con marcaId inexistente: RecursoNoEncontradoException")
    void create_marcaNotFound() {
        when(categoriaRepo.findById(1)).thenReturn(Optional.of(sampleCategoria()));
        when(unidadMedidaRepo.findById(1)).thenReturn(Optional.of(sampleUM()));
        when(marcaRepo.findById(999)).thenReturn(Optional.empty());

        ProductoRequest req = new ProductoRequest(
                null, "PRODUCTO", "X", null, 1, 999, 1,
                null, null, null, null, null);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("create sin marcaId: marca es null en entidad")
    void create_withoutMarca() {
        when(categoriaRepo.findById(1)).thenReturn(Optional.of(sampleCategoria()));
        when(unidadMedidaRepo.findById(1)).thenReturn(Optional.of(sampleUM()));

        Producto saved = sampleProducto();
        saved.setMarca(null);
        when(repo.save(any(Producto.class))).thenReturn(saved);

        ProductoRequest req = new ProductoRequest(
                "P001", "PRODUCTO", "Taladro", null, 1, null, 1,
                null, null, null, null, null);

        ProductoResponse resp = service.create(req);

        assertThat(resp.marcaId()).isNull();
        assertThat(resp.marcaNombre()).isNull();
    }

    // ── update ──────────────────────────────────────────────────────

    @Test
    @DisplayName("update encontrado: actualiza todos los campos y guarda")
    void update_found() {
        Producto existing = sampleProducto();
        when(repo.findById(1L)).thenReturn(Optional.of(existing));
        when(categoriaRepo.findById(1)).thenReturn(Optional.of(sampleCategoria()));
        when(unidadMedidaRepo.findById(1)).thenReturn(Optional.of(sampleUM()));
        when(marcaRepo.findById(1)).thenReturn(Optional.of(sampleMarca()));
        when(repo.save(any(Producto.class))).thenReturn(existing);

        ProductoRequest req = new ProductoRequest(
                "P002", "SERVICIO", "NuevoNombre", "desc", 1, 1, 1,
                new BigDecimal("200"), new BigDecimal("300"), null, false, null);

        ProductoResponse resp = service.update(1L, req);

        assertThat(resp.nombre()).isEqualTo("NuevoNombre");
        verify(repo).save(existing);
    }

    @Test
    @DisplayName("update inexistente: RecursoNoEncontradoException")
    void update_notFound() {
        when(repo.findById(999L)).thenReturn(Optional.empty());

        ProductoRequest req = new ProductoRequest(
                null, "PRODUCTO", "X", null, 1, null, 1,
                null, null, null, null, null);

        assertThatThrownBy(() -> service.update(999L, req))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ── codigos de barras ─────────────────────────────────────────

    private ProductoCodigoBarras sampleBarra(Producto p, String codigo, String factor) {
        return ProductoCodigoBarras.builder()
                .codigoBarras(codigo).producto(p).factor(new BigDecimal(factor)).build();
    }

    @Test
    @DisplayName("list con EAN: match por barras primero, con factor y sin tocar codigo/nombre")
    void list_barcodeMatchFirst() {
        Pageable pg = PageRequest.of(0, 20);
        Producto p = sampleProducto();
        ProductoCodigoBarras barra = sampleBarra(p, "7501234567001", "6");
        when(barrasRepo.findByCodigoBarras("7501234567001")).thenReturn(Optional.of(barra));
        when(barrasRepo.findByProductoProductoIdIn(List.of(1L))).thenReturn(List.of(barra));

        var result = service.list("7501234567001", null, null, null, null, pg);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).codigo()).isEqualTo("P001");
        assertThat(result.getContent().get(0).factorEscaneo()).isEqualByComparingTo("6");
        assertThat(result.getContent().get(0).codigosBarras()).containsExactly("7501234567001");
        verify(repo, never()).findByActivoTrueAndCodigoIgnoreCase(any(), any());
        verify(repo, never()).findByActivoTrueAndNombreContainingIgnoreCase(any(), any());
    }

    @Test
    @DisplayName("list con EAN de producto inactivo: no hace match, cae a codigo/nombre")
    void list_barcodeInactive_fallsThrough() {
        Pageable pg = PageRequest.of(0, 20);
        Producto inactivo = sampleProducto();
        inactivo.setActivo(false);
        when(barrasRepo.findByCodigoBarras("7501234567001"))
                .thenReturn(Optional.of(sampleBarra(inactivo, "7501234567001", "1")));
        when(repo.findByActivoTrueAndCodigoIgnoreCase("7501234567001", pg))
                .thenReturn(new PageImpl<>(List.of(), pg, 0));
        when(repo.findByActivoTrueAndNombreContainingIgnoreCase("7501234567001", pg))
                .thenReturn(new PageImpl<>(List.of(), pg, 0));

        var result = service.list("7501234567001", null, null, null, null, pg);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("create con barras: guarda producto + barras y las devuelve")
    void create_withBarras() {
        when(categoriaRepo.findById(1)).thenReturn(Optional.of(sampleCategoria()));
        when(unidadMedidaRepo.findById(1)).thenReturn(Optional.of(sampleUM()));
        Producto saved = sampleProducto();
        when(repo.save(any(Producto.class))).thenReturn(saved);
        when(barrasRepo.findByCodigoBarras("7501234567001")).thenReturn(Optional.empty());
        ProductoCodigoBarras guardada = sampleBarra(saved, "7501234567001", "1");
        when(barrasRepo.findByProductoProductoId(1L)).thenReturn(List.of(guardada));

        ProductoRequest req = new ProductoRequest(
                "P001", "PRODUCTO", "Taladro", null, 1, null, 1,
                null, null, null, null,
                List.of(new CodigoBarrasRequest("7501234567001", new BigDecimal("1"))));

        ProductoResponse resp = service.create(req);

        var captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(barrasRepo).saveAll(captor.capture());
        @SuppressWarnings("unchecked")
        List<ProductoCodigoBarras> entidades = captor.getValue();
        assertThat(entidades).hasSize(1);
        assertThat(entidades.get(0).getCodigoBarras()).isEqualTo("7501234567001");
        assertThat(resp.codigosBarras()).containsExactly("7501234567001");
    }

    @Test
    @DisplayName("create con barra de OTRO producto: 409 VALOR_DUPLICADO")
    void create_barraDuplicada() {
        when(categoriaRepo.findById(1)).thenReturn(Optional.of(sampleCategoria()));
        when(unidadMedidaRepo.findById(1)).thenReturn(Optional.of(sampleUM()));
        when(repo.save(any(Producto.class))).thenReturn(sampleProducto());
        Producto otro = sampleProducto();
        otro.setProductoId(99L);
        when(barrasRepo.findByCodigoBarras("7501234567001"))
                .thenReturn(Optional.of(sampleBarra(otro, "7501234567001", "1")));

        ProductoRequest req = new ProductoRequest(
                "P001", "PRODUCTO", "Taladro", null, 1, null, 1,
                null, null, null, null,
                List.of(new CodigoBarrasRequest("7501234567001", null)));

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(ReglaNegocioException.class)
                .extracting(e -> ((ReglaNegocioException) e).errorCode())
                .isEqualTo(ErrorCode.VALOR_DUPLICADO);
    }

    @Test
    @DisplayName("update reemplaza barras: borra anteriores y guarda nuevas")
    void update_replacesBarras() {
        Producto existing = sampleProducto();
        when(repo.findById(1L)).thenReturn(Optional.of(existing));
        when(categoriaRepo.findById(1)).thenReturn(Optional.of(sampleCategoria()));
        when(unidadMedidaRepo.findById(1)).thenReturn(Optional.of(sampleUM()));
        when(repo.save(any(Producto.class))).thenReturn(existing);
        when(barrasRepo.findByCodigoBarras("7501234567009")).thenReturn(Optional.empty());

        ProductoRequest req = new ProductoRequest(
                "P001", "PRODUCTO", "Taladro", null, 1, null, 1,
                null, null, null, null,
                List.of(new CodigoBarrasRequest("7501234567009", new BigDecimal("2"))));

        service.update(1L, req);

        verify(barrasRepo).deleteByProductoProductoId(1L);
        verify(barrasRepo).saveAll(any());
    }

    // ── deactivate ──────────────────────────────────────────────────

    @Test
    @DisplayName("deactivate: producto se marca inactivo y se guarda")
    void deactivate_setsInactive() {
        Producto existing = sampleProducto();
        when(repo.findById(1L)).thenReturn(Optional.of(existing));

        service.deactivate(1L);

        assertThat(existing.getActivo()).isFalse();
        verify(repo).save(existing);
    }

    @Test
    @DisplayName("deactivate inexistente: RecursoNoEncontradoException")
    void deactivate_notFound() {
        when(repo.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivate(999L))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }
}
