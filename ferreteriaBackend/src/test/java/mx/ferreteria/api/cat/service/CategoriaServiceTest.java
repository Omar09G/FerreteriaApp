package mx.ferreteria.api.cat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import mx.ferreteria.api.cat.dto.CatDtos.CategoriaRequest;
import mx.ferreteria.api.cat.dto.CatDtos.CategoriaResponse;
import mx.ferreteria.api.cat.entity.Categoria;
import mx.ferreteria.api.cat.repo.CategoriaRepository;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;

@ExtendWith(MockitoExtension.class)
class CategoriaServiceTest {

    @Mock
    CategoriaRepository repo;

    @InjectMocks
    CategoriaService service;

    private Categoria sampleCat(Integer id, String nombre, Categoria padre, Short nivel) {
        return Categoria.builder()
                .categoriaId(id).nombre(nombre).categoriaPadre(padre)
                .nivel(nivel).activo(true).build();
    }

    // ── listTree ────────────────────────────────────────────────────

    @Test
    @DisplayName("listTree: retorna categorias raiz con hijos anidados")
    void listTree_returnsRootsWithChildren() {
        Categoria root = sampleCat(1, "Herramientas", null, (short) 0);
        Categoria child = sampleCat(2, "Manuales", root, (short) 1);

        when(repo.findByActivoTrueAndCategoriaPadreIsNullOrderByNombre())
                .thenReturn(List.of(root));
        when(repo.findByCategoriaPadreCategoriaIdAndActivoTrueOrderByNombre(1))
                .thenReturn(List.of(child));
        when(repo.findByCategoriaPadreCategoriaIdAndActivoTrueOrderByNombre(2))
                .thenReturn(List.of());

        List<CategoriaResponse> result = service.listTree();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).nombre()).isEqualTo("Herramientas");
        assertThat(result.get(0).hijos()).hasSize(1);
        assertThat(result.get(0).hijos().get(0).nombre()).isEqualTo("Manuales");
        assertThat(result.get(0).hijos().get(0).hijos()).isEmpty();
    }

    @Test
    @DisplayName("listTree: raiz sin hijos retorna lista vacia de hijos")
    void listTree_rootWithNoChildren() {
        Categoria root = sampleCat(1, "Electricos", null, (short) 0);
        when(repo.findByActivoTrueAndCategoriaPadreIsNullOrderByNombre())
                .thenReturn(List.of(root));
        when(repo.findByCategoriaPadreCategoriaIdAndActivoTrueOrderByNombre(1))
                .thenReturn(List.of());

        List<CategoriaResponse> result = service.listTree();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).hijos()).isEmpty();
    }

    // ── list (paginado) ─────────────────────────────────────────────

    @Test
    @DisplayName("list paginado: retorna pagina de categorias planas")
    void list_returnsPage() {
        Pageable pg = PageRequest.of(0, 10);
        Categoria c = sampleCat(1, "Herramientas", null, (short) 0);
        when(repo.findByActivoTrue(pg)).thenReturn(new PageImpl<>(List.of(c), pg, 1));

        var result = service.list(null, pg);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).nombre()).isEqualTo("Herramientas");
    }

    // ── getById ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getById encontrado: retorna CategoriaResponse")
    void getById_found() {
        Categoria c = sampleCat(1, "Herramientas", null, (short) 0);
        when(repo.findById(1)).thenReturn(Optional.of(c));

        CategoriaResponse resp = service.getById(1);

        assertThat(resp.categoriaId()).isEqualTo(1);
        assertThat(resp.nombre()).isEqualTo("Herramientas");
    }

    @Test
    @DisplayName("getById inexistente: RecursoNoEncontradoException")
    void getById_notFound() {
        when(repo.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(999))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ── create ──────────────────────────────────────────────────────

    @Test
    @DisplayName("create sin padre: nivel = 0")
    void create_withoutParent_nivelCero() {
        CategoriaRequest req = new CategoriaRequest("Nueva", null);
        Categoria saved = sampleCat(10, "Nueva", null, (short) 0);
        when(repo.save(any(Categoria.class))).thenReturn(saved);

        CategoriaResponse resp = service.create(req);

        assertThat(resp.categoriaId()).isEqualTo(10);
        assertThat(resp.nivel()).isZero();
        verify(repo).save(any(Categoria.class));
    }

    @Test
    @DisplayName("create con padre: busca parent, nivel = parent.nivel + 1")
    void create_withParent_setsNivel() {
        Categoria parent = sampleCat(1, "Padre", null, (short) 1);
        when(repo.findById(1)).thenReturn(Optional.of(parent));

        CategoriaRequest req = new CategoriaRequest("Hijo", 1);
        Categoria saved = sampleCat(10, "Hijo", parent, (short) 2);
        when(repo.save(any(Categoria.class))).thenReturn(saved);

        CategoriaResponse resp = service.create(req);

        assertThat(resp.nivel()).isEqualTo((short) 2);
    }

    @Test
    @DisplayName("create con padre inexistente: RecursoNoEncontradoException")
    void create_parentNotFound() {
        when(repo.findById(999)).thenReturn(Optional.empty());

        CategoriaRequest req = new CategoriaRequest("Hijo", 999);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ── update ──────────────────────────────────────────────────────

    @Test
    @DisplayName("update encontrado: actualiza nombre y guarda")
    void update_found() {
        Categoria existing = sampleCat(1, "Vieja", null, (short) 0);
        when(repo.findById(1)).thenReturn(Optional.of(existing));
        Categoria saved = sampleCat(1, "Nueva", null, (short) 0);
        when(repo.save(any(Categoria.class))).thenReturn(saved);

        CategoriaResponse resp = service.update(1, new CategoriaRequest("Nueva", null));

        assertThat(resp.nombre()).isEqualTo("Nueva");
    }

    @Test
    @DisplayName("update inexistente: RecursoNoEncontradoException")
    void update_notFound() {
        when(repo.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(999, new CategoriaRequest("X", null)))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ── deactivate ──────────────────────────────────────────────────

    @Test
    @DisplayName("deactivate: categoria se marca inactiva y se guarda")
    void deactivate_setsInactive() {
        Categoria existing = sampleCat(1, "Cat", null, (short) 0);
        when(repo.findById(1)).thenReturn(Optional.of(existing));

        service.deactivate(1);

        assertThat(existing.getActivo()).isFalse();
        verify(repo).save(existing);
    }

    @Test
    @DisplayName("deactivate inexistente: RecursoNoEncontradoException")
    void deactivate_notFound() {
        when(repo.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivate(999))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }
}
