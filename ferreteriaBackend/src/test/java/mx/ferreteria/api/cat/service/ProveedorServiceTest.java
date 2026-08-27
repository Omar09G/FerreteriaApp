package mx.ferreteria.api.cat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import mx.ferreteria.api.cat.dto.CatDtos.ProveedorRequest;
import mx.ferreteria.api.cat.dto.CatDtos.ProveedorResponse;
import mx.ferreteria.api.cat.entity.Proveedor;
import mx.ferreteria.api.cat.repo.ProveedorRepository;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;

@ExtendWith(MockitoExtension.class)
class ProveedorServiceTest {

    @Mock
    ProveedorRepository repo;

    @InjectMocks
    ProveedorService service;

    private Proveedor sampleProveedor(Integer id, String razonSocial) {
        return Proveedor.builder()
                .proveedorId(id)
                .razonSocial(razonSocial)
                .rfc("RFC" + id)
                .regimenFiscal("601")
                .email("test@test.com")
                .telefono("5512345678")
                .diasCredito(30)
                .limiteCredito(new BigDecimal("100000.00"))
                .activo(true)
                .build();
    }

    // ── list ────────────────────────────────────────────────────────

    @Test
    @DisplayName("list sin query: findByActivoTrue retorna pagina con items")
    void list_all_returnsPage() {
        Pageable pg = PageRequest.of(0, 10);
        Proveedor p1 = sampleProveedor(1, "Proveedor A");
        Proveedor p2 = sampleProveedor(2, "Proveedor B");
        when(repo.findByActivoTrue(pg)).thenReturn(new PageImpl<>(List.of(p1, p2), pg, 2));

        var result = service.list(null, pg);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).razonSocial()).isEqualTo("Proveedor A");
        assertThat(result.getContent().get(1).razonSocial()).isEqualTo("Proveedor B");
    }

    @Test
    @DisplayName("list con query: findByActivoTrueAndRazonSocialContainingIgnoreCase retorna filtrado")
    void list_withQuery_returnsFiltered() {
        Pageable pg = PageRequest.of(0, 10);
        Proveedor p = sampleProveedor(1, "Proveedor Uno");
        when(repo.findByActivoTrueAndRazonSocialContainingIgnoreCase("Uno", pg))
                .thenReturn(new PageImpl<>(List.of(p), pg, 1));

        var result = service.list("Uno", pg);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).razonSocial()).isEqualTo("Proveedor Uno");
    }

    // ── getById ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getById encontrado: retorna ProveedorResponse")
    void getById_found() {
        Proveedor p = sampleProveedor(1, "Proveedor A");
        when(repo.findById(1)).thenReturn(Optional.of(p));

        ProveedorResponse resp = service.getById(1);

        assertThat(resp.proveedorId()).isEqualTo(1);
        assertThat(resp.razonSocial()).isEqualTo("Proveedor A");
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
        ProveedorRequest req = new ProveedorRequest("Nuevo Proveedor", "NPC850101ABC",
                "601", "nuevo@test.com", "55112233", 15, new BigDecimal("50000.00"));
        Proveedor saved = sampleProveedor(10, "Nuevo Proveedor");
        when(repo.save(any(Proveedor.class))).thenReturn(saved);

        ProveedorResponse resp = service.create(req);

        assertThat(resp.proveedorId()).isEqualTo(10);
        assertThat(resp.razonSocial()).isEqualTo("Nuevo Proveedor");
        verify(repo).save(any(Proveedor.class));
    }

    // ── update ──────────────────────────────────────────────────────

    @Test
    @DisplayName("update encontrado: actualiza campos y guarda")
    void update_found() {
        Proveedor existing = sampleProveedor(1, "Viejo Nombre");
        when(repo.findById(1)).thenReturn(Optional.of(existing));
        when(repo.save(any(Proveedor.class))).thenReturn(sampleProveedor(1, "Nuevo Nombre"));

        ProveedorResponse resp = service.update(1, new ProveedorRequest("Nuevo Nombre", null,
                null, null, null, null, null));

        assertThat(resp.razonSocial()).isEqualTo("Nuevo Nombre");
        verify(repo).save(existing);
    }

    @Test
    @DisplayName("update inexistente: RecursoNoEncontradoException")
    void update_notFound() {
        when(repo.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(999, new ProveedorRequest("X", null,
                null, null, null, null, null)))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ── deactivate ──────────────────────────────────────────────────

    @Test
    @DisplayName("deactivate: proveedor se marca como inactivo y se guarda")
    void deactivate_setsInactive() {
        Proveedor existing = sampleProveedor(1, "Proveedor A");
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
