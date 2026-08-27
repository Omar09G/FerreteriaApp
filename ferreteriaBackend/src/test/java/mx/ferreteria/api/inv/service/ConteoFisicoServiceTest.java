package mx.ferreteria.api.inv.service;

import static org.assertj.core.api.Assertions.assertThat;
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

import mx.ferreteria.api.inv.dto.InvDtos.ConteoFisicoDetalleRequest;
import mx.ferreteria.api.inv.dto.InvDtos.ConteoFisicoRequest;
import mx.ferreteria.api.inv.dto.InvDtos.ConteoFisicoResponse;
import mx.ferreteria.api.inv.entity.Almacen;
import mx.ferreteria.api.inv.entity.ConteoFisico;
import mx.ferreteria.api.inv.entity.ConteoFisicoDetalle;
import mx.ferreteria.api.inv.entity.Inventario;
import mx.ferreteria.api.inv.entity.InventarioId;
import mx.ferreteria.api.inv.repo.AlmacenRepository;
import mx.ferreteria.api.inv.repo.ConteoFisicoDetalleRepository;
import mx.ferreteria.api.inv.repo.ConteoFisicoRepository;
import mx.ferreteria.api.inv.repo.InventarioRepository;

@ExtendWith(MockitoExtension.class)
class ConteoFisicoServiceTest {

    @Mock
    ConteoFisicoRepository repo;

    @Mock
    ConteoFisicoDetalleRepository detalleRepo;

    @Mock
    InventarioRepository inventarioRepo;

    @Mock
    AlmacenRepository almacenRepo;

    @InjectMocks
    ConteoFisicoService service;

    private Almacen sampleAlmacen(Integer id, String nombre) {
        return Almacen.builder().almacenId(id).nombre(nombre).build();
    }

    private ConteoFisico sampleConteo(Long id, Integer almacenId) {
        return ConteoFisico.builder()
                .conteoId(id)
                .almacenId(almacenId)
                .estado("EN_PROCESO")
                .usuarioId(1)
                .observaciones("Conteo mensual")
                .build();
    }

    // ── listByAlmacen ───────────────────────────────────────────────

    @Test
    @DisplayName("listByAlmacen: retorna pagina de conteos")
    void listByAlmacen_returnsPage() {
        Pageable pg = PageRequest.of(0, 10);
        ConteoFisico c = sampleConteo(1L, 1);
        when(repo.findByAlmacenId(1, pg))
                .thenReturn(new PageImpl<>(List.of(c), pg, 1));
        when(almacenRepo.findById(1)).thenReturn(Optional.of(sampleAlmacen(1, "Central")));

        var result = service.list(1, pg);

        assertThat(result.getContent()).hasSize(1);
        ConteoFisicoResponse resp = result.getContent().get(0);
        assertThat(resp.conteoId()).isEqualTo(1L);
        assertThat(resp.almacenNombre()).isEqualTo("Central");
    }

    // ── getById ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getById: retorna conteo con almacen resuelto")
    void getById_returnsConteo() {
        ConteoFisico c = sampleConteo(1L, 1);
        when(repo.findById(1L)).thenReturn(Optional.of(c));
        when(almacenRepo.findById(1)).thenReturn(Optional.of(sampleAlmacen(1, "Central")));

        ConteoFisicoResponse resp = service.getById(1L);

        assertThat(resp.conteoId()).isEqualTo(1L);
        assertThat(resp.estado()).isEqualTo("EN_PROCESO");
        assertThat(resp.observaciones()).isEqualTo("Conteo mensual");
    }

    // ── create ──────────────────────────────────────────────────────

    @Test
    @DisplayName("create: guarda conteo con detalles y cantidadSistema desde inventario")
    void create_ok() {
        ConteoFisicoRequest req = new ConteoFisicoRequest(1, "Conteo trimestral",
                List.of(new ConteoFisicoDetalleRequest(1L, new BigDecimal("48.000"))));
        when(almacenRepo.findById(1)).thenReturn(Optional.of(sampleAlmacen(1, "Central")));

        ConteoFisico savedConteo = sampleConteo(1L, 1);
        when(repo.save(any(ConteoFisico.class))).thenReturn(savedConteo);

        Inventario inv = Inventario.builder()
                .productoId(1L).almacenId(1)
                .stock(new BigDecimal("50.000"))
                .build();
        when(inventarioRepo.findById(new InventarioId(1L, 1)))
                .thenReturn(Optional.of(inv));
        when(detalleRepo.save(any(ConteoFisicoDetalle.class)))
                .thenReturn(ConteoFisicoDetalle.builder()
                        .conteoId(1L).productoId(1L)
                        .cantidadSistema(new BigDecimal("50.000"))
                        .cantidadFisica(new BigDecimal("48.000"))
                        .build());
        when(almacenRepo.findById(1)).thenReturn(Optional.of(sampleAlmacen(1, "Central")));

        ConteoFisicoResponse resp = service.create(req);

        assertThat(resp.conteoId()).isEqualTo(1L);
        assertThat(resp.almacenNombre()).isEqualTo("Central");
        verify(repo).save(any(ConteoFisico.class));
        verify(detalleRepo).save(any(ConteoFisicoDetalle.class));
    }
}
