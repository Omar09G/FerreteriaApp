package mx.ferreteria.api.com.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
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
import org.springframework.data.domain.Sort;

import mx.ferreteria.api.cat.entity.FormaPago;
import mx.ferreteria.api.cat.entity.Producto;
import mx.ferreteria.api.cat.entity.Proveedor;
import mx.ferreteria.api.cat.repo.FormaPagoRepository;
import mx.ferreteria.api.cat.repo.ProductoRepository;
import mx.ferreteria.api.cat.repo.ProveedorRepository;
import mx.ferreteria.api.com.dto.ComDtos.CompraDetalleRequest;
import mx.ferreteria.api.com.dto.ComDtos.CompraRequest;
import mx.ferreteria.api.com.entity.Compra;
import mx.ferreteria.api.com.entity.CompraDetalle;
import mx.ferreteria.api.com.repo.CompraDetalleRepository;
import mx.ferreteria.api.com.repo.CompraReportRepository;
import mx.ferreteria.api.com.repo.CompraRepository;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.fin.service.CajaService;
import mx.ferreteria.api.inv.entity.Almacen;
import mx.ferreteria.api.inv.repo.AlmacenRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CompraServiceTest {

    @Mock CompraRepository compraRepo;
    @Mock CompraDetalleRepository detalleRepo;
    @Mock ProveedorRepository proveedorRepo;
    @Mock AlmacenRepository almacenRepo;
    @Mock FormaPagoRepository formaPagoRepo;
    @Mock ProductoRepository productoRepo;
    @Mock CompraReportRepository reportRepo;
    @Mock CajaService cajaService;
    @Mock org.springframework.context.ApplicationEventPublisher events;
    @Mock jakarta.persistence.EntityManager em;

    @InjectMocks
    CompraService service;

    @org.junit.jupiter.api.BeforeEach
    void inyectarEntityManager() {
        // @PersistenceContext no lo resuelve @InjectMocks: se setea explícito.
        org.springframework.test.util.ReflectionTestUtils.setField(service, "em", em);
    }

    /** Simula el INSERT...RETURNING de abonar devolviendo el id indicado. */
    private jakarta.persistence.Query stubInsertPagoRetornando(long id) {
        var q = mock(jakarta.persistence.Query.class);
        doReturn(q).when(em).createNativeQuery(any(String.class));
        doReturn(q).when(q).setParameter(any(String.class), any());
        doReturn(id).when(q).getSingleResult();
        return q;
    }

    private Compra sampleCompra(Long id) {
        return Compra.builder().compraId(id).folio("COMPRA-0001")
                .proveedorId(1).almacenId(1).formaPagoId(1)
                .fecha(Instant.parse("2026-01-15T10:00:00Z"))
                .subtotal(new BigDecimal("1000.00")).iva(new BigDecimal("160.00"))
                .total(new BigDecimal("1160.00")).estado("RECIBIDA")
                .usuarioId(1).build();
    }

    private CompraDetalle sampleDetalle(Long id) {
        return CompraDetalle.builder().compraDetalleId(id)
                .compraId(1L).productoId(10L)
                .cantidad(new BigDecimal("10.000"))
                .costoUnitario(new BigDecimal("100.00"))
                .importeLinea(new BigDecimal("1000.00")).build();
    }

    private void stubNombres() {
        doReturn(Optional.of(Proveedor.builder().proveedorId(1).razonSocial("Ferritas SA").build()))
                .when(proveedorRepo).findById(1);
        doReturn(Optional.of(Almacen.builder().almacenId(1).nombre("Bodega Central").build()))
                .when(almacenRepo).findById(1);
        doReturn(Optional.of(FormaPago.builder().formaPagoId(1).nombre("Contado").build()))
                .when(formaPagoRepo).findById(1);
        doReturn(Optional.of(Producto.builder().productoId(10L).nombre("Taladro").build()))
                .when(productoRepo).findById(10L);
    }

    @Test
    @DisplayName("list: filtra por almacen y enriquece nombres")
    void list_byAlmacen() {
        Pageable pg = PageRequest.of(0, 20);
        Compra c = sampleCompra(1L);
        doReturn(new PageImpl<>(List.of(c), pg, 1))
                .when(compraRepo).findByAlmacenIdOrderByFechaDesc(eq(1), org.mockito.ArgumentMatchers.any(Pageable.class));
        stubNombres();
        doReturn(List.of(sampleDetalle(1L)))
                .when(detalleRepo).findByCompraIdOrderByCompraDetalleId(1L);

        var result = service.list(1, null, null, null, pg);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).proveedor()).isEqualTo("Ferritas SA");
        assertThat(result.getContent().get(0).almacen()).isEqualTo("Bodega Central");
        assertThat(result.getContent().get(0).detalles()).hasSize(1);
        assertThat(result.getContent().get(0).detalles().get(0).producto()).isEqualTo("Taladro");
    }

    @Test
    @DisplayName("getById: compra inexistente -> RecursoNoEncontradoException")
    void getById_notFound() {
        doReturn(Optional.empty()).when(compraRepo).findById(999L);

        assertThatThrownBy(() -> service.getById(999L))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("create: guarda cabecera + detalles y re-lee folio asignado por BD")
    void create_ok() {
        Compra saved = sampleCompra(50L);
        saved.setTurnoCajaId(6L);
        doReturn(Optional.of(Proveedor.builder().proveedorId(1).razonSocial("Ferritas SA").build()))
                .when(proveedorRepo).findById(1);
        doReturn(Optional.of(Almacen.builder().almacenId(1).nombre("Bodega Central").build()))
                .when(almacenRepo).findById(1);
        doReturn(Optional.of(FormaPago.builder().formaPagoId(1).nombre("Contado").clave("EFECTIVO").build()))
                .when(formaPagoRepo).findById(1);
        doReturn(6L).when(cajaService).resolverTurnoAbierto(5, 1);
        doReturn(saved).when(compraRepo).save(any(Compra.class));
        doReturn(Optional.of(saved)).when(compraRepo).findById(50L);
        stubNombres();
        doReturn(List.of(sampleDetalle(1L)))
                .when(detalleRepo).findByCompraIdOrderByCompraDetalleId(50L);

        CompraRequest req = new CompraRequest(
                1, 1, 1, "F-0001", null, 5, "Primera compra",
                List.of(new CompraDetalleRequest(10L, new BigDecimal("10.000"),
                        new BigDecimal("100.00"))));

        var resp = service.create(req);

        assertThat(resp.compraId()).isEqualTo(50L);
        assertThat(resp.folio()).isEqualTo("COMPRA-0001");
        assertThat(resp.estado()).isEqualTo("RECIBIDA");
        assertThat(resp.turnoCajaId()).isEqualTo(6L);
        verify(cajaService).resolverTurnoAbierto(5, 1);
        verify(detalleRepo).save(any(CompraDetalle.class));
        verify(compraRepo).flush();
    }

    @Test
    @DisplayName("create credito: no exige caja ni resuelve turno")
    void create_creditoSinTurno() {
        Compra saved = sampleCompra(51L);
        doReturn(Optional.of(Proveedor.builder().proveedorId(1).razonSocial("Ferritas SA").build()))
                .when(proveedorRepo).findById(1);
        doReturn(Optional.of(Almacen.builder().almacenId(1).nombre("Bodega Central").build()))
                .when(almacenRepo).findById(1);
        doReturn(Optional.of(FormaPago.builder().formaPagoId(6).nombre("Crédito").clave("CREDITO").build()))
                .when(formaPagoRepo).findById(6);
        doReturn(saved).when(compraRepo).save(any(Compra.class));
        doReturn(Optional.of(saved)).when(compraRepo).findById(51L);
        doReturn(Optional.of(Producto.builder().productoId(10L).nombre("Taladro").build()))
                .when(productoRepo).findById(10L);
        doReturn(List.of(sampleDetalle(1L)))
                .when(detalleRepo).findByCompraIdOrderByCompraDetalleId(51L);

        CompraRequest req = new CompraRequest(
                1, 1, 6, "F-0002", null, null, null,
                List.of(new CompraDetalleRequest(10L, new BigDecimal("10.000"),
                        new BigDecimal("100.00"))));

        var resp = service.create(req);

        assertThat(resp.turnoCajaId()).isNull();
        org.mockito.Mockito.verifyNoInteractions(cajaService);
        verify(detalleRepo).save(any(CompraDetalle.class));
    }

    @Test
    @DisplayName("create contado sin caja: lanza CAMPO_REQUERIDO")
    void create_contadoSinCaja() {
        doReturn(Optional.of(Proveedor.builder().proveedorId(1).razonSocial("Ferritas SA").build()))
                .when(proveedorRepo).findById(1);
        doReturn(Optional.of(Almacen.builder().almacenId(1).nombre("Bodega Central").build()))
                .when(almacenRepo).findById(1);
        doReturn(Optional.of(FormaPago.builder().formaPagoId(1).nombre("Contado").clave("EFECTIVO").build()))
                .when(formaPagoRepo).findById(1);

        CompraRequest req = new CompraRequest(
                1, 1, 1, "F-0003", null, null, null,
                List.of(new CompraDetalleRequest(10L, new BigDecimal("1.000"),
                        new BigDecimal("10.00"))));

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(ReglaNegocioException.class);

        ReglaNegocioException ex = org.assertj.core.api.Assertions
                .catchThrowableOfType(() -> service.create(req), ReglaNegocioException.class);
        assertThat(ex.errorCode()).isEqualTo(ErrorCode.CAMPO_REQUERIDO);
    }

    @Test
    @DisplayName("create contado sin turno abierto: propaga la excepcion de caja")
    void create_sinTurnoAbierto() {
        doReturn(Optional.of(Proveedor.builder().proveedorId(1).razonSocial("Ferritas SA").build()))
                .when(proveedorRepo).findById(1);
        doReturn(Optional.of(Almacen.builder().almacenId(1).nombre("Bodega Central").build()))
                .when(almacenRepo).findById(1);
        doReturn(Optional.of(FormaPago.builder().formaPagoId(1).nombre("Contado").clave("EFECTIVO").build()))
                .when(formaPagoRepo).findById(1);
        doReturn(null).when(cajaService).resolverTurnoAbierto(5, 1);
        org.mockito.Mockito.lenient().doThrow(new ReglaNegocioException(ErrorCode.TURNO_NO_ABIERTO, 5))
                .when(cajaService).resolverTurnoAbierto(5, 1);

        CompraRequest req = new CompraRequest(
                1, 1, 1, "F-0004", null, 5, null,
                List.of(new CompraDetalleRequest(10L, new BigDecimal("1.000"),
                        new BigDecimal("10.00"))));

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("create: proveedor inexistente -> RecursoNoEncontradoException")
    void create_proveedorInvalido() {
        doReturn(Optional.empty()).when(proveedorRepo).findById(999);

        CompraRequest req = new CompraRequest(
                999, 1, 1, null, null, null, null,
                List.of(new CompraDetalleRequest(10L, new BigDecimal("1.000"),
                        new BigDecimal("10.00"))));

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("cuentasPagar: sin filtro consulta toda la vista")
    void cuentasPagar_sinFiltro() {
        var v = new mx.ferreteria.api.com.dto.ComDtos.CuentasPagarResponse(
                1L, "COMPRA-0001", "Ferritas SA",
                new BigDecimal("1160.00"), new BigDecimal("600.00"),
                new BigDecimal("560.00"), java.time.LocalDate.now(), 5, "PENDIENTE", null);
        doReturn(List.of(v)).when(reportRepo).vwCuentasPagar();

        var result = service.cuentasPagar(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).proveedor()).isEqualTo("Ferritas SA");
    }

    @Test
    @DisplayName("cuentasPagar: con estado pasa el parametro")
    void cuentasPagar_conEstado() {
        var v = new mx.ferreteria.api.com.dto.ComDtos.CuentasPagarResponse(
                1L, "COMPRA-0001", "Ferritas SA",
                new BigDecimal("1160.00"), new BigDecimal("600.00"),
                new BigDecimal("560.00"), java.time.LocalDate.now(), 5, "PENDIENTE", null);
        doReturn(List.of(v)).when(reportRepo).vwCuentasPagarPorEstado("PENDIENTE");

        var result = service.cuentasPagar("PENDIENTE");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).estado()).isEqualTo("PENDIENTE");
    }

    @Test
    @DisplayName("facturasVencidas y pendientes: consultan sus vistas")
    void facturasVencidasYPendientes() {
        var v = new mx.ferreteria.api.com.dto.ComDtos.FacturaVencidaResponse(
                1L, "COMPRA-0001", "F-0001", 1, "Ferritas SA", "555-0100",
                java.time.LocalDate.now().minusDays(30),
                new BigDecimal("1160.00"), new BigDecimal("1160.00"),
                BigDecimal.ZERO, java.time.LocalDate.now().minusDays(10),
                10, "10-20 dias", null);
        doReturn(List.of(v)).when(reportRepo).vwFacturasVencidas();
        doReturn(Collections.emptyList()).when(reportRepo).vwFacturasPendientes();

        assertThat(service.facturasVencidas()).hasSize(1);
        assertThat(service.facturasPendientes()).isEmpty();
    }

    @Test
    @DisplayName("facturasProveedor: consulta vista con filtro de proveedor")
    void facturasProveedor() {
        var v = new mx.ferreteria.api.com.dto.ComDtos.FacturaProveedorResponse(
                1, 1, "Ferritas SA", "COMPRA-0001", "F-0001",
                java.time.LocalDate.now().minusDays(5),
                new BigDecimal("1000.00"), new BigDecimal("160.00"),
                new BigDecimal("1160.00"), new BigDecimal("1160.00"),
                new BigDecimal("1160.00"), BigDecimal.ZERO,
                "CONTADO", java.time.LocalDate.now().plusDays(55), null);
        doReturn(List.of(v)).when(reportRepo).vwUltimasFacturasProveedor(1);

        var result = service.facturasProveedor(1);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).proveedor()).isEqualTo("Ferritas SA");
    }

    @Test
    @DisplayName("abonar: liquida la cuenta al cubrir el saldo y registra salida en caja")
    void abonar_liquidaCuenta() {
        var inicial = new mx.ferreteria.api.com.dto.ComDtos.CuentaPagoDetalle(
                1L, "COMPRA-0001", "VIGENTE", new BigDecimal("1160.00"),
                new BigDecimal("600.00"), new BigDecimal("560.00"), 1);
        var finalizada = new mx.ferreteria.api.com.dto.ComDtos.CuentaPagoDetalle(
                1L, "COMPRA-0001", "LIQUIDADA", new BigDecimal("1160.00"),
                new BigDecimal("1160.00"), BigDecimal.ZERO, 1);
        doReturn(List.of(inicial), List.of(finalizada))
                .when(reportRepo).findCuentaPagoDetalle(1L);
        stubInsertPagoRetornando(99L);
        doReturn(Optional.of(FormaPago.builder().formaPagoId(1).nombre("Efectivo").clave("EFECTIVO").build()))
                .when(formaPagoRepo).findById(1);
        doReturn(6L).when(cajaService).resolverTurnoAbierto(5, 1);

        var req = new mx.ferreteria.api.com.dto.ComDtos.PagoProveedorRequest(
                new BigDecimal("560.00"), 1, 5, null);

        var resp = service.abonar(1L, req);

        assertThat(resp.pagoProveedorId()).isEqualTo(99L);
        assertThat(resp.estado()).isEqualTo("LIQUIDADA");
        assertThat(resp.saldo()).isEqualByComparingTo("0.00");
        assertThat(resp.compraFolio()).isEqualTo("COMPRA-0001");
        verify(cajaService).resolverTurnoAbierto(5, 1);
        verify(em).createNativeQuery(
                org.mockito.ArgumentMatchers.contains("INSERT INTO com.pagos_proveedor"));
    }

    @Test
    @DisplayName("abonar: abono parcial deja la cuenta en PARCIAL con saldo restante")
    void abonar_parcial() {
        var inicial = new mx.ferreteria.api.com.dto.ComDtos.CuentaPagoDetalle(
                1L, "COMPRA-0001", "VIGENTE", new BigDecimal("1160.00"),
                new BigDecimal("600.00"), new BigDecimal("560.00"), 1);
        var parcial = new mx.ferreteria.api.com.dto.ComDtos.CuentaPagoDetalle(
                1L, "COMPRA-0001", "PARCIAL", new BigDecimal("1160.00"),
                new BigDecimal("900.00"), new BigDecimal("260.00"), 1);
        doReturn(List.of(inicial), List.of(parcial))
                .when(reportRepo).findCuentaPagoDetalle(1L);
        stubInsertPagoRetornando(1L);
        doReturn(Optional.of(FormaPago.builder().formaPagoId(1).nombre("Efectivo").clave("EFECTIVO").build()))
                .when(formaPagoRepo).findById(1);
        doReturn(6L).when(cajaService).resolverTurnoAbierto(5, 1);

        var req = new mx.ferreteria.api.com.dto.ComDtos.PagoProveedorRequest(
                new BigDecimal("300.00"), 1, 5, "ABONO PARCIAL 2");

        var resp = service.abonar(1L, req);

        assertThat(resp.estado()).isEqualTo("PARCIAL");
        assertThat(resp.saldo()).isEqualByComparingTo("260.00");
        verify(em).createNativeQuery(
                org.mockito.ArgumentMatchers.contains("INSERT INTO com.pagos_proveedor"));
    }

    @Test
    @DisplayName("abonar: cuenta liquidada o cancelada -> ESTADO_INVALIDO")
    void abonar_cuentaCerrada() {
        var cerrada = new mx.ferreteria.api.com.dto.ComDtos.CuentaPagoDetalle(
                1L, "COMPRA-0001", "LIQUIDADA", new BigDecimal("1160.00"),
                new BigDecimal("1160.00"), BigDecimal.ZERO, 1);
        doReturn(List.of(cerrada)).when(reportRepo).findCuentaPagoDetalle(1L);

        var req = new mx.ferreteria.api.com.dto.ComDtos.PagoProveedorRequest(
                new BigDecimal("10.00"), 1, 5, null);

        ReglaNegocioException ex = org.assertj.core.api.Assertions
                .catchThrowableOfType(() -> service.abonar(1L, req), ReglaNegocioException.class);
        assertThat(ex.errorCode()).isEqualTo(ErrorCode.ESTADO_INVALIDO);
        org.mockito.Mockito.verifyNoInteractions(cajaService, formaPagoRepo);
    }

    @Test
    @DisplayName("abonar: cuenta inexistente -> RecursoNoEncontradoException")
    void abonar_cuentaInexistente() {
        doReturn(Collections.emptyList()).when(reportRepo).findCuentaPagoDetalle(999L);

        var req = new mx.ferreteria.api.com.dto.ComDtos.PagoProveedorRequest(
                new BigDecimal("10.00"), 1, 5, null);

        assertThatThrownBy(() -> service.abonar(999L, req))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("abonar: monto mayor al saldo -> VALOR_INVALIDO")
    void abonar_montoExcedeSaldo() {
        var inicial = new mx.ferreteria.api.com.dto.ComDtos.CuentaPagoDetalle(
                1L, "COMPRA-0001", "VIGENTE", new BigDecimal("1160.00"),
                new BigDecimal("600.00"), new BigDecimal("560.00"), 1);
        doReturn(List.of(inicial)).when(reportRepo).findCuentaPagoDetalle(1L);

        var req = new mx.ferreteria.api.com.dto.ComDtos.PagoProveedorRequest(
                new BigDecimal("600.00"), 1, 5, null);

        ReglaNegocioException ex = org.assertj.core.api.Assertions
                .catchThrowableOfType(() -> service.abonar(1L, req), ReglaNegocioException.class);
        assertThat(ex.errorCode()).isEqualTo(ErrorCode.VALOR_INVALIDO);
    }

    @Test
    @DisplayName("abonar contado sin caja: CAMPO_REQUERIDO")
    void abonar_contadoSinCaja() {
        var inicial = new mx.ferreteria.api.com.dto.ComDtos.CuentaPagoDetalle(
                1L, "COMPRA-0001", "VIGENTE", new BigDecimal("1160.00"),
                new BigDecimal("600.00"), new BigDecimal("560.00"), 1);
        doReturn(List.of(inicial)).when(reportRepo).findCuentaPagoDetalle(1L);
        doReturn(Optional.of(FormaPago.builder().formaPagoId(1).nombre("Efectivo").clave("EFECTIVO").build()))
                .when(formaPagoRepo).findById(1);

        var req = new mx.ferreteria.api.com.dto.ComDtos.PagoProveedorRequest(
                new BigDecimal("560.00"), 1, null, null);

        ReglaNegocioException ex = org.assertj.core.api.Assertions
                .catchThrowableOfType(() -> service.abonar(1L, req), ReglaNegocioException.class);
        assertThat(ex.errorCode()).isEqualTo(ErrorCode.CAMPO_REQUERIDO);
        org.mockito.Mockito.verifyNoInteractions(cajaService);
    }

    @Test
    @DisplayName("abonar credito: no exige caja ni registra salida")
    void abonar_creditoSinCaja() {
        var inicial = new mx.ferreteria.api.com.dto.ComDtos.CuentaPagoDetalle(
                1L, "COMPRA-0001", "VIGENTE", new BigDecimal("1160.00"),
                new BigDecimal("600.00"), new BigDecimal("560.00"), 1);
        var parcial = new mx.ferreteria.api.com.dto.ComDtos.CuentaPagoDetalle(
                1L, "COMPRA-0001", "PARCIAL", new BigDecimal("1160.00"),
                new BigDecimal("900.00"), new BigDecimal("260.00"), 1);
        doReturn(List.of(inicial), List.of(parcial))
                .when(reportRepo).findCuentaPagoDetalle(1L);
        stubInsertPagoRetornando(2L);
        doReturn(Optional.of(FormaPago.builder().formaPagoId(6).nombre("Crédito").clave("CREDITO").build()))
                .when(formaPagoRepo).findById(6);

        var req = new mx.ferreteria.api.com.dto.ComDtos.PagoProveedorRequest(
                new BigDecimal("300.00"), 6, null, null);

        var resp = service.abonar(1L, req);

        assertThat(resp.turnoCajaId()).isNull();
        org.mockito.Mockito.verifyNoInteractions(cajaService);
        verify(em).createNativeQuery(
                org.mockito.ArgumentMatchers.contains("INSERT INTO com.pagos_proveedor"));
    }

    // ── list: resto de filtros ────────────────────────────────────

    @Test
    @DisplayName("list: filtra por almacen + rango de fechas")
    void list_byAlmacenYFechas() {
        Pageable pg = PageRequest.of(0, 20);
        LocalDate desde = LocalDate.now().minusDays(30);
        LocalDate hasta = LocalDate.now();
        Compra c = sampleCompra(1L);
        doReturn(new PageImpl<>(List.of(c), pg, 1))
                .when(compraRepo).findByAlmacenIdAndFechaLocalBetweenOrderByFechaDesc(
                        eq(1), eq(desde), eq(hasta), org.mockito.ArgumentMatchers.any(Pageable.class));
        stubNombres();
        doReturn(List.of(sampleDetalle(1L)))
                .when(detalleRepo).findByCompraIdOrderByCompraDetalleId(1L);

        var result = service.list(1, null, desde, hasta, pg);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).folio()).isEqualTo("COMPRA-0001");
    }

    @Test
    @DisplayName("list: filtra por proveedor")
    void list_byProveedor() {
        Pageable pg = PageRequest.of(0, 20);
        Compra c = sampleCompra(1L);
        doReturn(new PageImpl<>(List.of(c), pg, 1))
                .when(compraRepo).findByProveedorIdOrderByFechaDesc(
                        eq(1), org.mockito.ArgumentMatchers.any(Pageable.class));
        stubNombres();
        doReturn(List.of(sampleDetalle(1L)))
                .when(detalleRepo).findByCompraIdOrderByCompraDetalleId(1L);

        var result = service.list(null, 1, null, null, pg);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).proveedor()).isEqualTo("Ferritas SA");
    }

    @Test
    @DisplayName("list: filtra por rango de fechas")
    void list_byFechas() {
        Pageable pg = PageRequest.of(0, 20);
        LocalDate desde = LocalDate.now().minusDays(30);
        LocalDate hasta = LocalDate.now();
        Compra c = sampleCompra(1L);
        doReturn(new PageImpl<>(List.of(c), pg, 1))
                .when(compraRepo).findByFechaLocalBetweenOrderByFechaDesc(
                        eq(desde), eq(hasta), org.mockito.ArgumentMatchers.any(Pageable.class));
        stubNombres();
        doReturn(List.of(sampleDetalle(1L)))
                .when(detalleRepo).findByCompraIdOrderByCompraDetalleId(1L);

        var result = service.list(null, null, desde, hasta, pg);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("list: sin filtros usa consulta general ordenada por fecha")
    void list_sinFiltros() {
        Pageable pg = PageRequest.of(0, 20);
        Compra c = sampleCompra(1L);
        doReturn(new PageImpl<>(List.of(c), pg, 1))
                .when(compraRepo).findByOrderByFechaDesc(
                        org.mockito.ArgumentMatchers.any(Pageable.class));
        stubNombres();
        doReturn(List.of(sampleDetalle(1L)))
                .when(detalleRepo).findByCompraIdOrderByCompraDetalleId(1L);

        var result = service.list(null, null, null, null, pg);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("list: pageable ya ordenado no se normaliza")
    void list_sortedMantieneSort() {
        Pageable pg = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "folio"));
        Compra c = sampleCompra(1L);
        doReturn(new PageImpl<>(List.of(c), pg, 1))
                .when(compraRepo).findByOrderByFechaDesc(eq(pg));
        stubNombres();
        doReturn(List.of(sampleDetalle(1L)))
                .when(detalleRepo).findByCompraIdOrderByCompraDetalleId(1L);

        var result = service.list(null, null, null, null, pg);

        assertThat(result.getContent()).hasSize(1);
        verify(compraRepo).findByOrderByFechaDesc(pg);
    }

    @Test
    @DisplayName("list: almacen + desde sin hasta ignora el rango y filtra solo por almacen")
    void list_almacenConDesdeSinHasta() {
        Pageable pg = PageRequest.of(0, 20);
        LocalDate desde = LocalDate.now().minusDays(30);
        Compra c = sampleCompra(1L);
        doReturn(new PageImpl<>(List.of(c), pg, 1))
                .when(compraRepo).findByAlmacenIdOrderByFechaDesc(
                        eq(1), org.mockito.ArgumentMatchers.any(Pageable.class));
        stubNombres();
        doReturn(List.of(sampleDetalle(1L)))
                .when(detalleRepo).findByCompraIdOrderByCompraDetalleId(1L);

        var result = service.list(1, null, desde, null, pg);

        assertThat(result.getContent()).hasSize(1);
        verify(compraRepo).findByAlmacenIdOrderByFechaDesc(
                eq(1), org.mockito.ArgumentMatchers.any(Pageable.class));
    }

    @Test
    @DisplayName("list: desde sin hasta ignora el rango y usa consulta general")
    void list_desdeSinHasta() {
        Pageable pg = PageRequest.of(0, 20);
        LocalDate desde = LocalDate.now().minusDays(30);
        Compra c = sampleCompra(1L);
        doReturn(new PageImpl<>(List.of(c), pg, 1))
                .when(compraRepo).findByOrderByFechaDesc(
                        org.mockito.ArgumentMatchers.any(Pageable.class));
        stubNombres();
        doReturn(List.of(sampleDetalle(1L)))
                .when(detalleRepo).findByCompraIdOrderByCompraDetalleId(1L);

        var result = service.list(null, null, desde, null, pg);

        assertThat(result.getContent()).hasSize(1);
        verify(compraRepo).findByOrderByFechaDesc(
                org.mockito.ArgumentMatchers.any(Pageable.class));
    }

    @Test
    @DisplayName("list: pagina vacia retorna vacio sin consultas extra")
    void list_paginaVacia() {
        Pageable pg = PageRequest.of(0, 20);
        doReturn(new PageImpl<>(List.of(), pg, 0))
                .when(compraRepo).findByOrderByFechaDesc(
                        org.mockito.ArgumentMatchers.any(Pageable.class));

        var result = service.list(null, null, null, null, pg);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("list: pagina con varios usa ensamblador batch y tolera nombres ausentes")
    void list_multiples_batch() {
        Pageable pg = PageRequest.of(0, 10);
        Compra c1 = sampleCompra(1L);
        Compra c2 = sampleCompra(2L);
        c2.setProveedorId(2);
        c2.setAlmacenId(2);
        c2.setFormaPagoId(2);
        Compra c3 = sampleCompra(3L);
        doReturn(new PageImpl<>(List.of(c1, c2, c3), pg, 3))
                .when(compraRepo).findByOrderByFechaDesc(
                        org.mockito.ArgumentMatchers.any(Pageable.class));
        doReturn(List.of(Proveedor.builder().proveedorId(1).razonSocial("Ferritas SA").build()))
                .when(proveedorRepo).findAllById(org.mockito.ArgumentMatchers.any());
        doReturn(List.of(Almacen.builder().almacenId(1).nombre("Bodega Central").build()))
                .when(almacenRepo).findAllById(org.mockito.ArgumentMatchers.any());
        doReturn(List.of(FormaPago.builder().formaPagoId(1).nombre("Contado").build()))
                .when(formaPagoRepo).findAllById(org.mockito.ArgumentMatchers.any());
        CompraDetalle d2 = CompraDetalle.builder().compraDetalleId(2L)
                .compraId(2L).productoId(99L)
                .cantidad(new BigDecimal("2.000"))
                .costoUnitario(new BigDecimal("50.00"))
                .importeLinea(new BigDecimal("100.00")).build();
        doReturn(List.of(sampleDetalle(1L), d2))
                .when(detalleRepo).findByCompraIdIn(org.mockito.ArgumentMatchers.any());
        doReturn(List.of(Producto.builder().productoId(10L).nombre("Taladro").build()))
                .when(productoRepo).findAllById(org.mockito.ArgumentMatchers.any());

        var result = service.list(null, null, null, null, pg);

        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent().get(0).proveedor()).isEqualTo("Ferritas SA");
        assertThat(result.getContent().get(0).almacen()).isEqualTo("Bodega Central");
        assertThat(result.getContent().get(0).formaPago()).isEqualTo("Contado");
        assertThat(result.getContent().get(0).detalles()).hasSize(1);
        assertThat(result.getContent().get(0).detalles().get(0).producto()).isEqualTo("Taladro");
        // c2: ids desconocidos -> nombres nulos, producto desconocido -> nulo
        assertThat(result.getContent().get(1).proveedor()).isNull();
        assertThat(result.getContent().get(1).almacen()).isNull();
        assertThat(result.getContent().get(1).formaPago()).isNull();
        assertThat(result.getContent().get(1).detalles()).hasSize(1);
        assertThat(result.getContent().get(1).detalles().get(0).producto()).isNull();
        // c3: sin detalles -> lista vacia
        assertThat(result.getContent().get(2).detalles()).isEmpty();
    }

    @Test
    @DisplayName("list: batch sin detalles no consulta productos")
    void list_batchSinDetalles() {
        Pageable pg = PageRequest.of(0, 10);
        doReturn(new PageImpl<>(List.of(sampleCompra(1L), sampleCompra(2L)), pg, 2))
                .when(compraRepo).findByOrderByFechaDesc(
                        org.mockito.ArgumentMatchers.any(Pageable.class));
        doReturn(List.of(Proveedor.builder().proveedorId(1).razonSocial("Ferritas SA").build()))
                .when(proveedorRepo).findAllById(org.mockito.ArgumentMatchers.any());
        doReturn(List.of(Almacen.builder().almacenId(1).nombre("Bodega Central").build()))
                .when(almacenRepo).findAllById(org.mockito.ArgumentMatchers.any());
        doReturn(List.of(FormaPago.builder().formaPagoId(1).nombre("Contado").build()))
                .when(formaPagoRepo).findAllById(org.mockito.ArgumentMatchers.any());
        doReturn(List.of()).when(detalleRepo).findByCompraIdIn(org.mockito.ArgumentMatchers.any());

        var result = service.list(null, null, null, null, pg);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).detalles()).isEmpty();
        assertThat(result.getContent().get(1).detalles()).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(productoRepo);
    }

    // ── getById: ruta exitosa ─────────────────────────────────────

    @Test
    @DisplayName("getById: retorna compra con nombres y detalles")
    void getById_found() {
        doReturn(Optional.of(sampleCompra(1L))).when(compraRepo).findById(1L);
        stubNombres();
        doReturn(List.of(sampleDetalle(1L)))
                .when(detalleRepo).findByCompraIdOrderByCompraDetalleId(1L);

        var resp = service.getById(1L);

        assertThat(resp.compraId()).isEqualTo(1L);
        assertThat(resp.folio()).isEqualTo("COMPRA-0001");
        assertThat(resp.proveedor()).isEqualTo("Ferritas SA");
        assertThat(resp.almacen()).isEqualTo("Bodega Central");
        assertThat(resp.detalles()).hasSize(1);
        assertThat(resp.detalles().get(0).producto()).isEqualTo("Taladro");
    }

    @Test
    @DisplayName("getById: tolera nombres ausentes con nulos")
    void getById_nombresAusentes() {
        Compra c = sampleCompra(1L);
        c.setProveedorId(9);
        c.setAlmacenId(9);
        c.setFormaPagoId(9);
        doReturn(Optional.of(c)).when(compraRepo).findById(1L);
        doReturn(Optional.empty()).when(proveedorRepo).findById(9);
        doReturn(Optional.empty()).when(almacenRepo).findById(9);
        doReturn(Optional.empty()).when(formaPagoRepo).findById(9);
        CompraDetalle d = sampleDetalle(1L);
        d.setProductoId(99L);
        doReturn(List.of(d)).when(detalleRepo).findByCompraIdOrderByCompraDetalleId(1L);
        doReturn(Optional.empty()).when(productoRepo).findById(99L);

        var resp = service.getById(1L);

        assertThat(resp.proveedor()).isNull();
        assertThat(resp.almacen()).isNull();
        assertThat(resp.formaPago()).isNull();
        assertThat(resp.detalles().get(0).producto()).isNull();
    }

    // ── create: errores restantes ─────────────────────────────────

    @Test
    @DisplayName("create: almacen inexistente -> RecursoNoEncontradoException")
    void create_almacenInvalido() {
        doReturn(Optional.of(Proveedor.builder().proveedorId(1).razonSocial("Ferritas SA").build()))
                .when(proveedorRepo).findById(1);
        doReturn(Optional.empty()).when(almacenRepo).findById(1);

        CompraRequest req = new CompraRequest(
                1, 1, 1, null, null, null, null,
                List.of(new CompraDetalleRequest(10L, new BigDecimal("1.000"),
                        new BigDecimal("10.00"))));

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("create: forma de pago inexistente -> RecursoNoEncontradoException")
    void create_formaPagoInvalida() {
        doReturn(Optional.of(Proveedor.builder().proveedorId(1).razonSocial("Ferritas SA").build()))
                .when(proveedorRepo).findById(1);
        doReturn(Optional.of(Almacen.builder().almacenId(1).nombre("Bodega Central").build()))
                .when(almacenRepo).findById(1);
        doReturn(Optional.empty()).when(formaPagoRepo).findById(99);

        CompraRequest req = new CompraRequest(
                1, 1, 99, null, null, null, null,
                List.of(new CompraDetalleRequest(10L, new BigDecimal("1.000"),
                        new BigDecimal("10.00"))));

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("create: si la relectura falla usa la entidad guardada")
    void create_refreshFallback() {
        Compra saved = sampleCompra(52L);
        doReturn(Optional.of(Proveedor.builder().proveedorId(1).razonSocial("Ferritas SA").build()))
                .when(proveedorRepo).findById(1);
        doReturn(Optional.of(Almacen.builder().almacenId(1).nombre("Bodega Central").build()))
                .when(almacenRepo).findById(1);
        doReturn(Optional.of(FormaPago.builder().formaPagoId(6).nombre("Crédito").clave("CREDITO").build()))
                .when(formaPagoRepo).findById(6);
        doReturn(saved).when(compraRepo).save(any(Compra.class));
        doReturn(Optional.empty()).when(compraRepo).findById(52L);
        doReturn(List.of(sampleDetalle(1L)))
                .when(detalleRepo).findByCompraIdOrderByCompraDetalleId(52L);

        CompraRequest req = new CompraRequest(
                1, 1, 6, "F-0005", null, null, null,
                List.of(new CompraDetalleRequest(10L, new BigDecimal("1.000"),
                        new BigDecimal("10.00"))));

        var resp = service.create(req);

        assertThat(resp.compraId()).isEqualTo(52L);
        assertThat(resp.folio()).isEqualTo("COMPRA-0001");
        verify(compraRepo).flush();
    }

    // ── cuentasPagar: estado en blanco ────────────────────────────

    @Test
    @DisplayName("cuentasPagar: estado en blanco consulta toda la vista")
    void cuentasPagar_estadoEnBlanco() {
        doReturn(Collections.emptyList()).when(reportRepo).vwCuentasPagar();

        assertThat(service.cuentasPagar("   ")).isEmpty();
        verify(reportRepo).vwCuentasPagar();
        org.mockito.Mockito.verifyNoMoreInteractions(reportRepo);
    }

    // ── abonar: ramas restantes ───────────────────────────────────

    @Test
    @DisplayName("abonar: cuenta cancelada -> ESTADO_INVALIDO")
    void abonar_cuentaCancelada() {
        var cancelada = new mx.ferreteria.api.com.dto.ComDtos.CuentaPagoDetalle(
                1L, "COMPRA-0001", "CANCELADA", new BigDecimal("1160.00"),
                new BigDecimal("1160.00"), BigDecimal.ZERO, 1);
        doReturn(List.of(cancelada)).when(reportRepo).findCuentaPagoDetalle(1L);

        var req = new mx.ferreteria.api.com.dto.ComDtos.PagoProveedorRequest(
                new BigDecimal("10.00"), 1, 5, null);

        org.assertj.core.api.Assertions.assertThat(
                org.assertj.core.api.Assertions.catchThrowableOfType(
                        () -> service.abonar(1L, req), ReglaNegocioException.class)
                        .errorCode()).isEqualTo(ErrorCode.ESTADO_INVALIDO);
    }

    @Test
    @DisplayName("abonar: monto nulo -> VALOR_INVALIDO")
    void abonar_montoNulo() {
        var inicial = new mx.ferreteria.api.com.dto.ComDtos.CuentaPagoDetalle(
                1L, "COMPRA-0001", "VIGENTE", new BigDecimal("1160.00"),
                new BigDecimal("600.00"), new BigDecimal("560.00"), 1);
        doReturn(List.of(inicial)).when(reportRepo).findCuentaPagoDetalle(1L);

        var req = new mx.ferreteria.api.com.dto.ComDtos.PagoProveedorRequest(
                null, 1, 5, null);

        org.assertj.core.api.Assertions.assertThat(
                org.assertj.core.api.Assertions.catchThrowableOfType(
                        () -> service.abonar(1L, req), ReglaNegocioException.class)
                        .errorCode()).isEqualTo(ErrorCode.VALOR_INVALIDO);
    }

    @Test
    @DisplayName("abonar: forma de pago inexistente -> RecursoNoEncontradoException")
    void abonar_formaPagoInvalida() {
        var inicial = new mx.ferreteria.api.com.dto.ComDtos.CuentaPagoDetalle(
                1L, "COMPRA-0001", "VIGENTE", new BigDecimal("1160.00"),
                new BigDecimal("600.00"), new BigDecimal("560.00"), 1);
        doReturn(List.of(inicial)).when(reportRepo).findCuentaPagoDetalle(1L);
        doReturn(Optional.empty()).when(formaPagoRepo).findById(99);

        var req = new mx.ferreteria.api.com.dto.ComDtos.PagoProveedorRequest(
                new BigDecimal("100.00"), 99, 5, null);

        assertThatThrownBy(() -> service.abonar(1L, req))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("abonar: referencia en blanco usa ABONO por defecto")
    void abonar_referenciaBlank() {
        var inicial = new mx.ferreteria.api.com.dto.ComDtos.CuentaPagoDetalle(
                1L, "COMPRA-0001", "VIGENTE", new BigDecimal("1160.00"),
                new BigDecimal("600.00"), new BigDecimal("560.00"), 1);
        var finalizada = new mx.ferreteria.api.com.dto.ComDtos.CuentaPagoDetalle(
                1L, "COMPRA-0001", "LIQUIDADA", new BigDecimal("1160.00"),
                new BigDecimal("1160.00"), BigDecimal.ZERO, 1);
        doReturn(List.of(inicial), List.of(finalizada))
                .when(reportRepo).findCuentaPagoDetalle(1L);
        var q = stubInsertPagoRetornando(7L);
        doReturn(Optional.of(FormaPago.builder().formaPagoId(1).nombre("Efectivo").clave("EFECTIVO").build()))
                .when(formaPagoRepo).findById(1);
        doReturn(6L).when(cajaService).resolverTurnoAbierto(5, 1);

        var req = new mx.ferreteria.api.com.dto.ComDtos.PagoProveedorRequest(
                new BigDecimal("560.00"), 1, 5, "   ");

        var resp = service.abonar(1L, req);

        assertThat(resp.pagoProveedorId()).isEqualTo(7L);
        verify(q).setParameter("referencia", "ABONO");
    }

    @Test
    @DisplayName("abonar: referencia se recorta antes de guardar")
    void abonar_referenciaTrim() {
        var inicial = new mx.ferreteria.api.com.dto.ComDtos.CuentaPagoDetalle(
                1L, "COMPRA-0001", "VIGENTE", new BigDecimal("1160.00"),
                new BigDecimal("600.00"), new BigDecimal("560.00"), 1);
        var parcial = new mx.ferreteria.api.com.dto.ComDtos.CuentaPagoDetalle(
                1L, "COMPRA-0001", "PARCIAL", new BigDecimal("1160.00"),
                new BigDecimal("900.00"), new BigDecimal("260.00"), 1);
        doReturn(List.of(inicial), List.of(parcial))
                .when(reportRepo).findCuentaPagoDetalle(1L);
        var q = stubInsertPagoRetornando(8L);
        doReturn(Optional.of(FormaPago.builder().formaPagoId(1).nombre("Efectivo").clave("EFECTIVO").build()))
                .when(formaPagoRepo).findById(1);
        doReturn(6L).when(cajaService).resolverTurnoAbierto(5, 1);

        var req = new mx.ferreteria.api.com.dto.ComDtos.PagoProveedorRequest(
                new BigDecimal("300.00"), 1, 5, "  PAGO-1  ");

        var resp = service.abonar(1L, req);

        assertThat(resp.pagoProveedorId()).isEqualTo(8L);
        verify(q).setParameter("referencia", "PAGO-1");
    }
}