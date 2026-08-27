package mx.ferreteria.api.inv.service;

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

import mx.ferreteria.api.inv.dto.InvDtos.AlmacenRequest;
import mx.ferreteria.api.inv.dto.InvDtos.AlmacenResponse;
import mx.ferreteria.api.inv.entity.Almacen;
import mx.ferreteria.api.inv.repo.AlmacenRepository;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;

@ExtendWith(MockitoExtension.class)
class AlmacenServiceTest {

    @Mock
    AlmacenRepository repo;

    @InjectMocks
    AlmacenService service;

    private Almacen sampleAlmacen(Integer id, String nombre) {
        return Almacen.builder()
                .almacenId(id)
                .nombre(nombre)
                .direccion("Direccion " + id)
                .telefono("551234" + id)
                .esPuntoVenta(true)
                .activo(true)
                .build();
    }

    // ── list ────────────────────────────────────────────────────────

    @Test
    @DisplayName("list sin query: findByActivoTrue retorna pagina con items")
    void list_all_returnsPage() {
        Pageable pg = PageRequest.of(0, 10);
        Almacen a = sampleAlmacen(1, "Almacen Central");
        when(repo.findByActivoTrue(pg)).thenReturn(new PageImpl<>(List.of(a), pg, 1));

        var result = service.list(null, pg);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).nombre()).isEqualTo("Almacen Central");
    }

    @Test
    @DisplayName("list con query: findByNombreContainingIgnoreCase retorna filtrado")
    void list_withQuery_returnsFiltered() {
        Pageable pg = PageRequest.of(0, 10);
        Almacen a = sampleAlmacen(1, "Almacen Norte");
        when(repo.findByNombreContainingIgnoreCase("Norte", pg))
                .thenReturn(new PageImpl<>(List.of(a), pg, 1));

        var result = service.list("Norte", pg);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).nombre()).isEqualTo("Almacen Norte");
    }

    // ── getById ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getById encontrado: retorna AlmacenResponse")
    void getById_found() {
        Almacen a = sampleAlmacen(1, "Almacen Central");
        when(repo.findById(1)).thenReturn(Optional.of(a));

        AlmacenResponse resp = service.getById(1);

        assertThat(resp.almacenId()).isEqualTo(1);
        assertThat(resp.nombre()).isEqualTo("Almacen Central");
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
        AlmacenRequest req = new AlmacenRequest("Almacen Nuevo", "Calle 1", "551111", true);
        Almacen saved = sampleAlmacen(10, "Almacen Nuevo");
        when(repo.save(any(Almacen.class))).thenReturn(saved);

        AlmacenResponse resp = service.create(req);

        assertThat(resp.almacenId()).isEqualTo(10);
        assertThat(resp.nombre()).isEqualTo("Almacen Nuevo");
        verify(repo).save(any(Almacen.class));
    }

    // ── update ──────────────────────────────────────────────────────

    @Test
    @DisplayName("update encontrado: actualiza campos y guarda")
    void update_found() {
        Almacen existing = sampleAlmacen(1, "Viejo Nombre");
        when(repo.findById(1)).thenReturn(Optional.of(existing));
        when(repo.save(any(Almacen.class))).thenReturn(sampleAlmacen(1, "Nuevo Nombre"));

        AlmacenResponse resp = service.update(1,
                new AlmacenRequest("Nuevo Nombre", "Dir", "55000", false));

        assertThat(resp.nombre()).isEqualTo("Nuevo Nombre");
        verify(repo).save(existing);
    }

    @Test
    @DisplayName("update inexistente: RecursoNoEncontradoException")
    void update_notFound() {
        when(repo.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(999,
                new AlmacenRequest("X", null, null, null)))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ── deactivate ──────────────────────────────────────────────────

    @Test
    @DisplayName("deactivate: almacen se marca como inactivo y se guarda")
    void deactivate_setsInactive() {
        Almacen existing = sampleAlmacen(1, "Almacen A");
        when(repo.findById(1)).thenReturn(Optional.of(existing));

        service.deactivate(1);

        assertThat(existing.getActivo()).isFalse();
        verify(repo).save(existing);
    }
}
