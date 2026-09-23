package mx.ferreteria.api.ven.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import jakarta.persistence.EntityManager;
import mx.ferreteria.api.cat.entity.FormaPago;
import mx.ferreteria.api.cat.entity.Producto;
import mx.ferreteria.api.cat.repo.FormaPagoRepository;
import mx.ferreteria.api.cat.repo.ProductoRepository;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.fin.dto.FinDtos;
import mx.ferreteria.api.ven.dto.VenDtos;
import mx.ferreteria.api.ven.entity.DevolucionDetalle;
import mx.ferreteria.api.ven.entity.DevolucionVenta;
import mx.ferreteria.api.ven.entity.Venta;
import mx.ferreteria.api.ven.entity.VentaDetalle;
import mx.ferreteria.api.fin.service.CajaService;
import mx.ferreteria.api.ven.repo.DevolucionDetalleRepository;
import mx.ferreteria.api.ven.repo.DevolucionVentaRepository;
import mx.ferreteria.api.ven.repo.VentaDetalleRepository;
import mx.ferreteria.api.ven.repo.VentaRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DevolucionServiceTest {

    @Mock DevolucionVentaRepository repo;
    @Mock DevolucionDetalleRepository detalleRepo;
    @Mock VentaRepository ventaRepo;
    @Mock VentaDetalleRepository ventaDetalleRepo;
    @Mock ProductoRepository productoRepo;
    @Mock FormaPagoRepository formaPagoRepo;
    @Mock CajaService cajaService;
    @Mock EntityManager em;

    @InjectMocks
    DevolucionService service;

    // ── helpers ──────────────────────────────────────────────────────

    private DevolucionVenta sampleDevolucion(Long id) {
        return DevolucionVenta.builder()
                .devolucionId(id).ventaId(1L).motivo("Defectuoso")
                .total(BigDecimal.ZERO).formaDevolucionId(1).usuarioId(1)
                .fecha(Instant.now()).build();
    }

    private Venta sampleVenta(String estado) {
        return Venta.builder().ventaId(1L).folio("V-001").estado(estado)
                .almacenId(1).formaPagoId(1).subtotal(BigDecimal.ZERO)
                .iva(BigDecimal.ZERO).total(BigDecimal.ZERO).usuarioId(1)
                .ivaTasa(new BigDecimal("16.00")).ivaIncluido(true)
                .descuentoTotal(BigDecimal.ZERO).fecha(Instant.now()).build();
    }

    private void stubToResponse() {
        when(ventaRepo.findById(1L)).thenReturn(Optional.of(sampleVenta("COMPLETADA")));
        when(formaPagoRepo.findById(1))
                .thenReturn(Optional.of(FormaPago.builder().formaPagoId(1).nombre("EFECTIVO").build()));
        when(detalleRepo.findByDevolucionId(anyLong())).thenReturn(List.of());
        when(productoRepo.findById(anyLong()))
                .thenReturn(Optional.of(Producto.builder().productoId(1L).nombre("Clavo").build()));
    }

    private Pageable pg() {
        return PageRequest.of(0, 10);
    }

    // ── listByVenta ─────────────────────────────────────────────────

    @Test
    @DisplayName("listByVenta: retorna pagina de devoluciones de una venta")
    void listByVenta() {
        DevolucionVenta d = sampleDevolucion(1L);
        when(repo.findByVentaIdOrderByFechaDesc(1L, pg()))
                .thenReturn(new PageImpl<>(List.of(d), pg(), 1));
        stubToResponse();

        var result = service.listByVenta(1L, pg());

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).devolucionId()).isEqualTo(1L);
    }

    // ── getById ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getById encontrado: retorna DevolucionResponse con detalles")
    void getById_found() {
        DevolucionVenta d = sampleDevolucion(1L);
        when(repo.findById(1L)).thenReturn(Optional.of(d));
        stubToResponse();

        var resp = service.getById(1L);

        assertThat(resp.devolucionId()).isEqualTo(1L);
        assertThat(resp.motivo()).isEqualTo("Defectuoso");
        assertThat(resp.ventaFolio()).isEqualTo("V-001");
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
    @DisplayName("create ok: venta activa, guarda devolucion y detalles")
    void create_ok() {
        when(ventaRepo.findById(1L)).thenReturn(Optional.of(sampleVenta("COMPLETADA")));
        when(ventaDetalleRepo.findByVentaId(1L)).thenReturn(List.of(lineaVendida(1L, "1.000")));
        when(repo.findByVentaId(1L)).thenReturn(List.of());
        DevolucionVenta saved = sampleDevolucion(10L);
        when(repo.save(any(DevolucionVenta.class))).thenReturn(saved);
        when(repo.findById(10L)).thenReturn(Optional.of(saved));
        stubToResponse();

        VenDtos.DevolucionRequest req = new VenDtos.DevolucionRequest(
                1L, "Producto defectuoso", 1,
                List.of(new VenDtos.DevolucionDetalleRequest(1L, 1L, new BigDecimal("1.000"), new BigDecimal("50.00"))));

        var resp = service.create(req);

        assertThat(resp.devolucionId()).isEqualTo(10L);
        verify(repo).save(any(DevolucionVenta.class));
        verify(detalleRepo).save(any(DevolucionDetalle.class));
    }

    @Test
    @DisplayName("create venta no encontrada: lanza RecursoNoEncontradoException")
    void create_ventaNotFound() {
        when(ventaRepo.findById(999L)).thenReturn(Optional.empty());

        VenDtos.DevolucionRequest req = new VenDtos.DevolucionRequest(
                999L, "Motivo", 1,
                List.of(new VenDtos.DevolucionDetalleRequest(1L, null, new BigDecimal("1.000"), new BigDecimal("50.00"))));

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("create venta cancelada: lanza ReglaNegocioException")
    void create_ventaCancelled() {
        when(ventaRepo.findById(1L)).thenReturn(Optional.of(sampleVenta("CANCELADA")));

        VenDtos.DevolucionRequest req = new VenDtos.DevolucionRequest(
                1L, "Motivo", 1,
                List.of(new VenDtos.DevolucionDetalleRequest(1L, null, new BigDecimal("1.000"), new BigDecimal("50.00"))));

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(ReglaNegocioException.class);
    }

    private VentaDetalle lineaVendida(Long detalleId, String cantidad) {
        return VentaDetalle.builder().ventaDetalleId(detalleId).ventaId(1L)
                .productoId(1L).cantidad(new BigDecimal(cantidad))
                .precioUnitario(new BigDecimal("50.00")).build();
    }

    private VenDtos.DevolucionRequest devRequest(String cantidad) {
        return new VenDtos.DevolucionRequest(
                1L, "Defecto", 1,
                List.of(new VenDtos.DevolucionDetalleRequest(1L, 1L,
                        new BigDecimal(cantidad), new BigDecimal("50.00"))));
    }

    private void stubVentaConLinea(String vendido) {
        stubToResponse();
        Venta v = sampleVenta("COMPLETADA");
        v.setTurnoCajaId(17L);
        when(ventaRepo.findById(1L)).thenReturn(Optional.of(v));
        when(ventaDetalleRepo.findByVentaId(1L)).thenReturn(List.of(lineaVendida(1L, vendido)));
        when(repo.findByVentaId(1L)).thenReturn(List.of());
        DevolucionVenta saved = sampleDevolucion(10L);
        when(repo.save(any(DevolucionVenta.class))).thenReturn(saved);
        when(repo.findById(10L)).thenReturn(Optional.of(saved));
    }

    @Test
    @DisplayName("create total: línea cubierta al 100% -> venta DEVUELTA_TOTAL")
    void create_total_marcaDevueltaTotal() {
        stubVentaConLinea("1.000");

        service.create(devRequest("1.000"));

        var captor = org.mockito.ArgumentCaptor.forClass(Venta.class);
        verify(ventaRepo).save(captor.capture());
        assertThat(captor.getValue().getEstado()).isEqualTo("DEVUELTA_TOTAL");
    }

    @Test
    @DisplayName("create parcial: línea cubierta al 50% -> venta DEVUELTA_PARCIAL + salida en caja")
    void create_parcial_marcaParcialYRegistraCaja() {
        stubVentaConLinea("1.000");
        when(cajaService.turnoAbierto(17L)).thenReturn(true);
        // em.refresh es no-op en mock: el save ya trae el total del trigger.
        DevolucionVenta conTotal = sampleDevolucion(10L);
        conTotal.setTotal(new BigDecimal("25.00"));
        when(repo.save(any(DevolucionVenta.class))).thenReturn(conTotal);

        service.create(devRequest("0.500"));

        var captor = org.mockito.ArgumentCaptor.forClass(Venta.class);
        verify(ventaRepo).save(captor.capture());
        assertThat(captor.getValue().getEstado()).isEqualTo("DEVUELTA_PARCIAL");
        verify(cajaService).registrarMovimiento(eq(17L), any());
    }

    @Test
    @DisplayName("create exceso: cantidad mayor al remanente -> ReglaNegocioException sin guardar")
    void create_exceso_rechaza() {
        stubVentaConLinea("1.000");

        assertThatThrownBy(() -> service.create(devRequest("1.500")))
                .isInstanceOf(ReglaNegocioException.class);
        verify(detalleRepo, never()).save(any(DevolucionDetalle.class));
    }

    @Test
    @DisplayName("create línea ajena: ventaDetalleId de otra venta -> ReglaNegocioException")
    void create_lineaAjena_rechaza() {
        stubVentaConLinea("1.000");
        VenDtos.DevolucionRequest req = new VenDtos.DevolucionRequest(
                1L, "Defecto", 1,
                List.of(new VenDtos.DevolucionDetalleRequest(1L, 999L,
                        new BigDecimal("1.000"), new BigDecimal("50.00"))));

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(ReglaNegocioException.class);
    }

    // ── helpers nuevos ────────────────────────────────────────────

    private DevolucionDetalle detDevolucion(Long devId, Long prodId, Long vdId, String cant, String importe) {
        return DevolucionDetalle.builder().devolucionId(devId).productoId(prodId).ventaDetalleId(vdId)
                .cantidad(new BigDecimal(cant)).precioUnitario(new BigDecimal("50.00"))
                .importeLinea(new BigDecimal(importe)).build();
    }

    private void stubCreateBase(String vendido, BigDecimal totalGuardado) {
        stubToResponse();
        Venta v = sampleVenta("COMPLETADA");
        v.setTurnoCajaId(17L);
        when(ventaRepo.findById(1L)).thenReturn(Optional.of(v));
        when(ventaDetalleRepo.findByVentaId(1L)).thenReturn(List.of(lineaVendida(1L, vendido)));
        when(repo.findByVentaId(1L)).thenReturn(List.of());
        DevolucionVenta saved = sampleDevolucion(10L);
        saved.setTotal(totalGuardado);
        when(repo.save(any(DevolucionVenta.class))).thenReturn(saved);
    }

    private void stubPrevias(DevolucionDetalle... previas) {
        when(repo.findByVentaId(1L)).thenReturn(List.of(sampleDevolucion(5L)));
        when(detalleRepo.findByDevolucionIdIn(any())).thenReturn(List.of(previas));
    }

    // ── listByVenta: página vacía y ruta batch ────────────────────

    @Test
    @DisplayName("listByVenta vacía: retorna página vacía sin batch de detalles")
    void listByVenta_vacia() {
        when(repo.findByVentaIdOrderByFechaDesc(1L, pg()))
                .thenReturn(new PageImpl<>(List.of(), pg(), 0));

        var result = service.listByVenta(1L, pg());

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        verify(detalleRepo, never()).findByDevolucionIdIn(any());
    }

    @Test
    @DisplayName("listByVenta múltiple: enriquece folio/forma/productos e importes")
    void listByVenta_multiples_conEnriquecimiento() {
        DevolucionVenta d1 = sampleDevolucion(1L);
        d1.setFolio("DEV-001");
        DevolucionVenta d2 = sampleDevolucion(2L);
        d2.setFolio("DEV-002");
        when(repo.findByVentaIdOrderByFechaDesc(1L, pg()))
                .thenReturn(new PageImpl<>(List.of(d1, d2), pg(), 2));
        when(detalleRepo.findByDevolucionIdIn(any())).thenReturn(List.of(
                detDevolucion(1L, 1L, 1L, "1.000", "50.00"),
                detDevolucion(2L, 2L, 2L, "2.000", "100.00")));
        when(ventaRepo.findAllById(any())).thenReturn(List.of(sampleVenta("COMPLETADA")));
        when(formaPagoRepo.findAllById(any())).thenReturn(List.of(
                FormaPago.builder().formaPagoId(1).nombre("EFECTIVO").build()));
        when(productoRepo.findAllById(any())).thenReturn(List.of(
                Producto.builder().productoId(1L).nombre("Clavo").build(),
                Producto.builder().productoId(2L).nombre("Martillo").build()));

        var result = service.listByVenta(1L, pg());

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(2);
        var r1 = result.getContent().get(0);
        assertThat(r1.folio()).isEqualTo("DEV-001");
        assertThat(r1.ventaFolio()).isEqualTo("V-001");
        assertThat(r1.formaDevolucionNombre()).isEqualTo("EFECTIVO");
        assertThat(r1.detalles()).hasSize(1);
        assertThat(r1.detalles().get(0).productoNombre()).isEqualTo("Clavo");
        assertThat(r1.detalles().get(0).importeLinea()).isEqualByComparingTo("50.00");
        assertThat(result.getContent().get(1).detalles().get(0).productoNombre())
                .isEqualTo("Martillo");
    }

    @Test
    @DisplayName("listByVenta múltiple sin detalles: detalles vacíos y sin lookup de productos")
    void listByVenta_multiples_sinDetalles() {
        when(repo.findByVentaIdOrderByFechaDesc(1L, pg()))
                .thenReturn(new PageImpl<>(List.of(sampleDevolucion(1L), sampleDevolucion(2L)), pg(), 2));
        when(detalleRepo.findByDevolucionIdIn(any())).thenReturn(List.of());
        when(ventaRepo.findAllById(any())).thenReturn(List.of(sampleVenta("COMPLETADA")));
        when(formaPagoRepo.findAllById(any())).thenReturn(List.of(
                FormaPago.builder().formaPagoId(1).nombre("EFECTIVO").build()));

        var result = service.listByVenta(1L, pg());

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).detalles()).isEmpty();
        assertThat(result.getContent().get(1).detalles()).isEmpty();
        verify(productoRepo, never()).findAllById(any());
    }

    @Test
    @DisplayName("listByVenta múltiple con referencias faltantes: nombres/folio en null")
    void listByVenta_multiples_referenciasFaltantes() {
        DevolucionVenta d1 = sampleDevolucion(1L);
        d1.setFormaDevolucionId(99);
        DevolucionVenta d2 = sampleDevolucion(2L);
        when(repo.findByVentaIdOrderByFechaDesc(1L, pg()))
                .thenReturn(new PageImpl<>(List.of(d1, d2), pg(), 2));
        when(detalleRepo.findByDevolucionIdIn(any())).thenReturn(List.of(
                detDevolucion(1L, 77L, 1L, "1.000", "50.00")));
        when(ventaRepo.findAllById(any())).thenReturn(List.of());
        when(formaPagoRepo.findAllById(any())).thenReturn(List.of());
        when(productoRepo.findAllById(any())).thenReturn(List.of());

        var result = service.listByVenta(1L, pg());

        assertThat(result.getContent()).hasSize(2);
        var r1 = result.getContent().get(0);
        assertThat(r1.ventaFolio()).isNull();
        assertThat(r1.formaDevolucionNombre()).isNull();
        assertThat(r1.detalles()).hasSize(1);
        assertThat(r1.detalles().get(0).productoNombre()).isNull();
        assertThat(result.getContent().get(1).detalles()).isEmpty();
    }

    // ── getById: ramas nulas de toResponse ────────────────────────

    @Test
    @DisplayName("getById con referencias faltantes: folio/forma/producto en null, detalle mapeado")
    void getById_referenciasFaltantes() {
        DevolucionVenta d = sampleDevolucion(1L);
        d.setFormaDevolucionId(99);
        when(repo.findById(1L)).thenReturn(Optional.of(d));
        when(ventaRepo.findById(1L)).thenReturn(Optional.empty());
        when(formaPagoRepo.findById(99)).thenReturn(Optional.empty());
        when(detalleRepo.findByDevolucionId(1L)).thenReturn(List.of(
                detDevolucion(1L, 77L, 1L, "0.500", "25.00")));
        when(productoRepo.findById(77L)).thenReturn(Optional.empty());

        var resp = service.getById(1L);

        assertThat(resp.ventaFolio()).isNull();
        assertThat(resp.formaDevolucionNombre()).isNull();
        assertThat(resp.detalles()).hasSize(1);
        assertThat(resp.detalles().get(0).productoNombre()).isNull();
        assertThat(resp.detalles().get(0).cantidad()).isEqualByComparingTo("0.500");
        assertThat(resp.detalles().get(0).importeLinea()).isEqualByComparingTo("25.00");
    }

    // ── create: más ramas de error ───────────────────────────────

    @Test
    @DisplayName("create venta devuelta total: lanza ReglaNegocioException VALOR_INVALIDO")
    void create_ventaDevueltaTotal() {
        when(ventaRepo.findById(1L)).thenReturn(Optional.of(sampleVenta("DEVUELTA_TOTAL")));

        assertThatThrownBy(() -> service.create(devRequest("1.000")))
                .isInstanceOf(ReglaNegocioException.class)
                .satisfies(e -> assertThat(((ReglaNegocioException) e).errorCode())
                        .isEqualTo(ErrorCode.VALOR_INVALIDO));
        verify(repo, never()).save(any(DevolucionVenta.class));
    }

    @Test
    @DisplayName("create venta sin líneas: lanza ReglaNegocioException VALOR_INVALIDO")
    void create_ventaSinLineas() {
        when(ventaRepo.findById(1L)).thenReturn(Optional.of(sampleVenta("COMPLETADA")));
        when(ventaDetalleRepo.findByVentaId(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(devRequest("1.000")))
                .isInstanceOf(ReglaNegocioException.class)
                .satisfies(e -> assertThat(((ReglaNegocioException) e).errorCode())
                        .isEqualTo(ErrorCode.VALOR_INVALIDO));
        verify(repo, never()).save(any(DevolucionVenta.class));
    }

    @Test
    @DisplayName("create detalle sin ventaDetalleId: lanza ReglaNegocioException VALOR_INVALIDO")
    void create_lineaNula_rechaza() {
        stubVentaConLinea("1.000");
        VenDtos.DevolucionRequest req = new VenDtos.DevolucionRequest(
                1L, "Defecto", 1,
                List.of(new VenDtos.DevolucionDetalleRequest(1L, null,
                        new BigDecimal("1.000"), new BigDecimal("50.00"))));

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(ReglaNegocioException.class)
                .satisfies(e -> assertThat(((ReglaNegocioException) e).errorCode())
                        .isEqualTo(ErrorCode.VALOR_INVALIDO));
        verify(detalleRepo, never()).save(any(DevolucionDetalle.class));
    }

    @Test
    @DisplayName("create con devolución previa: remanente acumulado permite el resto -> DEVUELTA_TOTAL")
    void create_remanenteConPrevia_ok() {
        stubCreateBase("1.000", BigDecimal.ZERO);
        stubPrevias(detDevolucion(5L, 1L, 1L, "0.400", "20.00"));

        service.create(devRequest("0.600"));

        var captor = org.mockito.ArgumentCaptor.forClass(Venta.class);
        verify(ventaRepo).save(captor.capture());
        assertThat(captor.getValue().getEstado()).isEqualTo("DEVUELTA_TOTAL");
    }

    @Test
    @DisplayName("create excede remanente tras previa: VALOR_INVALIDO sin guardar")
    void create_remanenteExcedidoTrasPrevia_rechaza() {
        stubCreateBase("1.000", BigDecimal.ZERO);
        stubPrevias(detDevolucion(5L, 1L, 1L, "0.400", "20.00"));

        assertThatThrownBy(() -> service.create(devRequest("0.700")))
                .isInstanceOf(ReglaNegocioException.class)
                .satisfies(e -> assertThat(((ReglaNegocioException) e).errorCode())
                        .isEqualTo(ErrorCode.VALOR_INVALIDO));
        verify(detalleRepo, never()).save(any(DevolucionDetalle.class));
    }

    @Test
    @DisplayName("create previa con detalle sin línea: se ignora y el resto cuadra -> DEVUELTA_TOTAL")
    void create_previaSinLinea_seIgnora() {
        stubCreateBase("1.000", BigDecimal.ZERO);
        stubPrevias(
                detDevolucion(5L, 1L, null, "0.900", "45.00"),
                detDevolucion(5L, 1L, 1L, "0.200", "10.00"));

        service.create(devRequest("0.800"));

        var captor = org.mockito.ArgumentCaptor.forClass(Venta.class);
        verify(ventaRepo).save(captor.capture());
        assertThat(captor.getValue().getEstado()).isEqualTo("DEVUELTA_TOTAL");
    }

    @Test
    @DisplayName("create multilínea mixta: una completa y otra parcial -> DEVUELTA_PARCIAL")
    void create_multiLinea_mezcla_parcial() {
        stubToResponse();
        Venta v = sampleVenta("COMPLETADA");
        v.setTurnoCajaId(17L);
        when(ventaRepo.findById(1L)).thenReturn(Optional.of(v));
        when(ventaDetalleRepo.findByVentaId(1L)).thenReturn(
                List.of(lineaVendida(1L, "1.000"), lineaVendida(2L, "2.000")));
        when(repo.findByVentaId(1L)).thenReturn(List.of());
        when(repo.save(any(DevolucionVenta.class))).thenReturn(sampleDevolucion(10L));
        VenDtos.DevolucionRequest req = new VenDtos.DevolucionRequest(
                1L, "Defecto", 1, List.of(
                        new VenDtos.DevolucionDetalleRequest(1L, 1L,
                                new BigDecimal("1.000"), new BigDecimal("50.00")),
                        new VenDtos.DevolucionDetalleRequest(2L, 2L,
                                new BigDecimal("1.000"), new BigDecimal("30.00"))));

        service.create(req);

        var captor = org.mockito.ArgumentCaptor.forClass(Venta.class);
        verify(ventaRepo).save(captor.capture());
        assertThat(captor.getValue().getEstado()).isEqualTo("DEVUELTA_PARCIAL");
        verify(detalleRepo, times(2)).save(any(DevolucionDetalle.class));
    }

    // ── create: ramas de reembolso en caja ───────────────────────

    @Test
    @DisplayName("create turno cerrado: sin movimiento en caja y sin turno ligado")
    void create_turnoCerrado_sinMovimiento() {
        stubCreateBase("1.000", new BigDecimal("25.00"));
        when(cajaService.turnoAbierto(17L)).thenReturn(false);
        DevolucionVenta conTotal = sampleDevolucion(10L);
        conTotal.setTotal(new BigDecimal("25.00"));
        when(repo.save(any(DevolucionVenta.class))).thenReturn(conTotal);

        service.create(devRequest("0.500"));

        verify(cajaService, never()).registrarMovimiento(any(), any());
        assertThat(conTotal.getTurnoCajaId()).isNull();
    }

    @Test
    @DisplayName("create total cero con turno abierto: sin movimiento en caja")
    void create_totalCero_sinMovimiento() {
        stubCreateBase("1.000", BigDecimal.ZERO);
        when(cajaService.turnoAbierto(17L)).thenReturn(true);

        service.create(devRequest("0.500"));

        verify(cajaService, never()).registrarMovimiento(any(), any());
        verify(cajaService, never()).turnoAbierto(anyLong());
    }

    @Test
    @DisplayName("create total nulo: sin movimiento en caja y respuesta con total null")
    void create_totalNulo_sinMovimiento() {
        stubCreateBase("1.000", null);

        var resp = service.create(devRequest("0.500"));

        assertThat(resp.total()).isNull();
        verify(cajaService, never()).registrarMovimiento(any(), any());
    }

    @Test
    @DisplayName("create reembolso: liga turno y registra SALIDA DEVOLUCION_CLIENTE")
    void create_reembolso_seteaTurnoYConcepto() {
        stubCreateBase("1.000", new BigDecimal("25.00"));
        when(cajaService.turnoAbierto(17L)).thenReturn(true);
        DevolucionVenta conTotal = sampleDevolucion(10L);
        conTotal.setTotal(new BigDecimal("25.00"));
        when(repo.save(any(DevolucionVenta.class))).thenReturn(conTotal);

        service.create(devRequest("0.500"));

        assertThat(conTotal.getTurnoCajaId()).isEqualTo(17L);
        var captor = org.mockito.ArgumentCaptor.forClass(FinDtos.MovimientoCajaRequest.class);
        verify(cajaService).registrarMovimiento(eq(17L), captor.capture());
        assertThat(captor.getValue().tipo()).isEqualTo("SALIDA");
        assertThat(captor.getValue().concepto()).isEqualTo("DEVOLUCION_CLIENTE");
        assertThat(captor.getValue().monto()).isEqualByComparingTo("25.00");
        assertThat(captor.getValue().refTabla()).isEqualTo("ven.devoluciones_venta");
        assertThat(captor.getValue().refId()).isEqualTo(10L);
    }
}
