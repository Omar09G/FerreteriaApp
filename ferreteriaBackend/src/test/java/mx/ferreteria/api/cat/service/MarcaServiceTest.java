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

import mx.ferreteria.api.cat.dto.CatDtos.MarcaRequest;
import mx.ferreteria.api.cat.dto.CatDtos.MarcaResponse;
import mx.ferreteria.api.cat.entity.Marca;
import mx.ferreteria.api.cat.repo.MarcaRepository;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;

@ExtendWith(MockitoExtension.class)
class MarcaServiceTest {

    @Mock
    MarcaRepository repo;

    @InjectMocks
    MarcaService service;

    private Marca sampleMarca(Integer id, String nombre) {
        return Marca.builder().marcaId(id).nombre(nombre).activo(true).build();
    }

    // ── list ────────────────────────────────────────────────────────

    @Test
    @DisplayName("list sin query: findByActivoTrue retorna pagina con items")
    void list_all_returnsPage() {
        Pageable pg = PageRequest.of(0, 10);
        Marca m1 = sampleMarca(1, "Acme");
        Marca m2 = sampleMarca(2, "Bosch");
        when(repo.findByActivoTrue(pg)).thenReturn(new PageImpl<>(List.of(m1, m2), pg, 2));

        var result = service.list(null, pg);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).nombre()).isEqualTo("Acme");
        assertThat(result.getContent().get(1).nombre()).isEqualTo("Bosch");
    }

    @Test
    @DisplayName("list con query: findByActivoTrueAndNombreContainingIgnoreCase retorna filtrado")
    void list_withQuery_returnsFiltered() {
        Pageable pg = PageRequest.of(0, 10);
        Marca m = sampleMarca(1, "Acme Corp");
        when(repo.findByActivoTrueAndNombreContainingIgnoreCase("Acme", pg))
                .thenReturn(new PageImpl<>(List.of(m), pg, 1));

        var result = service.list("Acme", pg);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).nombre()).isEqualTo("Acme Corp");
    }

    // ── getById ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getById encontrado: retorna MarcaResponse")
    void getById_found() {
        Marca m = sampleMarca(1, "Acme");
        when(repo.findById(1)).thenReturn(Optional.of(m));

        MarcaResponse resp = service.getById(1);

        assertThat(resp.marcaId()).isEqualTo(1);
        assertThat(resp.nombre()).isEqualTo("Acme");
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
    @DisplayName("create: save retorna entidad con id")
    void create_savesAndReturns() {
        MarcaRequest req = new MarcaRequest("NuevaMarca");
        Marca saved = sampleMarca(10, "NuevaMarca");
        when(repo.save(any(Marca.class))).thenReturn(saved);

        MarcaResponse resp = service.create(req);

        assertThat(resp.marcaId()).isEqualTo(10);
        assertThat(resp.nombre()).isEqualTo("NuevaMarca");
        verify(repo).save(any(Marca.class));
    }

    // ── update ──────────────────────────────────────────────────────

    @Test
    @DisplayName("update encontrado: actualiza nombre y guarda")
    void update_found() {
        Marca existing = sampleMarca(1, "Vieja");
        when(repo.findById(1)).thenReturn(Optional.of(existing));
        when(repo.save(any(Marca.class))).thenReturn(sampleMarca(1, "Nueva"));

        MarcaResponse resp = service.update(1, new MarcaRequest("Nueva"));

        assertThat(resp.nombre()).isEqualTo("Nueva");
        verify(repo).save(existing);
    }

    @Test
    @DisplayName("update inexistente: RecursoNoEncontradoException")
    void update_notFound() {
        when(repo.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(999, new MarcaRequest("X")))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ── deactivate ──────────────────────────────────────────────────

    @Test
    @DisplayName("deactivate: marca se marca como inactiva y se guarda")
    void deactivate_setsInactive() {
        Marca existing = sampleMarca(1, "Acme");
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
