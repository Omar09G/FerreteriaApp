package mx.ferreteria.api.ven.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.util.ReflectionTestUtils;

import mx.ferreteria.api.cat.entity.Ciudad;
import mx.ferreteria.api.cat.entity.Cliente;
import mx.ferreteria.api.cat.entity.FormaPago;
import mx.ferreteria.api.cat.entity.Producto;
import mx.ferreteria.api.cat.repo.CiudadRepository;
import mx.ferreteria.api.cat.repo.ClienteRepository;
import mx.ferreteria.api.cat.repo.FormaPagoRepository;
import mx.ferreteria.api.cat.repo.ProductoRepository;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.common.error.ValidacionException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.fin.service.CajaService;
import mx.ferreteria.api.inv.entity.Almacen;
import mx.ferreteria.api.inv.repo.AlmacenRepository;
import mx.ferreteria.api.ven.dto.VenDtos;
import mx.ferreteria.api.ven.entity.CuentaCobrar;
import mx.ferreteria.api.ven.entity.PagoCliente;
import mx.ferreteria.api.ven.entity.Promocion;
import mx.ferreteria.api.ven.entity.Venta;
import mx.ferreteria.api.ven.entity.VentaDetalle;
import mx.ferreteria.api.ven.repo.CuentaCobrarRepository;
import mx.ferreteria.api.ven.repo.PagoClienteRepository;
import mx.ferreteria.api.ven.repo.PromocionRepository;
import mx.ferreteria.api.ven.repo.VentaDetalleRepository;
import mx.ferreteria.api.ven.repo.VentaRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VentaServiceTest {

        @Mock
        VentaRepository ventaRepo;
        @Mock
        VentaDetalleRepository detalleRepo;
        @Mock
        AlmacenRepository almacenRepo;
        @Mock
        ClienteRepository clienteRepo;
        @Mock
        CiudadRepository ciudadRepo;
        @Mock
        ProductoRepository productoRepo;
        @Mock
        FormaPagoRepository formaPagoRepo;
        @Mock
        CajaService cajaService;
        @Mock
        CuentaCobrarRepository cuentaRepo;
        @Mock
        PagoClienteRepository pagoRepo;
        @Mock
        PromocionRepository promocionRepo;
        @Mock
        PromocionService promocionService;
        @Mock
        org.springframework.context.ApplicationEventPublisher events;
        @Mock
        mx.ferreteria.api.ven.pdf.TicketPdfService ticketPdfService;
        @Mock
        mx.ferreteria.api.ven.service.VentaTicketPort ticketPort;
        @Mock
        mx.ferreteria.api.common.error.DbErrorTranslator dbTranslator;
        @Mock
        jakarta.persistence.EntityManager em;

        @InjectMocks
        VentaService service;

        @BeforeEach
        void inyectarEntityManager() {
                // @PersistenceContext no lo resuelve @InjectMocks: se setea explícito.
                ReflectionTestUtils.setField(service, "em", em);
        }

        // ── helpers ──────────────────────────────────────────────────────

        private Venta sampleVenta(Long id, String folio, String estado) {
                return Venta.builder()
                                .ventaId(id).folio(folio).almacenId(1).formaPagoId(1)
                                .subtotal(new BigDecimal("100.00")).iva(new BigDecimal("16.00"))
                                .total(new BigDecimal("116.00")).estado(estado).usuarioId(1)
                                .ivaTasa(new BigDecimal("16.00")).ivaIncluido(true)
                                .descuentoTotal(BigDecimal.ZERO).fecha(Instant.now())
                                .build();
        }

        private VentaDetalle sampleDetalle(Long id, Long ventaId, Long productoId) {
                return VentaDetalle.builder()
                                .ventaDetalleId(id).ventaId(ventaId).productoId(productoId)
                                .cantidad(new BigDecimal("2.000")).precioUnitario(new BigDecimal("50.00"))
                                .costoUnitario(BigDecimal.ZERO).descuentoLinea(BigDecimal.ZERO)
                                .build();
        }

        private void stubToResponse() {
                when(almacenRepo.findById(1))
                                .thenReturn(Optional
                                                .of(Almacen.builder().almacenId(1).nombre("Almacen Central").build()));
                when(formaPagoRepo.findById(1))
                                .thenReturn(Optional.of(FormaPago.builder().formaPagoId(1).nombre("EFECTIVO").build()));
                when(detalleRepo.findByVentaId(anyLong())).thenReturn(List.of());
                when(cuentaRepo.findByVentaId(anyLong())).thenReturn(Optional.empty());
                when(productoRepo.findById(anyLong()))
                                .thenReturn(Optional.of(Producto.builder().productoId(1L).nombre("Martillo").build()));
                when(clienteRepo.findById(anyLong())).thenReturn(Optional.empty());
        }

        private Pageable pg() {
                return PageRequest.of(0, 10);
        }

        private VenDtos.VentaRequest ventaRequest(Integer almacenId, Integer cajaId, Long clienteId,
                        Long promocionId, List<VenDtos.VentaDetalleRequest> detalles) {
                return new VenDtos.VentaRequest(
                                almacenId, cajaId, clienteId, null, 1,
                                detalles,
                                List.of(new VenDtos.PagoRequest(1, new BigDecimal("116.00"), null)),
                                null, promocionId);
        }

        private VenDtos.VentaDetalleRequest linea(Long productoId, String cantidad, String precio) {
                return new VenDtos.VentaDetalleRequest(productoId, new BigDecimal(cantidad),
                                new BigDecimal(precio));
        }

        private VenDtos.PromocionEvaluacionResponse evalResp(Long promoId, boolean aplica, BigDecimal beneficio) {
                return new VenDtos.PromocionEvaluacionResponse(
                                promoId, "Promo", "DESCUENTO_TOTAL_VENTA", "ACTIVA",
                                aplica, null, beneficio,
                                null, null, null, null, null, null, 0, null, null,
                                List.of(), null, null, false, List.of(), List.of());
        }

        /** Stubs del camino feliz de checkout sin promoción; devuelve la venta recargada. */
        private Venta stubCheckoutOk(Venta saved) {
                when(almacenRepo.existsById(1)).thenReturn(true);
                when(formaPagoRepo.findById(1))
                                .thenReturn(Optional.of(FormaPago.builder().formaPagoId(1).nombre("EFECTIVO").build()));
                when(ventaRepo.save(any(Venta.class))).thenReturn(saved);
                when(ventaRepo.reloadAfterTriggers(saved.getVentaId())).thenReturn(Optional.of(saved));
                when(detalleRepo.findByVentaId(saved.getVentaId())).thenReturn(List.of());
                when(cuentaRepo.findByVentaId(saved.getVentaId())).thenReturn(Optional.empty());
                return saved;
        }

        /** Simula fn_registrar_uso_promo exitoso. */
        private void stubPromoUsoOk() {
                var q = mock(jakarta.persistence.Query.class);
                doReturn(q).when(em).createNativeQuery(anyString());
                doReturn(q).when(q).setParameter(anyString(), any());
                doReturn(1).when(q).getSingleResult();
        }

        private void stubPromoEvaluada(Long promoId, Promocion promo, boolean aplica, BigDecimal beneficio) {
                when(promocionRepo.findById(promoId)).thenReturn(Optional.of(promo));
                when(promocionService.evaluar(any())).thenReturn(List.of(evalResp(promoId, aplica, beneficio)));
        }

        // ── list ────────────────────────────────────────────────────────

        @Test
        @DisplayName("list sin filtros: findAll retorna pagina con items")
        void list_all() {
                Venta v = sampleVenta(1L, "V-001", "COMPLETADA");
                when(ventaRepo.findAll(pg())).thenReturn(new PageImpl<>(List.of(v), pg(), 1));
                stubToResponse();

                var result = service.list(null, null, null, pg());

                assertThat(result.getContent()).hasSize(1);
                assertThat(result.getContent().get(0).ventaId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("list vacia: pagina sin contenido retorna vacia")
        void list_empty() {
                when(ventaRepo.findAll(pg())).thenReturn(new PageImpl<>(List.of(), pg(), 0));

                var result = service.list(null, null, null, pg());

                assertThat(result.getContent()).isEmpty();
                assertThat(result.getTotalElements()).isZero();
        }

        @Test
        @DisplayName("list por almacen y rango: findByAlmacenIdAndFechaBetween retorna filtrado")
        void list_byAlmacen() {
                Instant desde = Instant.parse("2025-01-01T00:00:00Z");
                Instant hasta = Instant.parse("2025-12-31T23:59:59Z");
                Venta v = sampleVenta(1L, "V-001", "COMPLETADA");
                when(ventaRepo.findByAlmacenIdAndFechaBetweenOrderByFechaDesc(1, desde, hasta, pg()))
                                .thenReturn(new PageImpl<>(List.of(v), pg(), 1));
                stubToResponse();

                var result = service.list(1, desde, hasta, pg());

                assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("list por rango de fechas: findByFechaBetween retorna filtrado")
        void list_byDateRange() {
                Instant desde = Instant.parse("2025-01-01T00:00:00Z");
                Instant hasta = Instant.parse("2025-12-31T23:59:59Z");
                Venta v = sampleVenta(1L, "V-001", "COMPLETADA");
                when(ventaRepo.findByFechaBetweenOrderByFechaDesc(desde, hasta, pg()))
                                .thenReturn(new PageImpl<>(List.of(v), pg(), 1));
                stubToResponse();

                var result = service.list(null, desde, hasta, pg());

                assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("list batch: 2 ventas usa 6 queries fijas (no N+1)")
        void list_batch() {
                Venta v1 = sampleVenta(1L, "V-001", "COMPLETADA");
                Venta v2 = sampleVenta(2L, "V-002", "COMPLETADA");
                v2.setClienteId(2L);
                VentaDetalle d1 = sampleDetalle(10L, 1L, 1L);
                VentaDetalle d2 = sampleDetalle(11L, 2L, 1L);

                when(ventaRepo.findAll(pg())).thenReturn(new PageImpl<>(List.of(v1, v2), pg(), 2));
                when(clienteRepo.findAllById(any())).thenReturn(List.of(
                                Cliente.builder().clienteId(2L).razonSocial("Cliente 2").build()));
                when(almacenRepo.findAllById(any())).thenReturn(List.of(
                                Almacen.builder().almacenId(1).nombre("Almacen Central").build()));
                when(formaPagoRepo.findAllById(any())).thenReturn(List.of(
                                FormaPago.builder().formaPagoId(1).nombre("EFECTIVO").build()));
                when(detalleRepo.findByVentaIdIn(List.of(1L, 2L))).thenReturn(List.of(d1, d2));
                when(productoRepo.findAllById(any())).thenReturn(List.of(
                                Producto.builder().productoId(1L).nombre("Martillo").build()));
                when(cuentaRepo.findByVentaIdIn(List.of(1L, 2L))).thenReturn(List.of());
                when(pagoRepo.findByCuentaCobrarIdIn(any())).thenReturn(List.of());

                var result = service.list(null, null, null, pg());

                assertThat(result.getContent()).hasSize(2);
                assertThat(result.getContent().get(0).ventaId()).isEqualTo(1L);
                assertThat(result.getContent().get(1).ventaId()).isEqualTo(2L);
                // Batch: 1 call each, no per-item loop
                verify(clienteRepo).findAllById(any());
                verify(almacenRepo).findAllById(any());
                verify(formaPagoRepo).findAllById(any());
                verify(detalleRepo).findByVentaIdIn(List.of(1L, 2L));
                verify(productoRepo).findAllById(any());
                verify(cuentaRepo).findByVentaIdIn(List.of(1L, 2L));
        }

        @Test
        @DisplayName("list batch rico: cliente con ciudad, cuenta con pagos, nombres faltantes")
        void list_batchRich() {
                Venta v1 = sampleVenta(1L, "V-001", "COMPLETADA");
                Venta v2 = sampleVenta(2L, "V-002", "COMPLETADA");
                v2.setClienteId(5L);
                VentaDetalle d1 = sampleDetalle(10L, 1L, 1L);
                VentaDetalle d2 = sampleDetalle(11L, 2L, 9L); // producto 9 no existe
                CuentaCobrar cc = CuentaCobrar.builder().cuentaCobrarId(100L).ventaId(2L)
                                .montoTotal(new BigDecimal("116.00")).build();
                PagoCliente pagoSinFecha = PagoCliente.builder().pagoClienteId(1L).cuentaCobrarId(100L)
                                .formaPagoId(1).monto(new BigDecimal("50.00")).fecha(null).build();
                PagoCliente pagoConFecha = PagoCliente.builder().pagoClienteId(2L).cuentaCobrarId(100L)
                                .formaPagoId(1).monto(new BigDecimal("66.00"))
                                .fecha(Instant.parse("2025-06-01T10:00:00Z")).build();

                when(ventaRepo.findAll(pg())).thenReturn(new PageImpl<>(List.of(v1, v2), pg(), 2));
                when(clienteRepo.findAllById(any())).thenReturn(List.of(
                                Cliente.builder().clienteId(5L).razonSocial("ACME").ciudadId(10).build()));
                // ciudades del batch vacías -> fallback a ciudadRepo.findById por item
                when(ciudadRepo.findAllById(any())).thenReturn(List.of());
                when(ciudadRepo.findById(10))
                                .thenReturn(Optional.of(Ciudad.builder().ciudadId(10).nombre("Guadalajara").build()));
                when(almacenRepo.findAllById(any())).thenReturn(List.of(
                                Almacen.builder().almacenId(1).nombre("Almacen Central").build()));
                // formas de pago del batch vacías -> formaPagoNombre null
                when(formaPagoRepo.findAllById(any())).thenReturn(List.of());
                when(detalleRepo.findByVentaIdIn(List.of(1L, 2L))).thenReturn(List.of(d1, d2));
                when(productoRepo.findAllById(any())).thenReturn(List.of(
                                Producto.builder().productoId(1L).nombre("Martillo").build()));
                when(cuentaRepo.findByVentaIdIn(List.of(1L, 2L))).thenReturn(List.of(cc));
                when(pagoRepo.findByCuentaCobrarIdIn(List.of(100L)))
                                .thenReturn(List.of(pagoSinFecha, pagoConFecha));

                var result = service.list(null, null, null, pg());

                assertThat(result.getContent()).hasSize(2);
                var r1 = result.getContent().get(0);
                var r2 = result.getContent().get(1);
                assertThat(r1.cliente()).isNull();
                assertThat(r1.pagos()).isEmpty();
                assertThat(r1.almacenNombre()).isEqualTo("Almacen Central");
                assertThat(r2.clienteNombre()).isEqualTo("ACME");
                assertThat(r2.cliente().ciudadNombre()).isEqualTo("Guadalajara");
                assertThat(r2.formaPagoNombre()).isNull();
                assertThat(r2.pagos()).hasSize(2);
                assertThat(r2.detalles()).hasSize(1);
                assertThat(r2.detalles().get(0).productoNombre()).isNull();
                assertThat(r1.detalles().get(0).productoNombre()).isEqualTo("Martillo");
        }

        @Test
        @DisplayName("list batch sin relaciones: mapas vacios y nombres null")
        void list_batchSinRelaciones() {
                Venta v1 = sampleVenta(1L, "V-001", "COMPLETADA");
                Venta v2 = sampleVenta(2L, "V-002", "COMPLETADA");

                when(ventaRepo.findAll(pg())).thenReturn(new PageImpl<>(List.of(v1, v2), pg(), 2));
                // sin clientes -> no se consulta clienteRepo ni ciudadRepo
                when(almacenRepo.findAllById(any())).thenReturn(List.of());
                when(formaPagoRepo.findAllById(any())).thenReturn(List.of(
                                FormaPago.builder().formaPagoId(1).nombre("EFECTIVO").build()));
                // sin detalles -> no se consulta productoRepo
                when(detalleRepo.findByVentaIdIn(List.of(1L, 2L))).thenReturn(List.of());
                when(cuentaRepo.findByVentaIdIn(List.of(1L, 2L))).thenReturn(List.of());

                var result = service.list(null, null, null, pg());

                assertThat(result.getContent()).hasSize(2);
                assertThat(result.getContent().get(0).almacenNombre()).isNull();
                assertThat(result.getContent().get(0).detalles()).isEmpty();
                assertThat(result.getContent().get(0).pagos()).isEmpty();
                verify(clienteRepo, never()).findAllById(any());
                verify(ciudadRepo, never()).findAllById(any());
                verify(productoRepo, never()).findAllById(any());
        }

        @Test
        @DisplayName("list batch con ciudad en mapa: usa cache de ciudades del batch")
        void list_batchCiudadEnMapa() {
                Venta v1 = sampleVenta(1L, "V-001", "COMPLETADA");
                v1.setClienteId(5L);
                Venta v2 = sampleVenta(2L, "V-002", "COMPLETADA");
                v2.setClienteId(5L);

                when(ventaRepo.findAll(pg())).thenReturn(new PageImpl<>(List.of(v1, v2), pg(), 2));
                when(clienteRepo.findAllById(any())).thenReturn(List.of(
                                Cliente.builder().clienteId(5L).razonSocial("ACME").ciudadId(10).build()));
                when(ciudadRepo.findAllById(any())).thenReturn(List.of(
                                Ciudad.builder().ciudadId(10).nombre("Guadalajara").build()));
                when(almacenRepo.findAllById(any())).thenReturn(List.of(
                                Almacen.builder().almacenId(1).nombre("Almacen Central").build()));
                when(formaPagoRepo.findAllById(any())).thenReturn(List.of(
                                FormaPago.builder().formaPagoId(1).nombre("EFECTIVO").build()));
                when(detalleRepo.findByVentaIdIn(List.of(1L, 2L))).thenReturn(List.of());
                when(cuentaRepo.findByVentaIdIn(List.of(1L, 2L))).thenReturn(List.of());

                var result = service.list(null, null, null, pg());

                assertThat(result.getContent()).hasSize(2);
                assertThat(result.getContent().get(0).cliente().ciudadNombre()).isEqualTo("Guadalajara");
                verify(ciudadRepo, never()).findById(anyInt());
        }

        @Test
        @DisplayName("list solo almacen sin rango: ignora filtro y usa findAll")
        void list_almacenSinRango() {
                Venta v = sampleVenta(1L, "V-001", "COMPLETADA");
                when(ventaRepo.findAll(pg())).thenReturn(new PageImpl<>(List.of(v), pg(), 1));
                stubToResponse();

                var result = service.list(1, null, null, pg());

                assertThat(result.getContent()).hasSize(1);
                verify(ventaRepo, never()).findByAlmacenIdAndFechaBetweenOrderByFechaDesc(anyInt(), any(), any(),
                                any());
        }

        @Test
        @DisplayName("list con hasta null: ignora rango incompleto y usa findAll")
        void list_rangoIncompleto() {
                Instant desde = Instant.parse("2025-01-01T00:00:00Z");
                Venta v = sampleVenta(1L, "V-001", "COMPLETADA");
                when(ventaRepo.findAll(pg())).thenReturn(new PageImpl<>(List.of(v), pg(), 1));
                stubToResponse();

                var result = service.list(null, desde, null, pg());

                assertThat(result.getContent()).hasSize(1);
                verify(ventaRepo, never()).findByFechaBetweenOrderByFechaDesc(any(), any(), any());
        }

        @Test
        @DisplayName("list almacen con desde pero sin hasta: ignora rango incompleto")
        void list_almacenConDesdeSinHasta() {
                Instant desde = Instant.parse("2025-01-01T00:00:00Z");
                Venta v = sampleVenta(1L, "V-001", "COMPLETADA");
                when(ventaRepo.findAll(pg())).thenReturn(new PageImpl<>(List.of(v), pg(), 1));
                stubToResponse();

                var result = service.list(1, desde, null, pg());

                assertThat(result.getContent()).hasSize(1);
        }

        // ── listByFechaLocal ────────────────────────────────────────────
        @Test
        @DisplayName("listByFechaLocal sin filtros: findAll retorna pagina")
        void listByFechaLocal_all() {
                Venta v = sampleVenta(1L, "V-001", "COMPLETADA");
                when(ventaRepo.findAll(pg())).thenReturn(new PageImpl<>(List.of(v), pg(), 1));
                stubToResponse();

                var result = service.listByFechaLocal(null, null, null, pg());

                assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("listByFechaLocal por almacen y rango: usa fecha_local con almacen")
        void listByFechaLocal_byAlmacen() {
                LocalDate desde = LocalDate.parse("2025-01-01");
                LocalDate hasta = LocalDate.parse("2025-12-31");
                Venta v = sampleVenta(1L, "V-001", "COMPLETADA");
                when(ventaRepo.findByAlmacenIdAndFechaLocalBetweenOrderByFechaDesc(1, desde, hasta, pg()))
                                .thenReturn(new PageImpl<>(List.of(v), pg(), 1));
                stubToResponse();

                var result = service.listByFechaLocal(1, desde, hasta, pg());

                assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("listByFechaLocal solo almacen sin rango: ignora filtro y usa findAll")
        void listByFechaLocal_almacenSinRango() {
                Venta v = sampleVenta(1L, "V-001", "COMPLETADA");
                when(ventaRepo.findAll(pg())).thenReturn(new PageImpl<>(List.of(v), pg(), 1));
                stubToResponse();

                var result = service.listByFechaLocal(1, null, null, pg());

                assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("listByFechaLocal con hasta null: ignora rango incompleto y usa findAll")
        void listByFechaLocal_rangoIncompleto() {
                LocalDate desde = LocalDate.parse("2025-01-01");
                Venta v = sampleVenta(1L, "V-001", "COMPLETADA");
                when(ventaRepo.findAll(pg())).thenReturn(new PageImpl<>(List.of(v), pg(), 1));
                stubToResponse();

                var result = service.listByFechaLocal(null, desde, null, pg());

                assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("listByFechaLocal almacen con desde pero sin hasta: ignora rango incompleto")
        void listByFechaLocal_almacenConDesdeSinHasta() {
                LocalDate desde = LocalDate.parse("2025-01-01");
                Venta v = sampleVenta(1L, "V-001", "COMPLETADA");
                when(ventaRepo.findAll(pg())).thenReturn(new PageImpl<>(List.of(v), pg(), 1));
                stubToResponse();

                var result = service.listByFechaLocal(1, desde, null, pg());

                assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("listByFechaLocal por rango: usa fecha_local sin almacen")
        void listByFechaLocal_byRange() {
                LocalDate desde = LocalDate.parse("2025-01-01");
                LocalDate hasta = LocalDate.parse("2025-12-31");
                Venta v = sampleVenta(1L, "V-001", "COMPLETADA");
                when(ventaRepo.findByFechaLocalBetweenOrderByFechaDesc(desde, hasta, pg()))
                                .thenReturn(new PageImpl<>(List.of(v), pg(), 1));
                stubToResponse();

                var result = service.listByFechaLocal(null, desde, hasta, pg());

                assertThat(result.getContent()).hasSize(1);
        }

        // ── getById ─────────────────────────────────────────────────────

        @Test
        @DisplayName("getById encontrado: retorna VentaResponse con nombres resueltos")
        void getById_found() {
                Venta v = sampleVenta(1L, "V-001", "COMPLETADA");
                when(ventaRepo.findById(1L)).thenReturn(Optional.of(v));
                stubToResponse();

                var resp = service.getById(1L);

                assertThat(resp.ventaId()).isEqualTo(1L);
                assertThat(resp.almacenNombre()).isEqualTo("Almacen Central");
                assertThat(resp.formaPagoNombre()).isEqualTo("EFECTIVO");
        }

        @Test
        @DisplayName("getById rico: cliente con ciudad, cuenta con pagos, producto faltante")
        void getById_rich() {
                Venta v = sampleVenta(1L, "V-001", "COMPLETADA");
                v.setClienteId(5L);
                VentaDetalle d1 = sampleDetalle(10L, 1L, 1L);
                VentaDetalle d2 = sampleDetalle(11L, 1L, 2L); // producto 2 no existe
                CuentaCobrar cc = CuentaCobrar.builder().cuentaCobrarId(100L).ventaId(1L)
                                .montoTotal(new BigDecimal("116.00")).build();
                PagoCliente pago = PagoCliente.builder().pagoClienteId(7L).cuentaCobrarId(100L)
                                .formaPagoId(1).referencia("REF-1").monto(new BigDecimal("116.00"))
                                .fecha(Instant.parse("2025-06-01T10:00:00Z")).build();

                when(ventaRepo.findById(1L)).thenReturn(Optional.of(v));
                when(clienteRepo.findById(5L)).thenReturn(Optional.of(
                                Cliente.builder().clienteId(5L).razonSocial("ACME")
                                                .nombreComercial("ACME SA").rfc("ACM010101AAA")
                                                .ciudadId(10).build()));
                when(ciudadRepo.findById(10))
                                .thenReturn(Optional.of(Ciudad.builder().ciudadId(10).nombre("Guadalajara").build()));
                when(almacenRepo.findById(1)).thenReturn(Optional.of(
                                Almacen.builder().almacenId(1).nombre("Almacen Central").build()));
                when(formaPagoRepo.findById(1)).thenReturn(Optional.of(
                                FormaPago.builder().formaPagoId(1).nombre("EFECTIVO").build()));
                when(detalleRepo.findByVentaId(1L)).thenReturn(List.of(d1, d2));
                when(productoRepo.findById(1L)).thenReturn(Optional.of(
                                Producto.builder().productoId(1L).nombre("Martillo").build()));
                when(productoRepo.findById(2L)).thenReturn(Optional.empty());
                when(cuentaRepo.findByVentaId(1L)).thenReturn(Optional.of(cc));
                when(pagoRepo.findByCuentaCobrarIdOrderByFechaDesc(100L)).thenReturn(List.of(pago));

                var resp = service.getById(1L);

                assertThat(resp.clienteNombre()).isEqualTo("ACME");
                assertThat(resp.cliente().ciudadNombre()).isEqualTo("Guadalajara");
                assertThat(resp.cliente().rfc()).isEqualTo("ACM010101AAA");
                assertThat(resp.detalles()).hasSize(2);
                assertThat(resp.detalles().get(0).productoNombre()).isEqualTo("Martillo");
                assertThat(resp.detalles().get(1).productoNombre()).isNull();
                assertThat(resp.pagos()).hasSize(1);
                assertThat(resp.pagos().get(0).referencia()).isEqualTo("REF-1");
        }

        @Test
        @DisplayName("getById sin relaciones: nombres null cuando faltan catalogos")
        void getById_sinRelaciones() {
                Venta v = sampleVenta(1L, "V-001", "COMPLETADA");
                when(ventaRepo.findById(1L)).thenReturn(Optional.of(v));
                when(clienteRepo.findById(anyLong())).thenReturn(Optional.empty());
                when(almacenRepo.findById(1)).thenReturn(Optional.empty());
                when(formaPagoRepo.findById(1)).thenReturn(Optional.empty());
                when(detalleRepo.findByVentaId(1L)).thenReturn(List.of(sampleDetalle(10L, 1L, 1L)));
                when(productoRepo.findById(1L)).thenReturn(Optional.empty());
                when(cuentaRepo.findByVentaId(1L)).thenReturn(Optional.empty());

                var resp = service.getById(1L);

                assertThat(resp.cliente()).isNull();
                assertThat(resp.almacenNombre()).isNull();
                assertThat(resp.formaPagoNombre()).isNull();
                assertThat(resp.detalles().get(0).productoNombre()).isNull();
                assertThat(resp.pagos()).isEmpty();
        }

        @Test
        @DisplayName("getById inexistente: lanza RecursoNoEncontradoException")
        void getById_notFound() {
                when(ventaRepo.findById(999L)).thenReturn(Optional.empty());

                assertThatThrownBy(() -> service.getById(999L))
                                .isInstanceOf(RecursoNoEncontradoException.class)
                                .extracting("errorCode").isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO);
        }

        // ── checkout ────────────────────────────────────────────────────

        @Test
        @DisplayName("checkout ok: guarda venta, detalles y pagos")
        void checkout_ok() {
                Venta saved = sampleVenta(10L, "V-010", "COMPLETADA");
                stubCheckoutOk(saved);

                VenDtos.VentaRequest req = new VenDtos.VentaRequest(
                                1, null, null, null, 1,
                                List.of(new VenDtos.VentaDetalleRequest(1L, new BigDecimal("2.000"),
                                                new BigDecimal("50.00"))),
                                List.of(new VenDtos.PagoRequest(1, new BigDecimal("116.00"), null)),
                                null, null);

                var resp = service.checkout(req);

                assertThat(resp.ventaId()).isEqualTo(10L);
                verify(detalleRepo).save(any(VentaDetalle.class));
                verify(ventaRepo).flush();
                // Aviso al módulo notif vía evento de dominio (sin depender de él)
                var cap = org.mockito.ArgumentCaptor.forClass(Object.class);
                verify(events).publishEvent(cap.capture());
                assertThat(cap.getValue()).isInstanceOfSatisfying(VentaCreadaEvent.class,
                                e -> assertThat(e.ventaId()).isEqualTo(10L));
        }

        @Test
        @DisplayName("checkout con cliente y caja: resuelve turno y enriquece cliente")
        void checkout_conClienteYCaja() {
                Venta saved = sampleVenta(10L, "V-010", "COMPLETADA");
                saved.setClienteId(5L);
                stubCheckoutOk(saved);
                when(clienteRepo.existsById(5L)).thenReturn(true);
                when(cajaService.resolverTurnoAbierto(3, 1)).thenReturn(7L);
                when(clienteRepo.findById(5L)).thenReturn(Optional.of(
                                Cliente.builder().clienteId(5L).razonSocial("ACME").build()));
                when(almacenRepo.findById(1)).thenReturn(Optional.of(
                                Almacen.builder().almacenId(1).nombre("Almacen Central").build()));
                when(formaPagoRepo.findById(1)).thenReturn(Optional.of(
                                FormaPago.builder().formaPagoId(1).nombre("EFECTIVO").build()));

                var req = ventaRequest(1, 3, 5L, null, List.of(linea(1L, "2.000", "50.00")));

                var resp = service.checkout(req);

                assertThat(resp.ventaId()).isEqualTo(10L);
                assertThat(resp.clienteNombre()).isEqualTo("ACME");
                verify(cajaService).resolverTurnoAbierto(3, 1);
                verify(detalleRepo).save(any(VentaDetalle.class));
        }

        @Test
        @DisplayName("checkout almacen no encontrado: lanza RecursoNoEncontradoException")
        void checkout_almacenNotFound() {
                when(almacenRepo.existsById(99)).thenReturn(false);

                VenDtos.VentaRequest req = new VenDtos.VentaRequest(
                                99, null, null, null, 1,
                                List.of(new VenDtos.VentaDetalleRequest(1L, new BigDecimal("1.000"),
                                                new BigDecimal("10.00"))),
                                List.of(new VenDtos.PagoRequest(1, new BigDecimal("10.00"), null)),
                                null, null);

                assertThatThrownBy(() -> service.checkout(req))
                                .isInstanceOf(RecursoNoEncontradoException.class)
                                .extracting("errorCode").isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO);
                verify(detalleRepo, never()).save(any());
        }

        @Test
        @DisplayName("checkout forma de pago inexistente: lanza RecursoNoEncontradoException")
        void checkout_formaPagoNotFound() {
                when(almacenRepo.existsById(1)).thenReturn(true);
                when(formaPagoRepo.findById(1)).thenReturn(Optional.empty());

                var req = ventaRequest(1, null, null, null, List.of(linea(1L, "1.000", "10.00")));

                assertThatThrownBy(() -> service.checkout(req))
                                .isInstanceOf(RecursoNoEncontradoException.class)
                                .extracting("errorCode").isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO);
                verify(detalleRepo, never()).save(any());
        }

        @Test
        @DisplayName("checkout cliente inexistente: lanza RecursoNoEncontradoException")
        void checkout_clienteNotFound() {
                when(almacenRepo.existsById(1)).thenReturn(true);
                when(formaPagoRepo.findById(1)).thenReturn(Optional.of(
                                FormaPago.builder().formaPagoId(1).nombre("EFECTIVO").build()));
                when(clienteRepo.existsById(5L)).thenReturn(false);

                var req = ventaRequest(1, null, 5L, null, List.of(linea(1L, "1.000", "10.00")));

                assertThatThrownBy(() -> service.checkout(req))
                                .isInstanceOf(RecursoNoEncontradoException.class)
                                .extracting("errorCode").isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO);
                verify(detalleRepo, never()).save(any());
        }

        @Test
        @DisplayName("checkout sin recarga de triggers: lanza RecursoNoEncontradoException")
        void checkout_reloadNotFound() {
                Venta saved = sampleVenta(10L, "V-010", "COMPLETADA");
                when(almacenRepo.existsById(1)).thenReturn(true);
                when(formaPagoRepo.findById(1)).thenReturn(Optional.of(
                                FormaPago.builder().formaPagoId(1).nombre("EFECTIVO").build()));
                when(ventaRepo.save(any(Venta.class))).thenReturn(saved);
                when(ventaRepo.reloadAfterTriggers(10L)).thenReturn(Optional.empty());

                var req = ventaRequest(1, null, null, null, List.of(linea(1L, "1.000", "10.00")));

                assertThatThrownBy(() -> service.checkout(req))
                                .isInstanceOf(RecursoNoEncontradoException.class)
                                .extracting("errorCode").isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO);
                verify(events, never()).publishEvent(any());
        }

        // ── checkout con promoción ──────────────────────────────────────

        @Test
        @DisplayName("checkout promo inexistente: lanza RecursoNoEncontradoException")
        void checkout_promoNotFound() {
                when(almacenRepo.existsById(1)).thenReturn(true);
                when(formaPagoRepo.findById(1)).thenReturn(Optional.of(
                                FormaPago.builder().formaPagoId(1).nombre("EFECTIVO").build()));
                when(promocionRepo.findById(9L)).thenReturn(Optional.empty());

                var req = ventaRequest(1, null, null, 9L, List.of(linea(1L, "1.000", "10.00")));

                assertThatThrownBy(() -> service.checkout(req))
                                .isInstanceOf(RecursoNoEncontradoException.class)
                                .extracting("errorCode").isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO);
        }

        @Test
        @DisplayName("checkout promo sin match en evaluacion: lanza ValidacionException")
        void checkout_promoEvalSinMatch() {
                when(almacenRepo.existsById(1)).thenReturn(true);
                when(formaPagoRepo.findById(1)).thenReturn(Optional.of(
                                FormaPago.builder().formaPagoId(1).nombre("EFECTIVO").build()));
                Promocion promo = Promocion.builder().promocionId(9L).nombre("P9")
                                .tipo("DESCUENTO_TOTAL_VENTA").build();
                when(promocionRepo.findById(9L)).thenReturn(Optional.of(promo));
                // la evaluación no incluye la promo pedida
                when(promocionService.evaluar(any())).thenReturn(List.of(evalResp(77L, true, BigDecimal.TEN)));

                var req = ventaRequest(1, null, null, 9L, List.of(linea(1L, "1.000", "10.00")));

                assertThatThrownBy(() -> service.checkout(req))
                                .isInstanceOf(ValidacionException.class)
                                .extracting("errorCode").isEqualTo(ErrorCode.VALOR_INVALIDO);
        }

        @Test
        @DisplayName("checkout promo que no aplica: lanza ValidacionException")
        void checkout_promoNoAplica() {
                when(almacenRepo.existsById(1)).thenReturn(true);
                when(formaPagoRepo.findById(1)).thenReturn(Optional.of(
                                FormaPago.builder().formaPagoId(1).nombre("EFECTIVO").build()));
                Promocion promo = Promocion.builder().promocionId(9L).nombre("P9")
                                .tipo("DESCUENTO_TOTAL_VENTA").build();
                when(promocionRepo.findById(9L)).thenReturn(Optional.of(promo));
                when(promocionService.evaluar(any()))
                                .thenReturn(List.of(evalResp(9L, false, BigDecimal.TEN)));

                var req = ventaRequest(1, null, null, 9L, List.of(linea(1L, "1.000", "10.00")));

                assertThatThrownBy(() -> service.checkout(req))
                                .isInstanceOf(ValidacionException.class)
                                .extracting("errorCode").isEqualTo(ErrorCode.VALOR_INVALIDO);
        }

        @Test
        @DisplayName("checkout promo con beneficio null: se ignora la promo y vende ok")
        void checkout_promoBeneficioNull() {
                Venta saved = sampleVenta(10L, "V-010", "COMPLETADA");
                stubCheckoutOk(saved);
                Promocion promo = Promocion.builder().promocionId(9L).nombre("P9")
                                .tipo("DESCUENTO_TOTAL_VENTA").build();
                stubPromoEvaluada(9L, promo, true, null);

                var req = ventaRequest(1, null, null, 9L, List.of(linea(1L, "2.000", "50.00")));

                var resp = service.checkout(req);

                assertThat(resp.ventaId()).isEqualTo(10L);
                verify(em, never()).createNativeQuery(anyString());
        }

        @Test
        @DisplayName("checkout promo DESCUENTO_TOTAL_VENTA: prorratea por linea y registra uso")
        void checkout_promoDescuentoTotalVenta() {
                Venta saved = sampleVenta(10L, "V-010", "COMPLETADA");
                stubCheckoutOk(saved);
                Promocion promo = Promocion.builder().promocionId(9L).nombre("P9")
                                .tipo("DESCUENTO_TOTAL_VENTA").build();
                stubPromoEvaluada(9L, promo, true, new BigDecimal("10.00"));
                stubPromoUsoOk();

                var req = ventaRequest(1, null, null, 9L,
                                List.of(linea(1L, "1.000", "60.00"), linea(2L, "1.000", "40.00")));

                var resp = service.checkout(req);

                assertThat(resp.ventaId()).isEqualTo(10L);
                var cap = ArgumentCaptor.forClass(VentaDetalle.class);
                verify(detalleRepo, times(2)).save(cap.capture());
                // 60/100*10 = 6.00 y resto 4.00; ambas líneas ligadas a la promo
                assertThat(cap.getAllValues()).allSatisfy(d -> assertThat(d.getPromocionId()).isEqualTo(9L));
                assertThat(cap.getAllValues().stream().map(VentaDetalle::getDescuentoLinea)
                                .reduce(BigDecimal.ZERO, BigDecimal::add))
                                .isEqualByComparingTo(new BigDecimal("10.00"));
                verify(em).createNativeQuery(anyString());
        }

        @Test
        @DisplayName("checkout promo DESCUENTO_TOTAL_VENTA con total cero: sin descuento")
        void checkout_promoDescuentoTotalVentaTotalCero() {
                Venta saved = sampleVenta(10L, "V-010", "COMPLETADA");
                stubCheckoutOk(saved);
                Promocion promo = Promocion.builder().promocionId(9L).nombre("P9")
                                .tipo("DESCUENTO_TOTAL_VENTA").build();
                stubPromoEvaluada(9L, promo, true, new BigDecimal("5.00"));
                // Aunque el reparto da vacío (total cero), el servicio igual registra el uso
                stubPromoUsoOk();

                var req = ventaRequest(1, null, null, 9L, List.of(linea(1L, "1.000", "0.00")));

                var resp = service.checkout(req);

                assertThat(resp.ventaId()).isEqualTo(10L);
                var cap = ArgumentCaptor.forClass(VentaDetalle.class);
                verify(detalleRepo).save(cap.capture());
                assertThat(cap.getValue().getDescuentoLinea()).isEqualByComparingTo(BigDecimal.ZERO);
                assertThat(cap.getValue().getPromocionId()).isNull();
                verify(em).createNativeQuery(anyString());
        }

        @Test
        @DisplayName("checkout promo DESCUENTO_PRODUCTO pct: ajusta redondeo al beneficio")
        void checkout_promoDescuentoProductoPct() {
                Venta saved = sampleVenta(10L, "V-010", "COMPLETADA");
                stubCheckoutOk(saved);
                Promocion promo = Promocion.builder().promocionId(9L).nombre("P9")
                                .tipo("DESCUENTO_PRODUCTO").valorPct(new BigDecimal("10")).build();
                // 10% de 33.33 = 3.33 calculado vs 3.00 estimado -> ajuste a 3.00
                stubPromoEvaluada(9L, promo, true, new BigDecimal("3.00"));
                stubPromoUsoOk();

                var req = ventaRequest(1, null, null, 9L, List.of(linea(1L, "1.000", "33.33")));

                service.checkout(req);

                var cap = ArgumentCaptor.forClass(VentaDetalle.class);
                verify(detalleRepo).save(cap.capture());
                assertThat(cap.getValue().getDescuentoLinea()).isEqualByComparingTo(new BigDecimal("3.00"));
                assertThat(cap.getValue().getPromocionId()).isEqualTo(9L);
        }

        @Test
        @DisplayName("checkout promo DESCUENTO_PRODUCTO monto: usa valorMonto sin pct")
        void checkout_promoDescuentoProductoMonto() {
                Venta saved = sampleVenta(10L, "V-010", "COMPLETADA");
                stubCheckoutOk(saved);
                Promocion promo = Promocion.builder().promocionId(9L).nombre("P9")
                                .tipo("DESCUENTO_PRODUCTO").valorMonto(new BigDecimal("15.00")).build();
                stubPromoEvaluada(9L, promo, true, new BigDecimal("15.00"));
                stubPromoUsoOk();

                var req = ventaRequest(1, null, null, 9L, List.of(linea(1L, "1.000", "100.00")));

                service.checkout(req);

                var cap = ArgumentCaptor.forClass(VentaDetalle.class);
                verify(detalleRepo).save(cap.capture());
                assertThat(cap.getValue().getDescuentoLinea()).isEqualByComparingTo(new BigDecimal("15.00"));
        }

        @Test
        @DisplayName("checkout promo POR_CANTIDAD bajo minimo: linea sin descuento pero registra uso")
        void checkout_promoPorCantidadBajoMinimo() {
                Venta saved = sampleVenta(10L, "V-010", "COMPLETADA");
                stubCheckoutOk(saved);
                Promocion promo = Promocion.builder().promocionId(9L).nombre("P9")
                                .tipo("POR_CANTIDAD").compraMinCantidad(new BigDecimal("5"))
                                .valorPct(new BigDecimal("10")).build();
                stubPromoEvaluada(9L, promo, true, new BigDecimal("7.00"));
                stubPromoUsoOk();

                var req = ventaRequest(1, null, null, 9L, List.of(linea(1L, "2.000", "100.00")));

                service.checkout(req);

                var cap = ArgumentCaptor.forClass(VentaDetalle.class);
                verify(detalleRepo).save(cap.capture());
                assertThat(cap.getValue().getDescuentoLinea()).isEqualByComparingTo(BigDecimal.ZERO);
                assertThat(cap.getValue().getPromocionId()).isNull();
                verify(em).createNativeQuery(anyString());
        }

        @Test
        @DisplayName("checkout promo POR_CANTIDAD ok: aplica pct sobre la linea")
        void checkout_promoPorCantidadOk() {
                Venta saved = sampleVenta(10L, "V-010", "COMPLETADA");
                stubCheckoutOk(saved);
                Promocion promo = Promocion.builder().promocionId(9L).nombre("P9")
                                .tipo("POR_CANTIDAD").compraMinCantidad(new BigDecimal("5"))
                                .valorPct(new BigDecimal("10")).build();
                stubPromoEvaluada(9L, promo, true, new BigDecimal("50.00"));
                stubPromoUsoOk();

                var req = ventaRequest(1, null, null, 9L, List.of(linea(1L, "5.000", "100.00")));

                service.checkout(req);

                var cap = ArgumentCaptor.forClass(VentaDetalle.class);
                verify(detalleRepo).save(cap.capture());
                assertThat(cap.getValue().getDescuentoLinea()).isEqualByComparingTo(new BigDecimal("50.00"));
                assertThat(cap.getValue().getPromocionId()).isEqualTo(9L);
        }

        @Test
        @DisplayName("checkout promo POR_CANTIDAD monto: usa valorMonto sin pct")
        void checkout_promoPorCantidadMonto() {
                Venta saved = sampleVenta(10L, "V-010", "COMPLETADA");
                stubCheckoutOk(saved);
                Promocion promo = Promocion.builder().promocionId(9L).nombre("P9")
                                .tipo("POR_CANTIDAD").compraMinCantidad(new BigDecimal("5"))
                                .valorMonto(new BigDecimal("15.00")).build();
                stubPromoEvaluada(9L, promo, true, new BigDecimal("15.00"));
                stubPromoUsoOk();

                var req = ventaRequest(1, null, null, 9L, List.of(linea(1L, "5.000", "100.00")));

                service.checkout(req);

                var cap = ArgumentCaptor.forClass(VentaDetalle.class);
                verify(detalleRepo).save(cap.capture());
                assertThat(cap.getValue().getDescuentoLinea()).isEqualByComparingTo(new BigDecimal("15.00"));
        }

        @Test
        @DisplayName("checkout promo PRECIO_ESPECIAL: solo lineas con ahorro llevan descuento")
        void checkout_promoPrecioEspecial() {
                Venta saved = sampleVenta(10L, "V-010", "COMPLETADA");
                stubCheckoutOk(saved);
                Promocion promo = Promocion.builder().promocionId(9L).nombre("P9")
                                .tipo("PRECIO_ESPECIAL").precioEspecial(new BigDecimal("40.00")).build();
                // línea 1: (50-40)*2 = 20; línea 2: precio 30 < 40 -> ahorro 0
                stubPromoEvaluada(9L, promo, true, new BigDecimal("20.00"));
                stubPromoUsoOk();

                var req = ventaRequest(1, null, null, 9L,
                                List.of(linea(1L, "2.000", "50.00"), linea(2L, "1.000", "30.00")));

                service.checkout(req);

                var cap = ArgumentCaptor.forClass(VentaDetalle.class);
                verify(detalleRepo, times(2)).save(cap.capture());
                var porProducto = new java.util.HashMap<Long, VentaDetalle>();
                cap.getAllValues().forEach(d -> porProducto.put(d.getProductoId(), d));
                assertThat(porProducto.get(1L).getDescuentoLinea()).isEqualByComparingTo(new BigDecimal("20.00"));
                assertThat(porProducto.get(1L).getPromocionId()).isEqualTo(9L);
                assertThat(porProducto.get(2L).getDescuentoLinea()).isEqualByComparingTo(BigDecimal.ZERO);
                assertThat(porProducto.get(2L).getPromocionId()).isNull();
        }

        @Test
        @DisplayName("checkout promo POR_CANTIDAD sin minimo: aplica pct directo")
        void checkout_promoPorCantidadSinMinimo() {
                Venta saved = sampleVenta(10L, "V-010", "COMPLETADA");
                stubCheckoutOk(saved);
                Promocion promo = Promocion.builder().promocionId(9L).nombre("P9")
                                .tipo("POR_CANTIDAD").valorPct(new BigDecimal("10")).build();
                stubPromoEvaluada(9L, promo, true, new BigDecimal("50.00"));
                stubPromoUsoOk();

                var req = ventaRequest(1, null, null, 9L, List.of(linea(1L, "5.000", "100.00")));

                service.checkout(req);

                var cap = ArgumentCaptor.forClass(VentaDetalle.class);
                verify(detalleRepo).save(cap.capture());
                assertThat(cap.getValue().getDescuentoLinea()).isEqualByComparingTo(new BigDecimal("50.00"));
        }

        @Test
        @DisplayName("checkout promo PRECIO_ESPECIAL sin precio: ahorro es el precio completo")
        void checkout_promoPrecioEspecialSinPrecio() {
                Venta saved = sampleVenta(10L, "V-010", "COMPLETADA");
                stubCheckoutOk(saved);
                Promocion promo = Promocion.builder().promocionId(9L).nombre("P9")
                                .tipo("PRECIO_ESPECIAL").build();
                // pe null -> ZERO; ahorro = 50*2 = 100
                stubPromoEvaluada(9L, promo, true, new BigDecimal("100.00"));
                stubPromoUsoOk();

                var req = ventaRequest(1, null, null, 9L, List.of(linea(1L, "2.000", "50.00")));

                service.checkout(req);

                var cap = ArgumentCaptor.forClass(VentaDetalle.class);
                verify(detalleRepo).save(cap.capture());
                assertThat(cap.getValue().getDescuentoLinea()).isEqualByComparingTo(new BigDecimal("100.00"));
        }

        @Test
        @DisplayName("checkout promo NXM: lleva 2 paga 1 descuenta unidades gratis")
        void checkout_promoNxm() {
                Venta saved = sampleVenta(10L, "V-010", "COMPLETADA");
                stubCheckoutOk(saved);
                Promocion promo = Promocion.builder().promocionId(9L).nombre("P9")
                                .tipo("NXM").lleva(new BigDecimal("2")).paga(new BigDecimal("1")).build();
                // veces = 4/2 = 2; b = (2-1)*2*50 = 200
                stubPromoEvaluada(9L, promo, true, new BigDecimal("200.00"));
                stubPromoUsoOk();

                var req = ventaRequest(1, null, null, 9L, List.of(linea(1L, "4.000", "50.00")));

                service.checkout(req);

                var cap = ArgumentCaptor.forClass(VentaDetalle.class);
                verify(detalleRepo).save(cap.capture());
                assertThat(cap.getValue().getDescuentoLinea()).isEqualByComparingTo(new BigDecimal("200.00"));
                assertThat(cap.getValue().getPromocionId()).isEqualTo(9L);
        }

        @Test
        @DisplayName("checkout promo NXM sin lleva/paga: linea sin descuento")
        void checkout_promoNxmSinLleva() {
                Venta saved = sampleVenta(10L, "V-010", "COMPLETADA");
                stubCheckoutOk(saved);
                Promocion promo = Promocion.builder().promocionId(9L).nombre("P9")
                                .tipo("NXM").build();
                stubPromoEvaluada(9L, promo, true, new BigDecimal("5.00"));
                stubPromoUsoOk();

                var req = ventaRequest(1, null, null, 9L, List.of(linea(1L, "4.000", "50.00")));

                service.checkout(req);

                var cap = ArgumentCaptor.forClass(VentaDetalle.class);
                verify(detalleRepo).save(cap.capture());
                assertThat(cap.getValue().getDescuentoLinea()).isEqualByComparingTo(BigDecimal.ZERO);
                assertThat(cap.getValue().getPromocionId()).isNull();
        }

        @Test
        @DisplayName("checkout promo NXM sin veces completas: linea sin descuento")
        void checkout_promoNxmSinVeces() {
                Venta saved = sampleVenta(10L, "V-010", "COMPLETADA");
                stubCheckoutOk(saved);
                Promocion promo = Promocion.builder().promocionId(9L).nombre("P9")
                                .tipo("NXM").lleva(new BigDecimal("5")).paga(new BigDecimal("4")).build();
                stubPromoEvaluada(9L, promo, true, new BigDecimal("5.00"));
                stubPromoUsoOk();

                var req = ventaRequest(1, null, null, 9L, List.of(linea(1L, "2.000", "50.00")));

                service.checkout(req);

                var cap = ArgumentCaptor.forClass(VentaDetalle.class);
                verify(detalleRepo).save(cap.capture());
                assertThat(cap.getValue().getDescuentoLinea()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("checkout promo NXM sin paga: linea sin descuento")
        void checkout_promoNxmSinPaga() {
                Venta saved = sampleVenta(10L, "V-010", "COMPLETADA");
                stubCheckoutOk(saved);
                Promocion promo = Promocion.builder().promocionId(9L).nombre("P9")
                                .tipo("NXM").lleva(new BigDecimal("2")).build();
                stubPromoEvaluada(9L, promo, true, new BigDecimal("5.00"));
                stubPromoUsoOk();

                var req = ventaRequest(1, null, null, 9L, List.of(linea(1L, "4.000", "50.00")));

                service.checkout(req);

                var cap = ArgumentCaptor.forClass(VentaDetalle.class);
                verify(detalleRepo).save(cap.capture());
                assertThat(cap.getValue().getDescuentoLinea()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("checkout promo NXM con lleva cero: linea sin descuento")
        void checkout_promoNxmLlevaCero() {
                Venta saved = sampleVenta(10L, "V-010", "COMPLETADA");
                stubCheckoutOk(saved);
                Promocion promo = Promocion.builder().promocionId(9L).nombre("P9")
                                .tipo("NXM").lleva(BigDecimal.ZERO).paga(BigDecimal.ZERO).build();
                stubPromoEvaluada(9L, promo, true, new BigDecimal("5.00"));
                stubPromoUsoOk();

                var req = ventaRequest(1, null, null, 9L, List.of(linea(1L, "4.000", "50.00")));

                service.checkout(req);

                var cap = ArgumentCaptor.forClass(VentaDetalle.class);
                verify(detalleRepo).save(cap.capture());
                assertThat(cap.getValue().getDescuentoLinea()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("checkout promo DESCUENTO_TOTAL_VENTA con linea en cero: esa linea no entra al reparto")
        void checkout_promoDescuentoTotalVentaLineaCero() {
                Venta saved = sampleVenta(10L, "V-010", "COMPLETADA");
                stubCheckoutOk(saved);
                Promocion promo = Promocion.builder().promocionId(9L).nombre("P9")
                                .tipo("DESCUENTO_TOTAL_VENTA").build();
                stubPromoEvaluada(9L, promo, true, new BigDecimal("10.00"));
                stubPromoUsoOk();

                var req = ventaRequest(1, null, null, 9L,
                                List.of(linea(1L, "1.000", "60.00"), linea(2L, "1.000", "40.00"),
                                                linea(3L, "1.000", "0.00")));

                service.checkout(req);

                var cap = ArgumentCaptor.forClass(VentaDetalle.class);
                verify(detalleRepo, times(3)).save(cap.capture());
                var porProducto = new java.util.HashMap<Long, VentaDetalle>();
                cap.getAllValues().forEach(d -> porProducto.put(d.getProductoId(), d));
                assertThat(porProducto.get(3L).getDescuentoLinea()).isEqualByComparingTo(BigDecimal.ZERO);
                assertThat(porProducto.get(3L).getPromocionId()).isNull();
                assertThat(porProducto.get(1L).getDescuentoLinea()).isEqualByComparingTo(new BigDecimal("6.00"));
                assertThat(porProducto.get(2L).getDescuentoLinea()).isEqualByComparingTo(new BigDecimal("4.00"));
        }

        @Test
        @DisplayName("checkout promo tipo desconocido: linea sin descuento")
        void checkout_promoTipoDesconocido() {
                Venta saved = sampleVenta(10L, "V-010", "COMPLETADA");
                stubCheckoutOk(saved);
                Promocion promo = Promocion.builder().promocionId(9L).nombre("P9")
                                .tipo("CUPON_MAGICO").build();
                stubPromoEvaluada(9L, promo, true, new BigDecimal("5.00"));
                stubPromoUsoOk();

                var req = ventaRequest(1, null, null, 9L, List.of(linea(1L, "1.000", "100.00")));

                service.checkout(req);

                var cap = ArgumentCaptor.forClass(VentaDetalle.class);
                verify(detalleRepo).save(cap.capture());
                assertThat(cap.getValue().getDescuentoLinea()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("checkout promo con producto duplicado: acumula descuento por producto")
        void checkout_promoProductoDuplicado() {
                Venta saved = sampleVenta(10L, "V-010", "COMPLETADA");
                stubCheckoutOk(saved);
                Promocion promo = Promocion.builder().promocionId(9L).nombre("P9")
                                .tipo("DESCUENTO_TOTAL_VENTA").build();
                stubPromoEvaluada(9L, promo, true, new BigDecimal("10.00"));
                stubPromoUsoOk();

                var req = ventaRequest(1, null, null, 9L,
                                List.of(linea(1L, "1.000", "50.00"), linea(1L, "1.000", "50.00")));

                service.checkout(req);

                var cap = ArgumentCaptor.forClass(VentaDetalle.class);
                verify(detalleRepo, times(2)).save(cap.capture());
                // OJO: el reparto va por productoId, con duplicados ambas líneas reciben
                // el acumulado completo (10.00 cada una); el servicio lo documenta como
                // caso no típico en POS (agrupa). Se aserta el comportamiento real.
                assertThat(cap.getAllValues()).allSatisfy(d -> {
                        assertThat(d.getDescuentoLinea()).isEqualByComparingTo(new BigDecimal("10.00"));
                        assertThat(d.getPromocionId()).isEqualTo(9L);
                });
        }

        @Test
        @DisplayName("checkout promo con descuento mayor al importe: clamp al importe de linea")
        void checkout_promoClampImporte() {
                Venta saved = sampleVenta(10L, "V-010", "COMPLETADA");
                stubCheckoutOk(saved);
                Promocion promo = Promocion.builder().promocionId(9L).nombre("P9")
                                .tipo("DESCUENTO_PRODUCTO").valorMonto(new BigDecimal("500.00")).build();
                stubPromoEvaluada(9L, promo, true, new BigDecimal("500.00"));
                stubPromoUsoOk();

                var req = ventaRequest(1, null, null, 9L, List.of(linea(1L, "1.000", "100.00")));

                service.checkout(req);

                var cap = ArgumentCaptor.forClass(VentaDetalle.class);
                verify(detalleRepo).save(cap.capture());
                assertThat(cap.getValue().getDescuentoLinea()).isEqualByComparingTo(new BigDecimal("100.00"));
                assertThat(cap.getValue().getPromocionId()).isEqualTo(9L);
        }

        @Test
        @DisplayName("checkout promo con registro concurrente: lanza ReglaNegocioException")
        void checkout_promoRegistroFalla() {
                Venta saved = sampleVenta(10L, "V-010", "COMPLETADA");
                stubCheckoutOk(saved);
                Promocion promo = Promocion.builder().promocionId(9L).nombre("P9")
                                .tipo("DESCUENTO_TOTAL_VENTA").build();
                stubPromoEvaluada(9L, promo, true, new BigDecimal("10.00"));
                var q = mock(jakarta.persistence.Query.class);
                doReturn(q).when(em).createNativeQuery(anyString());
                doReturn(q).when(q).setParameter(anyString(), any());
                when(q.getSingleResult()).thenThrow(new RuntimeException("P0400"));

                var req = ventaRequest(1, null, null, 9L,
                                List.of(linea(1L, "1.000", "60.00"), linea(2L, "1.000", "40.00")));

                assertThatThrownBy(() -> service.checkout(req))
                                .isInstanceOf(ReglaNegocioException.class)
                                .extracting("errorCode").isEqualTo(ErrorCode.REGISTRO_NO_MODIFICABLE);
                verify(events, never()).publishEvent(any());
        }

        @Test
        @DisplayName("checkout promo con P0400 real: traduce a PROMOCION_AGOTADA")
        void checkout_promoAgotada_traduceSqlState() {
                Venta saved = sampleVenta(10L, "V-010", "COMPLETADA");
                stubCheckoutOk(saved);
                Promocion promo = Promocion.builder().promocionId(9L).nombre("P9")
                                .tipo("DESCUENTO_TOTAL_VENTA").build();
                stubPromoEvaluada(9L, promo, true, new BigDecimal("10.00"));
                var q = mock(jakarta.persistence.Query.class);
                doReturn(q).when(em).createNativeQuery(anyString());
                doReturn(q).when(q).setParameter(anyString(), any());
                // SQLSTATE P0400 en la cadena de causas, como lo lanza PostgreSQL.
                when(q.getSingleResult()).thenThrow(new jakarta.persistence.PersistenceException(
                                new java.sql.SQLException("Promocion agotada", "P0400")));
                // Traductor real (no mock) para probar la especificidad de punta a punta.
                org.springframework.test.util.ReflectionTestUtils.setField(service, "dbTranslator",
                                new mx.ferreteria.api.common.error.DbErrorTranslator());

                var req = ventaRequest(1, null, null, 9L,
                                List.of(linea(1L, "1.000", "60.00"), linea(2L, "1.000", "40.00")));

                assertThatThrownBy(() -> service.checkout(req))
                                .isInstanceOf(ReglaNegocioException.class)
                                .extracting("errorCode").isEqualTo(ErrorCode.PROMOCION_AGOTADA);
                verify(events, never()).publishEvent(any());
        }

        // ── ticketPdf ───────────────────────────────────────────────────

        @Test
        @DisplayName("ticketPdf ok: delega al generador y retorna bytes")
        void ticketPdf_ok() {
                when(ventaRepo.existsById(1L)).thenReturn(true);
                byte[] pdf = new byte[] { 1, 2, 3 };
                when(ticketPdfService.generarTicketPdf(1L)).thenReturn(pdf);

                assertThat(service.ticketPdf(1L)).isEqualTo(pdf);
        }

        @Test
        @DisplayName("ticketPdf inexistente: lanza RecursoNoEncontradoException")
        void ticketPdf_notFound() {
                when(ventaRepo.existsById(99L)).thenReturn(false);

                assertThatThrownBy(() -> service.ticketPdf(99L))
                                .isInstanceOf(RecursoNoEncontradoException.class)
                                .extracting("errorCode").isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO);
                verify(ticketPdfService, never()).generarTicketPdf(anyLong());
        }

        // ── ticket-whatsapp ─────────────────────────────────────────────

        @Test
        @DisplayName("enviarTicketWhatsapp ok: delega al puerto y responde enviado")
        void ticketWhatsapp_ok() {
                when(ventaRepo.existsById(1L)).thenReturn(true);

                var r = service.enviarTicketWhatsapp(1L, "5215500000001");

                assertThat(r.enviado()).isTrue();
                verify(ticketPort).enviarWhatsapp(1L, "5215500000001");
        }

        @Test
        @DisplayName("enviarTicketWhatsapp delega la existencia+envío al puerto (404 único)")
        void ticketWhatsapp_delegaExistencia() {
                var r = service.enviarTicketWhatsapp(99L, "5215500000001");

                assertThat(r.enviado()).isTrue();
                verify(ticketPort).enviarWhatsapp(99L, "5215500000001");
                verify(ticketPdfService, never()).generarTicketPdf(anyLong());
        }

        // ── cancel ──────────────────────────────────────────────────────

        @Test
        @DisplayName("cancel ok: venta activa se marca como CANCELADA")
        void cancel_ok() {
                Venta v = sampleVenta(1L, "V-001", "COMPLETADA");
                when(ventaRepo.findById(1L)).thenReturn(Optional.of(v));
                stubToResponse();

                var resp = service.cancel(1L, "Cliente solicitó");

                assertThat(v.getEstado()).isEqualTo("CANCELADA");
                assertThat(v.getMotivoCancelacion()).isEqualTo("Cliente solicitó");
                verify(ventaRepo).save(v);
                assertThat(resp.estado()).isEqualTo("CANCELADA");
                assertThat(resp.motivoCancelacion()).isEqualTo("Cliente solicitó");
        }

        @Test
        @DisplayName("cancel ya cancelada: lanza ReglaNegocioException")
        void cancel_alreadyCancelled() {
                Venta v = sampleVenta(1L, "V-001", "CANCELADA");
                when(ventaRepo.findById(1L)).thenReturn(Optional.of(v));

                assertThatThrownBy(() -> service.cancel(1L, "Motivo"))
                                .isInstanceOf(ReglaNegocioException.class)
                                .extracting("errorCode").isEqualTo(ErrorCode.REGISTRO_DUPLICADO);
                verify(ventaRepo, never()).save(any());
        }

        @Test
        @DisplayName("cancel inexistente: lanza RecursoNoEncontradoException")
        void cancel_notFound() {
                when(ventaRepo.findById(999L)).thenReturn(Optional.empty());

                assertThatThrownBy(() -> service.cancel(999L, "Motivo"))
                                .isInstanceOf(RecursoNoEncontradoException.class)
                                .extracting("errorCode").isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO);
        }
}
