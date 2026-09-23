package mx.ferreteria.api.ven.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import mx.ferreteria.api.cat.entity.Cliente;
import mx.ferreteria.api.cat.entity.Producto;
import mx.ferreteria.api.cat.repo.ClienteRepository;
import mx.ferreteria.api.cat.repo.ProductoRepository;
import mx.ferreteria.api.common.error.ApiException;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.ven.dto.VenDtos;
import mx.ferreteria.api.ven.entity.Cotizacion;
import mx.ferreteria.api.ven.entity.CotizacionDetalle;
import mx.ferreteria.api.ven.repo.CotizacionDetalleRepository;
import mx.ferreteria.api.ven.repo.CotizacionRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CotizacionServiceTest {

    @Mock
    CotizacionRepository repo;
    @Mock
    CotizacionDetalleRepository detalleRepo;
    @Mock
    ClienteRepository clienteRepo;
    @Mock
    ProductoRepository productoRepo;
    @Mock
    VentaService ventaService;

    @InjectMocks
    CotizacionService service;

    private VenDtos.VentaResponse sampleVentaResponse(Long ventaId) {
        return new VenDtos.VentaResponse(
                ventaId, "V-0010", null, null, null,
                1, "Almacén Principal", Instant.now(), java.time.LocalDate.now(),
                1, "Efectivo", new BigDecimal("16.00"), true,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "COMPLETADA", 1, null, null, null, null,
                List.of(), List.of());
    }

    // ── helpers ──────────────────────────────────────────────────────

    private Cotizacion sampleCotizacion(Long id, String folio, String estado) {
        return Cotizacion.builder()
                .cotizacionId(id).folio(folio).fecha(Instant.now())
                .subtotal(BigDecimal.ZERO).iva(BigDecimal.ZERO).total(BigDecimal.ZERO)
                .estado(estado).usuarioId(1)
                .build();
    }

    private CotizacionDetalle sampleDetalle(Long cotizacionId, Long productoId) {
        return CotizacionDetalle.builder()
                .cotizacionId(cotizacionId).productoId(productoId)
                .cantidad(new BigDecimal("3.000")).precioUnitario(new BigDecimal("25.00"))
                .build();
    }

    private void stubToResponse() {
        when(clienteRepo.findById(anyLong())).thenReturn(Optional.empty());
        when(detalleRepo.findByCotizacionId(anyLong())).thenReturn(List.of());
        when(productoRepo.findById(anyLong()))
                .thenReturn(Optional.of(Producto.builder().productoId(1L).nombre("Taladro").build()));
    }

    private Pageable pg() {
        return PageRequest.of(0, 10);
    }

    // ── list ────────────────────────────────────────────────────────

    @Test
    @DisplayName("list sin estado: filtrar sin filtros retorna pagina con items")
    void list_all() {
        Cotizacion c = sampleCotizacion(1L, "COT-001", "VIGENTE");
        when(repo.filtrar(null, null, null, pg()))
                .thenReturn(new PageImpl<>(List.of(c), pg(), 1));
        stubToResponse();

        var result = service.list(null, null, null, pg());

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).cotizacionId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("list por estado: filtrar retorna filtrado")
    void list_byEstado() {
        Cotizacion c = sampleCotizacion(1L, "COT-001", "VIGENTE");
        when(repo.filtrar("VIGENTE", null, null, pg()))
                .thenReturn(new PageImpl<>(List.of(c), pg(), 1));
        stubToResponse();

        var result = service.list("VIGENTE", null, null, pg());

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).estado()).isEqualTo("VIGENTE");
    }

    // ── getById ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getById encontrado: retorna CotizacionResponse con detalles")
    void getById_found() {
        Cotizacion c = sampleCotizacion(1L, "COT-001", "VIGENTE");
        when(repo.findById(1L)).thenReturn(Optional.of(c));
        when(detalleRepo.findByCotizacionId(1L))
                .thenReturn(List.of(sampleDetalle(1L, 1L)));
        when(productoRepo.findById(1L))
                .thenReturn(Optional.of(Producto.builder().productoId(1L).nombre("Taladro").build()));
        when(clienteRepo.findById(anyLong())).thenReturn(Optional.empty());

        var resp = service.getById(1L);

        assertThat(resp.cotizacionId()).isEqualTo(1L);
        assertThat(resp.folio()).isEqualTo("COT-001");
        assertThat(resp.detalles()).hasSize(1);
        assertThat(resp.detalles().get(0).productoNombre()).isEqualTo("Taladro");
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
    @DisplayName("create ok: guarda entidad y detalles, retorna respuesta")
    void create_ok() {
        Cotizacion saved = sampleCotizacion(5L, "COT-005", "VIGENTE");
        when(repo.save(any(Cotizacion.class))).thenReturn(saved);
        stubToResponse();

        VenDtos.CotizacionRequest req = new VenDtos.CotizacionRequest(
                null, null,
                List.of(new VenDtos.CotizacionDetalleRequest(1L, new BigDecimal("2.000"), new BigDecimal("30.00"))));

        var resp = service.create(req);

        assertThat(resp.cotizacionId()).isEqualTo(5L);
        verify(repo).save(any(Cotizacion.class));
        verify(detalleRepo).save(any(CotizacionDetalle.class));
    }

    // ── convertirAVenta ─────────────────────────────────────────────

    @Test
    @DisplayName("convertir ok: crea venta con caja/turno y marca cotizacion como CONVERTIDA con venta_generada_id")
    void convertir_ok() {
        Cotizacion c = sampleCotizacion(1L, "COT-001", "VIGENTE");
        when(repo.findById(1L)).thenReturn(Optional.of(c));
        when(ventaService.checkout(any(VenDtos.VentaRequest.class)))
                .thenReturn(sampleVentaResponse(10L));
        stubToResponse();

        var resp = service.convertirAVenta(1L, 1, 1, 2);

        ArgumentCaptor<VenDtos.VentaRequest> captor = ArgumentCaptor.forClass(VenDtos.VentaRequest.class);
        verify(ventaService).checkout(captor.capture());
        assertThat(captor.getValue().cajaId()).isEqualTo(2);
        assertThat(captor.getValue().cotizacionId()).isEqualTo(1L);
        assertThat(c.getEstado()).isEqualTo("CONVERTIDA");
        assertThat(c.getVentaGeneradaId()).isEqualTo(10L);
        verify(repo).save(c);
        assertThat(resp.estado()).isEqualTo("CONVERTIDA");
    }

    @Test
    @DisplayName("convertir con estado no VIGENTE: lanza ReglaNegocioException")
    void convertir_notVigente() {
        Cotizacion c = sampleCotizacion(1L, "COT-001", "CONVERTIDA");
        when(repo.findById(1L)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.convertirAVenta(1L, 1, 1, 2))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("convertir inexistente: lanza RecursoNoEncontradoException")
    void convertir_notFound() {
        when(repo.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.convertirAVenta(999L, 1, 1, 2))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .satisfies(e -> assertThat(((ApiException) e).errorCode())
                        .isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO));
    }

    // ── list: pagina vacia y ruta batch (>1 item) ────────────────────

    @Test
    @DisplayName("list vacia: retorna pagina sin contenido")
    void list_empty() {
        when(repo.filtrar(null, null, null, pg()))
                .thenReturn(new PageImpl<>(List.of(), pg(), 0));

        var result = service.list(null, null, null, pg());

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("list con varios: usa ruta batch, resuelve nombres y recalcula/conserva totales")
    void list_multi_batch() {
        Cotizacion c1 = sampleCotizacion(1L, "COT-001", "VIGENTE");
        c1.setClienteId(7L); // totales en cero + detalles -> recalcula
        Cotizacion c2 = sampleCotizacion(2L, "COT-002", "VIGENTE");
        c2.setSubtotal(new BigDecimal("100.00"));
        c2.setIva(new BigDecimal("16.00"));
        c2.setTotal(new BigDecimal("116.00")); // totales almacenados -> se conservan
        when(repo.filtrar(null, null, null, pg()))
                .thenReturn(new PageImpl<>(List.of(c1, c2), pg(), 2));
        when(clienteRepo.findAllById(List.of(7L))).thenReturn(List.of(
                Cliente.builder().clienteId(7L).razonSocial("ACME").build()));
        when(detalleRepo.findByCotizacionIdIn(List.of(1L, 2L)))
                .thenReturn(List.of(sampleDetalle(1L, 1L)));
        when(productoRepo.findAllById(List.of(1L))).thenReturn(List.of(
                Producto.builder().productoId(1L).nombre("Taladro").build()));

        var result = service.list(null, null, null, pg());

        assertThat(result.getContent()).hasSize(2);
        var r1 = result.getContent().get(0);
        assertThat(r1.clienteNombre()).isEqualTo("ACME");
        assertThat(r1.detalles()).hasSize(1);
        assertThat(r1.detalles().get(0).productoNombre()).isEqualTo("Taladro");
        // 3.000 x 25.00 = 75.00 -> subtotal 64.66, iva 10.34
        assertThat(r1.total()).isEqualByComparingTo(new BigDecimal("75.00"));
        assertThat(r1.subtotal()).isEqualByComparingTo(new BigDecimal("64.66"));
        assertThat(r1.iva()).isEqualByComparingTo(new BigDecimal("10.34"));
        var r2 = result.getContent().get(1);
        assertThat(r2.clienteNombre()).isNull();
        assertThat(r2.detalles()).isEmpty();
        assertThat(r2.subtotal()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(r2.total()).isEqualByComparingTo(new BigDecimal("116.00"));
    }

    @Test
    @DisplayName("toResponses vacia: retorna lista vacia sin tocar repos")
    void toResponses_empty() {
        assertThat(service.toResponses(List.of())).isEmpty();
    }

    // ── getById/toResponse: ramas de nombres y totales ───────────────

    @Test
    @DisplayName("getById con cliente: resuelve clienteNombre")
    void getById_clienteNombre() {
        Cotizacion c = sampleCotizacion(1L, "COT-001", "VIGENTE");
        c.setClienteId(7L);
        when(repo.findById(1L)).thenReturn(Optional.of(c));
        when(clienteRepo.findById(7L)).thenReturn(Optional.of(
                Cliente.builder().clienteId(7L).razonSocial("ACME").build()));
        when(detalleRepo.findByCotizacionId(1L)).thenReturn(List.of());

        var resp = service.getById(1L);

        assertThat(resp.clienteNombre()).isEqualTo("ACME");
    }

    @Test
    @DisplayName("getById con cliente inexistente: clienteNombre null")
    void getById_clienteAusente() {
        Cotizacion c = sampleCotizacion(1L, "COT-001", "VIGENTE");
        c.setClienteId(7L);
        when(repo.findById(1L)).thenReturn(Optional.of(c));
        when(clienteRepo.findById(7L)).thenReturn(Optional.empty());
        when(detalleRepo.findByCotizacionId(1L)).thenReturn(List.of());

        var resp = service.getById(1L);

        assertThat(resp.clienteId()).isEqualTo(7L);
        assertThat(resp.clienteNombre()).isNull();
    }

    @Test
    @DisplayName("getById con producto inexistente: productoNombre null")
    void getById_productoAusente() {
        Cotizacion c = sampleCotizacion(1L, "COT-001", "VIGENTE");
        when(repo.findById(1L)).thenReturn(Optional.of(c));
        when(detalleRepo.findByCotizacionId(1L))
                .thenReturn(List.of(sampleDetalle(1L, 9L)));
        when(productoRepo.findById(9L)).thenReturn(Optional.empty());

        var resp = service.getById(1L);

        assertThat(resp.detalles()).hasSize(1);
        assertThat(resp.detalles().get(0).productoNombre()).isNull();
    }

    @Test
    @DisplayName("getById con totales en cero y detalles: recalcula al vuelo")
    void getById_recalculaTotalesEnCero() {
        Cotizacion c = sampleCotizacion(1L, "COT-001", "VIGENTE");
        when(repo.findById(1L)).thenReturn(Optional.of(c));
        when(detalleRepo.findByCotizacionId(1L))
                .thenReturn(List.of(sampleDetalle(1L, 1L)));
        when(productoRepo.findById(1L)).thenReturn(Optional.of(
                Producto.builder().productoId(1L).nombre("Taladro").build()));

        var resp = service.getById(1L);

        assertThat(resp.total()).isEqualByComparingTo(new BigDecimal("75.00"));
        assertThat(resp.subtotal()).isEqualByComparingTo(new BigDecimal("64.66"));
        assertThat(resp.iva()).isEqualByComparingTo(new BigDecimal("10.34"));
    }

    @Test
    @DisplayName("getById con totales almacenados: los conserva aunque haya detalles")
    void getById_conservaTotalesAlmacenados() {
        Cotizacion c = sampleCotizacion(1L, "COT-001", "VIGENTE");
        c.setSubtotal(new BigDecimal("100.00"));
        c.setIva(new BigDecimal("16.00"));
        c.setTotal(new BigDecimal("116.00"));
        when(repo.findById(1L)).thenReturn(Optional.of(c));
        when(detalleRepo.findByCotizacionId(1L))
                .thenReturn(List.of(sampleDetalle(1L, 1L)));
        when(productoRepo.findById(1L)).thenReturn(Optional.of(
                Producto.builder().productoId(1L).nombre("Taladro").build()));

        var resp = service.getById(1L);

        assertThat(resp.subtotal()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(resp.iva()).isEqualByComparingTo(new BigDecimal("16.00"));
        assertThat(resp.total()).isEqualByComparingTo(new BigDecimal("116.00"));
    }

    // ── create: calculo de totales y persistencia de detalles ────────

    @Test
    @DisplayName("create: calcula subtotal/iva/total (precios con IVA incluido) y usuario SYSTEM")
    void create_calculaTotales() {
        Cotizacion saved = sampleCotizacion(5L, "COT-005", "VIGENTE");
        when(repo.save(any(Cotizacion.class))).thenReturn(saved);
        stubToResponse();
        LocalDate vigencia = LocalDate.of(2026, 12, 31);

        var resp = service.create(new VenDtos.CotizacionRequest(
                null, vigencia,
                List.of(new VenDtos.CotizacionDetalleRequest(1L, new BigDecimal("2.000"), new BigDecimal("30.00")))));

        ArgumentCaptor<Cotizacion> captor = ArgumentCaptor.forClass(Cotizacion.class);
        verify(repo).save(captor.capture());
        // 2.000 x 30.00 = 60.00 -> base 60/1.16 = 51.72, iva 8.28
        assertThat(captor.getValue().getSubtotal()).isEqualByComparingTo(new BigDecimal("51.72"));
        assertThat(captor.getValue().getIva()).isEqualByComparingTo(new BigDecimal("8.28"));
        assertThat(captor.getValue().getTotal()).isEqualByComparingTo(new BigDecimal("60.00"));
        assertThat(captor.getValue().getVigenciaHasta()).isEqualTo(vigencia);
        assertThat(captor.getValue().getUsuarioId()).isZero(); // UserPrincipal SYSTEM sin sesion
        assertThat(resp.cotizacionId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("create con varios detalles: todos heredan el cotizacionId generado")
    void create_multiplesDetalles() {
        Cotizacion saved = sampleCotizacion(5L, "COT-005", "VIGENTE");
        when(repo.save(any(Cotizacion.class))).thenReturn(saved);
        stubToResponse();

        service.create(new VenDtos.CotizacionRequest(
                null, null,
                List.of(
                        new VenDtos.CotizacionDetalleRequest(1L, new BigDecimal("3.000"), new BigDecimal("25.00")),
                        new VenDtos.CotizacionDetalleRequest(2L, new BigDecimal("2.000"), new BigDecimal("30.00")))));

        ArgumentCaptor<CotizacionDetalle> captor = ArgumentCaptor.forClass(CotizacionDetalle.class);
        verify(detalleRepo, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).hasSize(2);
        assertThat(captor.getAllValues())
                .allSatisfy(d -> assertThat(d.getCotizacionId()).isEqualTo(5L));
        assertThat(captor.getAllValues())
                .extracting(CotizacionDetalle::getProductoId)
                .containsExactlyInAnyOrder(1L, 2L);
    }

    // ── convertirAVenta: ramas y mapeo ───────────────────────────────

    @Test
    @DisplayName("convertir con estado null: lanza ReglaNegocioException REGISTRO_DUPLICADO")
    void convertir_estadoNull() {
        Cotizacion c = sampleCotizacion(1L, "COT-001", null);
        when(repo.findById(1L)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.convertirAVenta(1L, 1, 1, 2))
                .isInstanceOf(ReglaNegocioException.class)
                .satisfies(e -> assertThat(((ApiException) e).errorCode())
                        .isEqualTo(ErrorCode.REGISTRO_DUPLICADO));
    }

    @Test
    @DisplayName("convertir: mapea detalles, pago por el total y nota con folio")
    void convertir_mapeaDetallesPagosNota() {
        Cotizacion c = sampleCotizacion(1L, "COT-001", "VIGENTE");
        c.setClienteId(7L);
        c.setSubtotal(new BigDecimal("64.66"));
        c.setIva(new BigDecimal("10.34"));
        c.setTotal(new BigDecimal("75.00"));
        when(repo.findById(1L)).thenReturn(Optional.of(c));
        when(detalleRepo.findByCotizacionId(1L))
                .thenReturn(List.of(sampleDetalle(1L, 1L)));
        when(ventaService.checkout(any(VenDtos.VentaRequest.class)))
                .thenReturn(sampleVentaResponse(10L));
        stubToResponse();

        service.convertirAVenta(1L, 1, 1, 2);

        ArgumentCaptor<VenDtos.VentaRequest> captor = ArgumentCaptor.forClass(VenDtos.VentaRequest.class);
        verify(ventaService).checkout(captor.capture());
        var req = captor.getValue();
        assertThat(req.almacenId()).isEqualTo(1);
        assertThat(req.cajaId()).isEqualTo(2);
        assertThat(req.clienteId()).isEqualTo(7L);
        assertThat(req.cotizacionId()).isEqualTo(1L);
        assertThat(req.formaPagoId()).isEqualTo(1);
        assertThat(req.detalles()).hasSize(1);
        assertThat(req.detalles().get(0).productoId()).isEqualTo(1L);
        assertThat(req.detalles().get(0).cantidad()).isEqualByComparingTo(new BigDecimal("3.000"));
        assertThat(req.detalles().get(0).precioUnitario()).isEqualByComparingTo(new BigDecimal("25.00"));
        assertThat(req.pagos()).hasSize(1);
        assertThat(req.pagos().get(0).monto()).isEqualByComparingTo(new BigDecimal("75.00"));
        assertThat(req.notas()).contains("COT-001");
    }
}
