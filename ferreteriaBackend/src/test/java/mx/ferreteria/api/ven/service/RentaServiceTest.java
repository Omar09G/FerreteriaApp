package mx.ferreteria.api.ven.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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

import mx.ferreteria.api.cat.entity.Cliente;
import mx.ferreteria.api.cat.entity.FormaPago;
import mx.ferreteria.api.cat.entity.Producto;
import mx.ferreteria.api.cat.repo.ClienteRepository;
import mx.ferreteria.api.cat.repo.FormaPagoRepository;
import mx.ferreteria.api.cat.repo.ProductoRepository;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.fin.service.CajaService;
import mx.ferreteria.api.inv.entity.Almacen;
import mx.ferreteria.api.inv.repo.AlmacenRepository;
import mx.ferreteria.api.ven.dto.VenDtos;
import mx.ferreteria.api.ven.entity.Renta;
import mx.ferreteria.api.ven.entity.RentaDetalle;
import mx.ferreteria.api.ven.repo.RentaDetalleRepository;
import mx.ferreteria.api.ven.repo.RentaRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RentaServiceTest {

    @Mock RentaRepository repo;
    @Mock RentaDetalleRepository detalleRepo;
    @Mock AlmacenRepository almacenRepo;
    @Mock ClienteRepository clienteRepo;
    @Mock ProductoRepository productoRepo;
    @Mock FormaPagoRepository formaPagoRepo;
    @Mock CajaService cajaService;

    @InjectMocks
    RentaService service;

    // ── helpers ──────────────────────────────────────────────────────

    private Renta sampleRenta(Long id, String estado) {
        return Renta.builder()
                .rentaId(id).folio("R-001").clienteId(1L).almacenId(1)
                .fechaRenta(Instant.now()).fechaDevEsperada(LocalDate.now().plusDays(7))
                .deposito(new BigDecimal("500.00")).costoTotal(BigDecimal.ZERO)
                .estado(estado).usuarioId(1).build();
    }

    private void stubToResponse() {
        when(clienteRepo.findById(1L))
                .thenReturn(Optional.of(Cliente.builder().clienteId(1L).razonSocial("Juan Perez").build()));
        when(almacenRepo.findById(1))
                .thenReturn(Optional.of(Almacen.builder().almacenId(1).nombre("Almacen Norte").build()));
        when(detalleRepo.findByRentaId(anyLong())).thenReturn(List.of());
        when(productoRepo.findById(anyLong()))
                .thenReturn(Optional.of(Producto.builder().productoId(1L).nombre("Rotomartillo").build()));
    }

    private Pageable pg() {
        return PageRequest.of(0, 10);
    }

    // ── list ────────────────────────────────────────────────────────

    @Test
    @DisplayName("list sin estado: filtrar sin filtros retorna pagina con items")
    void list_all() {
        Renta r = sampleRenta(1L, "ABIERTA");
        when(repo.filtrar(null, null, null, pg()))
                .thenReturn(new PageImpl<>(List.of(r), pg(), 1));
        stubToResponse();

        var result = service.list(null, null, null, pg());

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).rentaId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("list por estado: filtrar retorna filtrado")
    void list_byEstado() {
        Renta r = sampleRenta(1L, "ABIERTA");
        when(repo.filtrar("ABIERTA", null, null, pg()))
                .thenReturn(new PageImpl<>(List.of(r), pg(), 1));
        stubToResponse();

        var result = service.list("ABIERTA", null, null, pg());

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).estado()).isEqualTo("ABIERTA");
    }

    // ── getById ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getById encontrado: retorna RentaResponse con detalles")
    void getById_found() {
        Renta r = sampleRenta(1L, "ABIERTA");
        when(repo.findById(1L)).thenReturn(Optional.of(r));
        stubToResponse();

        var resp = service.getById(1L);

        assertThat(resp.rentaId()).isEqualTo(1L);
        assertThat(resp.clienteNombre()).isEqualTo("Juan Perez");
        assertThat(resp.almacenNombre()).isEqualTo("Almacen Norte");
    }

    @Test
    @DisplayName("getById inexistente: lanza RecursoNoEncontradoException")
    void getById_notFound() {
        when(repo.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(999L))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ── create ──────────────────────────────────────────────────────

    @Test
    @DisplayName("create ok: fechaDevEsperada en futuro, guarda renta y detalles")
    void create_ok() {
        Renta saved = sampleRenta(10L, "ABIERTA");
        when(repo.save(any(Renta.class))).thenReturn(saved);
        when(repo.findById(10L)).thenReturn(Optional.of(saved));
        when(formaPagoRepo.findById(anyInt()))
                .thenReturn(Optional.of(FormaPago.builder().formaPagoId(1).nombre("EFECTIVO").build()));
        stubToResponse();

        VenDtos.RentaRequest req = new VenDtos.RentaRequest(
                1L, 1, null, 1, LocalDate.now().plusDays(14),
                new BigDecimal("500.00"),
                List.of(new VenDtos.RentaDetalleRequest(1L, new BigDecimal("2.000"), new BigDecimal("25.00"))));

        var resp = service.create(req);

        assertThat(resp.rentaId()).isEqualTo(10L);
        verify(repo).save(any(Renta.class));
        verify(detalleRepo).save(any(RentaDetalle.class));
    }

    @Test
    @DisplayName("create fecha pasada: lanza ReglaNegocioException")
    void create_pastDate() {
        VenDtos.RentaRequest req = new VenDtos.RentaRequest(
                1L, 1, null, 1, LocalDate.now().minusDays(1),
                new BigDecimal("500.00"), List.of());

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(ReglaNegocioException.class);
    }

    // ── devolver ────────────────────────────────────────────────────

    @Test
    @DisplayName("devolver ok: renta ABIERTA se marca como DEVUELTA")
    void devolver_ok() {
        Renta r = sampleRenta(1L, "ABIERTA");
        when(repo.findById(1L)).thenReturn(Optional.of(r));
        stubToResponse();

        VenDtos.RentaDevolucionRequest req = new VenDtos.RentaDevolucionRequest(List.of());

        var resp = service.devolver(1L, req);

        assertThat(r.getEstado()).isEqualTo("DEVUELTA");
        assertThat(r.getFechaDevReal()).isNotNull();
        verify(repo).save(r);
        assertThat(resp.estado()).isEqualTo("DEVUELTA");
    }

    @Test
    @DisplayName("devolver estado incorrecto (CANCELADA): lanza ReglaNegocioException")
    void devolver_wrongEstado() {
        Renta r = sampleRenta(1L, "CANCELADA");
        when(repo.findById(1L)).thenReturn(Optional.of(r));

        VenDtos.RentaDevolucionRequest req = new VenDtos.RentaDevolucionRequest(List.of());

        assertThatThrownBy(() -> service.devolver(1L, req))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("devolver inexistente: lanza RecursoNoEncontradoException")
    void devolver_notFound() {
        when(repo.findById(999L)).thenReturn(Optional.empty());

        VenDtos.RentaDevolucionRequest req = new VenDtos.RentaDevolucionRequest(List.of());

        assertThatThrownBy(() -> service.devolver(999L, req))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ── cancelar ────────────────────────────────────────────────────

    @Test
    @DisplayName("cancelar ok: renta ABIERTA se marca como CANCELADA")
    void cancelar_ok() {
        Renta r = sampleRenta(1L, "ABIERTA");
        when(repo.findById(1L)).thenReturn(Optional.of(r));
        stubToResponse();

        var resp = service.cancelar(1L);

        assertThat(r.getEstado()).isEqualTo("CANCELADA");
        verify(repo).save(r);
        assertThat(resp.estado()).isEqualTo("CANCELADA");
    }

    @Test
    @DisplayName("cancelar estado incorrecto (DEVUELTA): lanza ReglaNegocioException")
    void cancelar_wrongEstado() {
        Renta r = sampleRenta(1L, "DEVUELTA");
        when(repo.findById(1L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.cancelar(1L))
                .isInstanceOf(ReglaNegocioException.class);
    }
}
