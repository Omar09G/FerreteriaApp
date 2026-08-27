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

import mx.ferreteria.api.cat.dto.CatDtos.UnidadMedidaRequest;
import mx.ferreteria.api.cat.dto.CatDtos.UnidadMedidaResponse;
import mx.ferreteria.api.cat.entity.UnidadMedida;
import mx.ferreteria.api.cat.repo.UnidadMedidaRepository;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;

@ExtendWith(MockitoExtension.class)
class UnidadMedidaServiceTest {

    @Mock
    UnidadMedidaRepository repo;

    @InjectMocks
    UnidadMedidaService service;

    private UnidadMedida sampleUnidad(Integer id, String clave, String nombre) {
        return UnidadMedida.builder()
                .unidadId(id)
                .clave(clave)
                .nombre(nombre)
                .permiteFraccion(false)
                .activo(true)
                .build();
    }

    // ── list ────────────────────────────────────────────────────────

    @Test
    @DisplayName("list sin query: findByActivoTrue retorna pagina con items")
    void list_all_returnsPage() {
        Pageable pg = PageRequest.of(0, 10);
        UnidadMedida u1 = sampleUnidad(1, "PZA", "Pieza");
        UnidadMedida u2 = sampleUnidad(2, "KG", "Kilogramo");
        when(repo.findByActivoTrue(pg)).thenReturn(new PageImpl<>(List.of(u1, u2), pg, 2));

        var result = service.list(null, pg);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).clave()).isEqualTo("PZA");
        assertThat(result.getContent().get(1).clave()).isEqualTo("KG");
    }

    @Test
    @DisplayName("list con query: findByActivoTrueAndNombreContainingIgnoreCase retorna filtrado")
    void list_withQuery_returnsFiltered() {
        Pageable pg = PageRequest.of(0, 10);
        UnidadMedida u = sampleUnidad(1, "PZA", "Pieza");
        when(repo.findByActivoTrueAndNombreContainingIgnoreCase("Pieza", pg))
                .thenReturn(new PageImpl<>(List.of(u), pg, 1));

        var result = service.list("Pieza", pg);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).nombre()).isEqualTo("Pieza");
    }

    // ── getById ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getById encontrado: retorna UnidadMedidaResponse")
    void getById_found() {
        UnidadMedida u = sampleUnidad(1, "PZA", "Pieza");
        when(repo.findById(1)).thenReturn(Optional.of(u));

        UnidadMedidaResponse resp = service.getById(1);

        assertThat(resp.unidadId()).isEqualTo(1);
        assertThat(resp.clave()).isEqualTo("PZA");
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
        UnidadMedidaRequest req = new UnidadMedidaRequest("LT", "Litro", true);
        UnidadMedida saved = sampleUnidad(10, "LT", "Litro");
        when(repo.save(any(UnidadMedida.class))).thenReturn(saved);

        UnidadMedidaResponse resp = service.create(req);

        assertThat(resp.unidadId()).isEqualTo(10);
        assertThat(resp.clave()).isEqualTo("LT");
        verify(repo).save(any(UnidadMedida.class));
    }

    // ── update ──────────────────────────────────────────────────────

    @Test
    @DisplayName("update encontrado: actualiza campos y guarda")
    void update_found() {
        UnidadMedida existing = sampleUnidad(1, "PZA", "Pieza Vieja");
        when(repo.findById(1)).thenReturn(Optional.of(existing));
        when(repo.save(any(UnidadMedida.class))).thenReturn(sampleUnidad(1, "PZA", "Pieza Nueva"));

        UnidadMedidaResponse resp = service.update(1, new UnidadMedidaRequest("PZA", "Pieza Nueva", null));

        assertThat(resp.nombre()).isEqualTo("Pieza Nueva");
        verify(repo).save(existing);
    }

    @Test
    @DisplayName("update inexistente: RecursoNoEncontradoException")
    void update_notFound() {
        when(repo.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(999, new UnidadMedidaRequest("X", "X", null)))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ── deactivate ──────────────────────────────────────────────────

    @Test
    @DisplayName("deactivate: unidad se marca como inactiva y se guarda")
    void deactivate_setsInactive() {
        UnidadMedida existing = sampleUnidad(1, "PZA", "Pieza");
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
