package mx.ferreteria.api.ven.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.common.time.ZonaHoraria;
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
                .isInstanceOf(RecursoNoEncontradoException.class)
                .satisfies(e -> assertThat(((RecursoNoEncontradoException) e).errorCode())
                        .isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO));
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
                .isInstanceOf(ReglaNegocioException.class)
                .satisfies(e -> assertThat(((ReglaNegocioException) e).errorCode())
                        .isEqualTo(ErrorCode.VALOR_INVALIDO));
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
    @DisplayName("devolver con detalles: aplica dias y recalcula costoTotal desde costo_dia de BD")
    void devolver_conDetalles_recalculaTotal() {
        Renta r = sampleRenta(1L, "ABIERTA");
        when(repo.findById(1L)).thenReturn(Optional.of(r));
        stubToResponse();
        // Simula fila post-BD: subtotal GENERATED ya calculado (costo_dia x dias).
        RentaDetalle det = RentaDetalle.builder().rentaId(1L).productoId(7L)
                .cantidad(BigDecimal.ONE).costoDia(new BigDecimal("100.00"))
                .diasCobrados(BigDecimal.ZERO).subtotal(new BigDecimal("200.00")).build();
        when(detalleRepo.findByRentaId(1L)).thenReturn(List.of(det));

        VenDtos.RentaDevolucionRequest req = new VenDtos.RentaDevolucionRequest(List.of(
                new VenDtos.RentaDevolucionDetalleRequest(7L, new BigDecimal("2"))));

        var resp = service.devolver(1L, req);

        assertThat(det.getDiasCobrados()).isEqualByComparingTo(new BigDecimal("2"));
        assertThat(r.getCostoTotal()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(r.getEstado()).isEqualTo("DEVUELTA");
        assertThat(resp.costoTotal()).isEqualByComparingTo(new BigDecimal("200.00"));
    }

    @Test
    @DisplayName("devolver con producto ajeno a la renta: lanza ReglaNegocioException")
    void devolver_productoAjeno_lanzaError() {
        Renta r = sampleRenta(1L, "ABIERTA");
        when(repo.findById(1L)).thenReturn(Optional.of(r));
        stubToResponse();
        RentaDetalle det = RentaDetalle.builder().rentaId(1L).productoId(7L)
                .cantidad(BigDecimal.ONE).costoDia(new BigDecimal("100.00"))
                .diasCobrados(BigDecimal.ZERO).build();
        when(detalleRepo.findByRentaId(1L)).thenReturn(List.of(det));

        VenDtos.RentaDevolucionRequest req = new VenDtos.RentaDevolucionRequest(List.of(
                new VenDtos.RentaDevolucionDetalleRequest(99L, BigDecimal.ONE)));

        assertThatThrownBy(() -> service.devolver(1L, req))
                .isInstanceOf(ReglaNegocioException.class)
                .satisfies(e -> assertThat(((ReglaNegocioException) e).errorCode())
                        .isEqualTo(ErrorCode.VALOR_INVALIDO));
    }

    @Test
    @DisplayName("devolver estado incorrecto (CANCELADA): lanza ReglaNegocioException")
    void devolver_wrongEstado() {
        Renta r = sampleRenta(1L, "CANCELADA");
        when(repo.findById(1L)).thenReturn(Optional.of(r));

        VenDtos.RentaDevolucionRequest req = new VenDtos.RentaDevolucionRequest(List.of());

        assertThatThrownBy(() -> service.devolver(1L, req))
                .isInstanceOf(ReglaNegocioException.class)
                .satisfies(e -> assertThat(((ReglaNegocioException) e).errorCode())
                        .isEqualTo(ErrorCode.VALOR_INVALIDO));
    }

    @Test
    @DisplayName("devolver inexistente: lanza RecursoNoEncontradoException")
    void devolver_notFound() {
        when(repo.findById(999L)).thenReturn(Optional.empty());

        VenDtos.RentaDevolucionRequest req = new VenDtos.RentaDevolucionRequest(List.of());

        assertThatThrownBy(() -> service.devolver(999L, req))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .satisfies(e -> assertThat(((RecursoNoEncontradoException) e).errorCode())
                        .isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO));
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
                .isInstanceOf(ReglaNegocioException.class)
                .satisfies(e -> assertThat(((ReglaNegocioException) e).errorCode())
                        .isEqualTo(ErrorCode.VALOR_INVALIDO));
    }

    // ── list ruta batch (2+ rentas) ─────────────────────────────────

    @Test
    @DisplayName("list vacia: retorna pagina vacia sin consultar detalles")
    void list_empty() {
        Pageable p = pg();
        when(repo.filtrar(null, null, null, p))
                .thenReturn(new PageImpl<>(List.of(), p, 0));

        var result = service.list(null, null, null, p);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        verify(detalleRepo, never()).findByRentaIdIn(any());
    }

    @Test
    @DisplayName("list multi: resuelve nombres y detalles; ausentes quedan null")
    void list_multi_resuelveNombres() {
        Renta r1 = sampleRenta(1L, "ABIERTA");
        Renta r2 = Renta.builder()
                .rentaId(2L).folio("R-002").clienteId(2L).almacenId(1)
                .fechaRenta(Instant.now()).fechaDevEsperada(LocalDate.now().plusDays(7))
                .deposito(new BigDecimal("100.00")).costoTotal(BigDecimal.ZERO)
                .estado("VENCIDA").usuarioId(1).build();
        Pageable p = pg();
        when(repo.filtrar(null, null, null, p))
                .thenReturn(new PageImpl<>(List.of(r1, r2), p, 2));
        RentaDetalle d1 = RentaDetalle.builder().rentaId(1L).productoId(10L)
                .cantidad(BigDecimal.ONE).costoDia(new BigDecimal("50.00"))
                .diasCobrados(BigDecimal.ONE).subtotal(new BigDecimal("50.00")).build();
        RentaDetalle d2 = RentaDetalle.builder().rentaId(2L).productoId(20L)
                .cantidad(BigDecimal.ONE).costoDia(new BigDecimal("30.00"))
                .diasCobrados(BigDecimal.ONE).subtotal(new BigDecimal("30.00")).build();
        when(detalleRepo.findByRentaIdIn(List.of(1L, 2L))).thenReturn(List.of(d1, d2));
        when(clienteRepo.findAllById(anySet())).thenReturn(List.of(
                Cliente.builder().clienteId(1L).razonSocial("Juan Perez").build()));
        when(almacenRepo.findAllById(anySet())).thenReturn(List.of(
                Almacen.builder().almacenId(1).nombre("Almacen Norte").build()));
        when(productoRepo.findAllById(anySet())).thenReturn(List.of(
                Producto.builder().productoId(10L).nombre("Rotomartillo").build()));

        var result = service.list(null, null, null, p);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent().get(0).clienteNombre()).isEqualTo("Juan Perez");
        assertThat(result.getContent().get(0).almacenNombre()).isEqualTo("Almacen Norte");
        assertThat(result.getContent().get(0).detalles()).hasSize(1);
        assertThat(result.getContent().get(0).detalles().get(0).productoNombre())
                .isEqualTo("Rotomartillo");
        // cliente 2 y producto 20 ausentes en BD → null
        assertThat(result.getContent().get(1).clienteNombre()).isNull();
        assertThat(result.getContent().get(1).detalles()).hasSize(1);
        assertThat(result.getContent().get(1).detalles().get(0).productoNombre()).isNull();
    }

    @Test
    @DisplayName("list multi sin detalles: no consulta productos")
    void list_multi_sinDetalles() {
        Renta r1 = sampleRenta(1L, "ABIERTA");
        Renta r2 = sampleRenta(2L, "ABIERTA");
        Pageable p = pg();
        when(repo.filtrar(null, null, null, p))
                .thenReturn(new PageImpl<>(List.of(r1, r2), p, 2));
        when(detalleRepo.findByRentaIdIn(any())).thenReturn(List.of());
        when(clienteRepo.findAllById(anySet())).thenReturn(List.of());
        when(almacenRepo.findAllById(anySet())).thenReturn(List.of());

        var result = service.list(null, null, null, p);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).detalles()).isEmpty();
        assertThat(result.getContent().get(0).clienteNombre()).isNull();
        assertThat(result.getContent().get(0).almacenNombre()).isNull();
        verify(productoRepo, never()).findAllById(any());
    }

    // ── create ramas faltantes ──────────────────────────────────────

    @Test
    @DisplayName("create formaPago inexistente: RECURSO_NO_ENCONTRADO")
    void create_formaPagoNotFound() {
        when(formaPagoRepo.findById(1)).thenReturn(Optional.empty());

        VenDtos.RentaRequest req = new VenDtos.RentaRequest(
                1L, 1, null, 1, ZonaHoraria.hoy().plusDays(7),
                new BigDecimal("500.00"),
                List.of(new VenDtos.RentaDetalleRequest(1L, new BigDecimal("2.000"), new BigDecimal("25.00"))));

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .satisfies(e -> assertThat(((RecursoNoEncontradoException) e).errorCode())
                        .isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO));
    }

    @Test
    @DisplayName("create fechaDevEsperada == hoy: limite permitido")
    void create_fechaHoy_ok() {
        Renta saved = sampleRenta(10L, "ABIERTA");
        when(repo.save(any(Renta.class))).thenReturn(saved);
        when(repo.findById(10L)).thenReturn(Optional.of(saved));
        when(formaPagoRepo.findById(anyInt()))
                .thenReturn(Optional.of(FormaPago.builder().formaPagoId(1).nombre("EFECTIVO").build()));
        stubToResponse();

        VenDtos.RentaRequest req = new VenDtos.RentaRequest(
                1L, 1, null, 1, ZonaHoraria.hoy(),
                new BigDecimal("0"),
                List.of(new VenDtos.RentaDetalleRequest(1L, new BigDecimal("1.000"), new BigDecimal("25.00"))));

        var resp = service.create(req);

        assertThat(resp.rentaId()).isEqualTo(10L);
        verify(repo).save(any(Renta.class));
    }

    @Test
    @DisplayName("create con caja: propaga turnoCajaId y usa fallback si no hay refresh")
    void create_conTurno_ySinRefresh() {
        when(repo.save(any(Renta.class))).thenAnswer(inv -> {
            Renta e = inv.getArgument(0);
            e.setRentaId(10L);
            return e;
        });
        when(repo.findById(10L)).thenReturn(Optional.empty());
        when(formaPagoRepo.findById(anyInt()))
                .thenReturn(Optional.of(FormaPago.builder().formaPagoId(1).nombre("EFECTIVO").build()));
        when(cajaService.resolverTurnoAbierto(5, 1)).thenReturn(77L);
        stubToResponse();

        VenDtos.RentaRequest req = new VenDtos.RentaRequest(
                1L, 1, 5, 1, ZonaHoraria.hoy().plusDays(3),
                new BigDecimal("100.00"),
                List.of(
                        new VenDtos.RentaDetalleRequest(1L, new BigDecimal("1.000"), new BigDecimal("10.00")),
                        new VenDtos.RentaDetalleRequest(2L, new BigDecimal("2.000"), new BigDecimal("20.00"))));

        var resp = service.create(req);

        assertThat(resp.turnoCajaId()).isEqualTo(77L);
        assertThat(resp.detalles()).isEmpty();
        verify(detalleRepo, times(2)).save(any(RentaDetalle.class));
        verify(repo).flush();
    }

    // ── devolver ramas faltantes ────────────────────────────────────

    @Test
    @DisplayName("devolver VENCIDA: acepta igual que ABIERTA")
    void devolver_vencida_ok() {
        Renta r = sampleRenta(1L, "VENCIDA");
        when(repo.findById(1L)).thenReturn(Optional.of(r));
        stubToResponse();

        var resp = service.devolver(1L, new VenDtos.RentaDevolucionRequest(List.of()));

        assertThat(r.getEstado()).isEqualTo("DEVUELTA");
        assertThat(resp.estado()).isEqualTo("DEVUELTA");
        verify(repo).save(r);
    }

    @Test
    @DisplayName("devolver subtotal null: suma como cero")
    void devolver_subtotalNull_sumaCero() {
        Renta r = sampleRenta(1L, "ABIERTA");
        when(repo.findById(1L)).thenReturn(Optional.of(r));
        RentaDetalle det = RentaDetalle.builder().rentaId(1L).productoId(7L)
                .cantidad(BigDecimal.ONE).costoDia(new BigDecimal("100.00"))
                .diasCobrados(BigDecimal.ZERO).subtotal(null).build();
        when(detalleRepo.findByRentaId(1L)).thenReturn(List.of(det));
        when(clienteRepo.findById(1L)).thenReturn(Optional.empty());
        when(almacenRepo.findById(1)).thenReturn(Optional.empty());
        when(productoRepo.findById(7L)).thenReturn(Optional.empty());

        var resp = service.devolver(1L, new VenDtos.RentaDevolucionRequest(List.of(
                new VenDtos.RentaDevolucionDetalleRequest(7L, new BigDecimal("2")))));

        assertThat(r.getCostoTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(resp.costoTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(resp.clienteNombre()).isNull();
        assertThat(resp.almacenNombre()).isNull();
        assertThat(resp.detalles().get(0).productoNombre()).isNull();
    }

    @Test
    @DisplayName("devolver varios detalles: costoTotal es la suma de subtotales")
    void devolver_variosDetalles_suma() {
        Renta r = sampleRenta(1L, "VENCIDA");
        when(repo.findById(1L)).thenReturn(Optional.of(r));
        stubToResponse();
        RentaDetalle d1 = RentaDetalle.builder().rentaId(1L).productoId(7L)
                .cantidad(BigDecimal.ONE).costoDia(new BigDecimal("100.00"))
                .diasCobrados(BigDecimal.ZERO).subtotal(new BigDecimal("200.00")).build();
        RentaDetalle d2 = RentaDetalle.builder().rentaId(1L).productoId(8L)
                .cantidad(BigDecimal.ONE).costoDia(new BigDecimal("50.00"))
                .diasCobrados(BigDecimal.ZERO).subtotal(new BigDecimal("150.00")).build();
        when(detalleRepo.findByRentaId(1L)).thenReturn(List.of(d1, d2));

        var resp = service.devolver(1L, new VenDtos.RentaDevolucionRequest(List.of(
                new VenDtos.RentaDevolucionDetalleRequest(7L, new BigDecimal("2")),
                new VenDtos.RentaDevolucionDetalleRequest(8L, new BigDecimal("3")))));

        assertThat(d1.getDiasCobrados()).isEqualByComparingTo(new BigDecimal("2"));
        assertThat(d2.getDiasCobrados()).isEqualByComparingTo(new BigDecimal("3"));
        assertThat(r.getCostoTotal()).isEqualByComparingTo(new BigDecimal("350.00"));
        assertThat(resp.costoTotal()).isEqualByComparingTo(new BigDecimal("350.00"));
        assertThat(resp.detalles()).hasSize(2);
        verify(detalleRepo, times(2)).save(any(RentaDetalle.class));
    }

    // ── cancelar ramas faltantes ────────────────────────────────────

    @Test
    @DisplayName("cancelar inexistente: RECURSO_NO_ENCONTRADO")
    void cancelar_notFound() {
        when(repo.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelar(999L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .satisfies(e -> assertThat(((RecursoNoEncontradoException) e).errorCode())
                        .isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO));
    }

    @Test
    @DisplayName("cancelar VENCIDA: se marca como CANCELADA")
    void cancelar_vencida_ok() {
        Renta r = sampleRenta(1L, "VENCIDA");
        when(repo.findById(1L)).thenReturn(Optional.of(r));
        stubToResponse();

        var resp = service.cancelar(1L);

        assertThat(r.getEstado()).isEqualTo("CANCELADA");
        assertThat(resp.estado()).isEqualTo("CANCELADA");
        verify(repo).save(r);
    }

    // ── getById ramas toResponse ────────────────────────────────────

    @Test
    @DisplayName("getById con detalle: mapea diasCobrados y subtotal")
    void getById_conDetalle() {
        Renta r = sampleRenta(1L, "ABIERTA");
        when(repo.findById(1L)).thenReturn(Optional.of(r));
        stubToResponse();
        RentaDetalle det = RentaDetalle.builder().rentaId(1L).productoId(1L)
                .cantidad(new BigDecimal("2.000")).costoDia(new BigDecimal("25.00"))
                .diasCobrados(new BigDecimal("3")).subtotal(new BigDecimal("75.00")).build();
        when(detalleRepo.findByRentaId(1L)).thenReturn(List.of(det));

        var resp = service.getById(1L);

        assertThat(resp.detalles()).hasSize(1);
        assertThat(resp.detalles().get(0).productoNombre()).isEqualTo("Rotomartillo");
        assertThat(resp.detalles().get(0).diasCobrados()).isEqualByComparingTo(new BigDecimal("3"));
        assertThat(resp.detalles().get(0).subtotal()).isEqualByComparingTo(new BigDecimal("75.00"));
    }

    @Test
    @DisplayName("getById sin cliente/almacen/producto: nombres null")
    void getById_nombresAusentes_null() {
        Renta r = sampleRenta(1L, "ABIERTA");
        when(repo.findById(1L)).thenReturn(Optional.of(r));
        when(clienteRepo.findById(1L)).thenReturn(Optional.empty());
        when(almacenRepo.findById(1)).thenReturn(Optional.empty());
        RentaDetalle det = RentaDetalle.builder().rentaId(1L).productoId(9L)
                .cantidad(BigDecimal.ONE).costoDia(BigDecimal.TEN)
                .diasCobrados(BigDecimal.ZERO).subtotal(BigDecimal.ZERO).build();
        when(detalleRepo.findByRentaId(1L)).thenReturn(List.of(det));
        when(productoRepo.findById(9L)).thenReturn(Optional.empty());

        var resp = service.getById(1L);

        assertThat(resp.clienteNombre()).isNull();
        assertThat(resp.almacenNombre()).isNull();
        assertThat(resp.detalles()).hasSize(1);
        assertThat(resp.detalles().get(0).productoNombre()).isNull();
    }
}
