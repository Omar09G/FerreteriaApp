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

import mx.ferreteria.api.cat.dto.CatDtos.ClienteRequest;
import mx.ferreteria.api.cat.dto.CatDtos.ClienteResponse;
import mx.ferreteria.api.cat.entity.Cliente;
import mx.ferreteria.api.cat.repo.ClienteRepository;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;

@ExtendWith(MockitoExtension.class)
class ClienteServiceTest {

    @Mock
    ClienteRepository repo;

    @InjectMocks
    ClienteService service;

    private Cliente sampleCliente(Long id, String razonSocial) {
        return Cliente.builder()
                .clienteId(id)
                .tipoPersona("FISICA")
                .razonSocial(razonSocial)
                .nombreComercial(null)
                .rfc("PEPJ800101ABC")
                .telefono("5512345678")
                .email("test@test.com")
                .limiteCredito(new BigDecimal("50000.00"))
                .diasCredito(30)
                .esMayorista(false)
                .activo(true)
                .build();
    }

    // ── list ────────────────────────────────────────────────────────

    @Test
    @DisplayName("list sin query: findByActivoTrue retorna pagina con items")
    void list_all_returnsPage() {
        Pageable pg = PageRequest.of(0, 10);
        Cliente c1 = sampleCliente(1L, "Cliente A");
        Cliente c2 = sampleCliente(2L, "Cliente B");
        when(repo.findByActivoTrue(pg)).thenReturn(new PageImpl<>(List.of(c1, c2), pg, 2));

        var result = service.list(null, pg);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).razonSocial()).isEqualTo("Cliente A");
        assertThat(result.getContent().get(1).razonSocial()).isEqualTo("Cliente B");
    }

    @Test
    @DisplayName("list con query: findByActivoTrueAndRazonSocialContainingIgnoreCase retorna filtrado")
    void list_withQuery_returnsFiltered() {
        Pageable pg = PageRequest.of(0, 10);
        Cliente c = sampleCliente(1L, "Cliente Uno");
        when(repo.findByActivoTrueAndRazonSocialContainingIgnoreCase("Uno", pg))
                .thenReturn(new PageImpl<>(List.of(c), pg, 1));

        var result = service.list("Uno", pg);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).razonSocial()).isEqualTo("Cliente Uno");
    }

    // ── getById ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getById encontrado: retorna ClienteResponse")
    void getById_found() {
        Cliente c = sampleCliente(1L, "Cliente A");
        when(repo.findById(1L)).thenReturn(Optional.of(c));

        ClienteResponse resp = service.getById(1L);

        assertThat(resp.clienteId()).isEqualTo(1L);
        assertThat(resp.razonSocial()).isEqualTo("Cliente A");
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
    @DisplayName("create: save retorna entidad con id")
    void create_savesAndReturns() {
        ClienteRequest req = new ClienteRequest("MORAL", "Nuevo Cliente", null,
                "NCC850101ABC", "55112233", "nuevo@test.com",
                new BigDecimal("50000.00"), 15, true);
        Cliente saved = sampleCliente(10L, "Nuevo Cliente");
        when(repo.save(any(Cliente.class))).thenReturn(saved);

        ClienteResponse resp = service.create(req);

        assertThat(resp.clienteId()).isEqualTo(10L);
        assertThat(resp.razonSocial()).isEqualTo("Nuevo Cliente");
        verify(repo).save(any(Cliente.class));
    }

    // ── update ──────────────────────────────────────────────────────

    @Test
    @DisplayName("update encontrado: actualiza campos y guarda")
    void update_found() {
        Cliente existing = sampleCliente(1L, "Viejo Nombre");
        when(repo.findById(1L)).thenReturn(Optional.of(existing));
        when(repo.save(any(Cliente.class))).thenReturn(sampleCliente(1L, "Nuevo Nombre"));

        ClienteResponse resp = service.update(1L, new ClienteRequest(null, "Nuevo Nombre",
                null, null, null, null, null, null, null));

        assertThat(resp.razonSocial()).isEqualTo("Nuevo Nombre");
        verify(repo).save(existing);
    }

    @Test
    @DisplayName("update inexistente: RecursoNoEncontradoException")
    void update_notFound() {
        when(repo.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(999L, new ClienteRequest(null, "X",
                null, null, null, null, null, null, null)))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ── deactivate ──────────────────────────────────────────────────

    @Test
    @DisplayName("deactivate: cliente se marca como inactivo y se guarda")
    void deactivate_setsInactive() {
        Cliente existing = sampleCliente(1L, "Cliente A");
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
