package mx.ferreteria.api.inv.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.ArgumentCaptor;
import org.mockito.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import mx.ferreteria.api.cat.entity.MotivoMovimiento;
import mx.ferreteria.api.cat.entity.Producto;
import mx.ferreteria.api.cat.repo.MotivoMovimientoRepository;
import mx.ferreteria.api.cat.repo.ProductoRepository;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.inv.dto.InvDtos.TrasladoDetalleRequest;
import mx.ferreteria.api.inv.dto.InvDtos.TrasladoRequest;
import mx.ferreteria.api.inv.dto.InvDtos.TrasladoResponse;
import mx.ferreteria.api.inv.entity.Almacen;
import mx.ferreteria.api.inv.entity.MovimientoInventario;
import mx.ferreteria.api.inv.entity.Traslado;
import mx.ferreteria.api.inv.entity.TrasladoDetalle;
import mx.ferreteria.api.inv.repo.AlmacenRepository;
import mx.ferreteria.api.inv.repo.MovimientoInventarioRepository;
import mx.ferreteria.api.inv.repo.TrasladoDetalleRepository;
import mx.ferreteria.api.inv.repo.TrasladoRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TrasladoServiceTest {

        @Mock
        TrasladoRepository repo;

        @Mock
        TrasladoDetalleRepository detalleRepo;

        @Mock
        MovimientoInventarioRepository movimientoRepo;

        @Mock
        AlmacenRepository almacenRepo;

        @Mock
        ProductoRepository productoRepo;

        @Mock
        MotivoMovimientoRepository motivoRepo;

        @Spy
        @InjectMocks
        TrasladoService service;

        private Producto sampleProducto(Long id, String nombre) {
                return Producto.builder().productoId(id).codigo("P" + id).nombre(nombre).build();
        }

        private Almacen sampleAlmacen(Integer id, String nombre) {
                return Almacen.builder().almacenId(id).nombre(nombre).build();
        }

        private Traslado sampleTraslado(Long id, Integer origen, Integer destino) {
                return Traslado.builder()
                                .trasladoId(id)
                                .folio("TR-" + id)
                                .almacenOrigen(origen)
                                .almacenDestino(destino)
                                .estado("APLICADO")
                                .usuarioId(1)
                                .build();
        }

        private TrasladoDetalle sampleDetalle(Long trasladoId, Long productoId, BigDecimal cantidad) {
                return TrasladoDetalle.builder()
                                .trasladoId(trasladoId)
                                .productoId(productoId)
                                .cantidad(cantidad)
                                .build();
        }

        // ── list ────────────────────────────────────────────────────────

        @Test
        @DisplayName("list: retorna pagina de traslados con detalles")
        void list_returnsPage() {
                Pageable pg = PageRequest.of(0, 10);
                Traslado t = sampleTraslado(1L, 1, 2);
                when(repo.findAllByOrderByCreadoEnDesc(pg))
                                .thenReturn(new PageImpl<>(List.of(t), pg, 1));
                when(detalleRepo.findByTrasladoId(1L))
                                .thenReturn(List.of(sampleDetalle(1L, 1L, new BigDecimal("20.000"))));
                when(almacenRepo.findById(1)).thenReturn(Optional.of(sampleAlmacen(1, "Origen")));
                when(almacenRepo.findById(2)).thenReturn(Optional.of(sampleAlmacen(2, "Destino")));
                when(productoRepo.findAllById(List.of(1L)))
                                .thenReturn(List.of(sampleProducto(1L, "Tornillo")));

                var result = service.list(pg);

                assertThat(result.getContent()).hasSize(1);
                TrasladoResponse resp = result.getContent().get(0);
                assertThat(resp.almacenOrigenNombre()).isEqualTo("Origen");
                assertThat(resp.almacenDestinoNombre()).isEqualTo("Destino");
                assertThat(resp.detalles()).hasSize(1);
        }

        // ── getById ─────────────────────────────────────────────────────

        @Test
        @DisplayName("getById: retorna traslado con detalles")
        void getById_returnsTraslado() {
                Traslado t = sampleTraslado(1L, 1, 2);
                when(repo.findById(1L)).thenReturn(Optional.of(t));
                when(detalleRepo.findByTrasladoId(1L))
                                .thenReturn(List.of(sampleDetalle(1L, 1L, new BigDecimal("10.000"))));
                when(almacenRepo.findById(1)).thenReturn(Optional.of(sampleAlmacen(1, "Origen")));
                when(almacenRepo.findById(2)).thenReturn(Optional.of(sampleAlmacen(2, "Destino")));
                when(productoRepo.findAllById(List.of(1L)))
                                .thenReturn(List.of(sampleProducto(1L, "Clavo")));

                TrasladoResponse resp = service.getById(1L);

                assertThat(resp.trasladoId()).isEqualTo(1L);
                assertThat(resp.folio()).startsWith("TR-");
                assertThat(resp.detalles()).hasSize(1);
                assertThat(resp.detalles().get(0).productoNombre()).isEqualTo("Clavo");
        }

        // ── create ──────────────────────────────────────────────────────

        @Test
        @DisplayName("create ok: guarda traslado + detalles + 2 movimientos por detalle")
        void create_ok() {
                TrasladoRequest req = new TrasladoRequest(1, 2,
                                List.of(new TrasladoDetalleRequest(1L, new BigDecimal("10.000"))));

                when(almacenRepo.findById(1)).thenReturn(Optional.of(sampleAlmacen(1, "Origen")));
                when(almacenRepo.findById(2)).thenReturn(Optional.of(sampleAlmacen(2, "Destino")));
                when(productoRepo.findAllById(Set.of(1L)))
                                .thenReturn(List.of(sampleProducto(1L, "Tornillo")));

                Traslado savedTraslado = sampleTraslado(1L, 1, 2);
                when(repo.save(any(Traslado.class))).thenReturn(savedTraslado);
                when(repo.findFolioById(1L)).thenReturn("TR-0001");
                when(motivoRepo.findByClave("TRASLADO_SALIDA"))
                                .thenReturn(Optional.of(MotivoMovimiento.builder().motivoId(1).clave("TRASLADO_SALIDA")
                                                .tipoDefault("SALIDA").nombre("Salida por traslado").build()));
                when(motivoRepo.findByClave("TRASLADO_ENTRADA"))
                                .thenReturn(Optional.of(MotivoMovimiento.builder().motivoId(2).clave("TRASLADO_ENTRADA")
                                                .tipoDefault("ENTRADA").nombre("Entrada por traslado").build()));
                when(detalleRepo.findByTrasladoId(1L))
                                .thenReturn(List.of(sampleDetalle(1L, 1L, new BigDecimal("10.000"))));

                TrasladoResponse resp = service.create(req);

                assertThat(resp.trasladoId()).isEqualTo(1L);
                assertThat(resp.almacenOrigen()).isEqualTo(1);
                assertThat(resp.almacenDestino()).isEqualTo(2);
                verify(repo).save(any(Traslado.class));
                verify(detalleRepo).saveAll(any());
                @SuppressWarnings("unchecked")
                org.mockito.ArgumentCaptor<List<MovimientoInventario>> movCaptor = org.mockito.ArgumentCaptor
                                .forClass(List.class);
                verify(movimientoRepo).saveAll(movCaptor.capture());
                assertThat(movCaptor.getValue()).hasSize(2);
        }

        @Test
        @DisplayName("create mismo almacen origen y destino: ReglaNegocioException")
        void create_sameWarehouse() {
                TrasladoRequest req = new TrasladoRequest(1, 1,
                                List.of(new TrasladoDetalleRequest(1L, new BigDecimal("5.000"))));

                assertThatThrownBy(() -> service.create(req))
                                .isInstanceOf(ReglaNegocioException.class)
                                .satisfies(e -> assertThat(((ReglaNegocioException) e).errorCode())
                                                .isEqualTo(ErrorCode.VALOR_INVALIDO));
        }

        @Test
        @DisplayName("create producto inexistente: RecursoNoEncontradoException")
        void create_productNotFound() {
                TrasladoRequest req = new TrasladoRequest(1, 2,
                                List.of(new TrasladoDetalleRequest(999L, new BigDecimal("5.000"))));

                when(almacenRepo.findById(1)).thenReturn(Optional.of(sampleAlmacen(1, "Origen")));
                when(almacenRepo.findById(2)).thenReturn(Optional.of(sampleAlmacen(2, "Destino")));
                when(productoRepo.findAllById(Set.of(999L))).thenReturn(List.of());

                assertThatThrownBy(() -> service.create(req))
                                .isInstanceOf(RecursoNoEncontradoException.class)
                                .satisfies(e -> assertThat(((RecursoNoEncontradoException) e).errorCode())
                                                .isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO));
        }

        // ── list: página vacía ──────────────────────────────────────────

        @Test
        @DisplayName("list vacía: retorna página vacía sin consultar detalles")
        void list_emptyPage() {
                Pageable pg = PageRequest.of(0, 10);
                when(repo.findAllByOrderByCreadoEnDesc(pg))
                                .thenReturn(new PageImpl<>(List.of(), pg, 0));

                var result = service.list(pg);

                assertThat(result.getContent()).isEmpty();
                assertThat(result.getTotalElements()).isZero();
        }

        // ── list: rama batch (2+ traslados) ─────────────────────────────

        @Test
        @DisplayName("list multi: resuelve almacenes y productos en batch")
        void list_multiBatch() {
                Pageable pg = PageRequest.of(0, 10);
                Traslado t1 = sampleTraslado(1L, 1, 2);
                Traslado t2 = sampleTraslado(2L, 2, 3);
                when(repo.findAllByOrderByCreadoEnDesc(pg))
                                .thenReturn(new PageImpl<>(List.of(t1, t2), pg, 2));
                when(detalleRepo.findByTrasladoIdIn(List.of(1L, 2L))).thenReturn(List.of(
                                sampleDetalle(1L, 10L, new BigDecimal("5.000")),
                                sampleDetalle(2L, 20L, new BigDecimal("7.500"))));
                when(almacenRepo.findAllById(Set.of(1, 2, 3))).thenReturn(List.of(
                                sampleAlmacen(1, "Norte"), sampleAlmacen(2, "Sur"),
                                sampleAlmacen(3, "Este")));
                when(productoRepo.findAllById(Set.of(10L, 20L))).thenReturn(List.of(
                                sampleProducto(10L, "Tornillo"), sampleProducto(20L, "Clavo")));

                var result = service.list(pg);

                assertThat(result.getContent()).hasSize(2);
                assertThat(result.getTotalElements()).isEqualTo(2);
                TrasladoResponse r1 = result.getContent().get(0);
                assertThat(r1.almacenOrigenNombre()).isEqualTo("Norte");
                assertThat(r1.almacenDestinoNombre()).isEqualTo("Sur");
                assertThat(r1.detalles()).hasSize(1);
                assertThat(r1.detalles().get(0).productoNombre()).isEqualTo("Tornillo");
                TrasladoResponse r2 = result.getContent().get(1);
                assertThat(r2.almacenOrigenNombre()).isEqualTo("Sur");
                assertThat(r2.almacenDestinoNombre()).isEqualTo("Este");
                assertThat(r2.detalles()).hasSize(1);
                assertThat(r2.detalles().get(0).productoNombre()).isEqualTo("Clavo");
        }

        @Test
        @DisplayName("list multi: almacenes ausentes en batch dejan nombres en null")
        void list_multiMissingAlmacenes_nullNames() {
                Pageable pg = PageRequest.of(0, 10);
                Traslado t1 = sampleTraslado(1L, 99, 98);
                Traslado t2 = sampleTraslado(2L, 1, 2);
                when(repo.findAllByOrderByCreadoEnDesc(pg))
                                .thenReturn(new PageImpl<>(List.of(t1, t2), pg, 2));
                when(detalleRepo.findByTrasladoIdIn(List.of(1L, 2L))).thenReturn(List.of());
                when(almacenRepo.findAllById(Set.of(99, 98, 1, 2))).thenReturn(List.of(
                                sampleAlmacen(1, "Norte"), sampleAlmacen(2, "Sur")));

                var result = service.list(pg);

                assertThat(result.getContent()).hasSize(2);
                assertThat(result.getContent().get(0).almacenOrigenNombre()).isNull();
                assertThat(result.getContent().get(0).almacenDestinoNombre()).isNull();
                assertThat(result.getContent().get(0).detalles()).isEmpty();
                assertThat(result.getContent().get(1).almacenOrigenNombre()).isEqualTo("Norte");
                assertThat(result.getContent().get(1).almacenDestinoNombre()).isEqualTo("Sur");
        }

        @Test
        @DisplayName("list multi: producto ausente en batch deja nombre en null")
        void list_multiMissingProducto_nullName() {
                Pageable pg = PageRequest.of(0, 10);
                Traslado t1 = sampleTraslado(1L, 1, 2);
                Traslado t2 = sampleTraslado(2L, 1, 2);
                when(repo.findAllByOrderByCreadoEnDesc(pg))
                                .thenReturn(new PageImpl<>(List.of(t1, t2), pg, 2));
                when(detalleRepo.findByTrasladoIdIn(List.of(1L, 2L))).thenReturn(List.of(
                                sampleDetalle(1L, 10L, new BigDecimal("5.000"))));
                when(almacenRepo.findAllById(Set.of(1, 2))).thenReturn(List.of(
                                sampleAlmacen(1, "Norte"), sampleAlmacen(2, "Sur")));
                when(productoRepo.findAllById(Set.of(10L))).thenReturn(List.of());

                var result = service.list(pg);

                assertThat(result.getContent()).hasSize(2);
                assertThat(result.getContent().get(0).detalles()).hasSize(1);
                assertThat(result.getContent().get(0).detalles().get(0).productoNombre()).isNull();
                assertThat(result.getContent().get(1).detalles()).isEmpty();
        }

        // ── getById: ramas de error ─────────────────────────────────────

        @Test
        @DisplayName("getById inexistente: RecursoNoEncontradoException RECURSO_NO_ENCONTRADO")
        void getById_notFound() {
                when(repo.findById(999L)).thenReturn(Optional.empty());

                assertThatThrownBy(() -> service.getById(999L))
                                .isInstanceOf(RecursoNoEncontradoException.class)
                                .satisfies(e -> assertThat(((RecursoNoEncontradoException) e).errorCode())
                                                .isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO));
        }

        @Test
        @DisplayName("getById sin detalles: retorna respuesta con detalles vacios")
        void getById_noDetalles() {
                Traslado t = sampleTraslado(1L, 1, 2);
                when(repo.findById(1L)).thenReturn(Optional.of(t));
                when(detalleRepo.findByTrasladoId(1L)).thenReturn(List.of());
                when(almacenRepo.findById(1)).thenReturn(Optional.of(sampleAlmacen(1, "Origen")));
                when(almacenRepo.findById(2)).thenReturn(Optional.of(sampleAlmacen(2, "Destino")));

                TrasladoResponse resp = service.getById(1L);

                assertThat(resp.trasladoId()).isEqualTo(1L);
                assertThat(resp.detalles()).isEmpty();
        }

        @Test
        @DisplayName("getById con almacenes y producto ausentes: nombres en null")
        void getById_missingAlmacenAndProducto_nullNames() {
                Traslado t = sampleTraslado(1L, 1, 2);
                when(repo.findById(1L)).thenReturn(Optional.of(t));
                when(detalleRepo.findByTrasladoId(1L)).thenReturn(List.of(
                                sampleDetalle(1L, 10L, new BigDecimal("3.000"))));
                when(almacenRepo.findById(1)).thenReturn(Optional.empty());
                when(almacenRepo.findById(2)).thenReturn(Optional.empty());
                when(productoRepo.findAllById(List.of(10L))).thenReturn(List.of());

                TrasladoResponse resp = service.getById(1L);

                assertThat(resp.almacenOrigenNombre()).isNull();
                assertThat(resp.almacenDestinoNombre()).isNull();
                assertThat(resp.detalles()).hasSize(1);
                assertThat(resp.detalles().get(0).productoNombre()).isNull();
        }

        // ── create: ramas de error ──────────────────────────────────────

        @Test
        @DisplayName("create origen inexistente: RecursoNoEncontradoException")
        void create_origenNotFound() {
                TrasladoRequest req = new TrasladoRequest(1, 2,
                                List.of(new TrasladoDetalleRequest(1L, new BigDecimal("5.000"))));
                when(almacenRepo.findById(1)).thenReturn(Optional.empty());

                assertThatThrownBy(() -> service.create(req))
                                .isInstanceOf(RecursoNoEncontradoException.class)
                                .satisfies(e -> assertThat(((RecursoNoEncontradoException) e).errorCode())
                                                .isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO));
        }

        @Test
        @DisplayName("create destino inexistente: RecursoNoEncontradoException")
        void create_destinoNotFound() {
                TrasladoRequest req = new TrasladoRequest(1, 2,
                                List.of(new TrasladoDetalleRequest(1L, new BigDecimal("5.000"))));
                when(almacenRepo.findById(1)).thenReturn(Optional.of(sampleAlmacen(1, "Origen")));
                when(almacenRepo.findById(2)).thenReturn(Optional.empty());

                assertThatThrownBy(() -> service.create(req))
                                .isInstanceOf(RecursoNoEncontradoException.class)
                                .satisfies(e -> assertThat(((RecursoNoEncontradoException) e).errorCode())
                                                .isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO));
        }

        @Test
        @DisplayName("create con un producto parcialmente inexistente: RecursoNoEncontradoException")
        void create_partialProductsMissing() {
                TrasladoRequest req = new TrasladoRequest(1, 2, List.of(
                                new TrasladoDetalleRequest(1L, new BigDecimal("5.000")),
                                new TrasladoDetalleRequest(999L, new BigDecimal("2.000"))));
                when(almacenRepo.findById(1)).thenReturn(Optional.of(sampleAlmacen(1, "Origen")));
                when(almacenRepo.findById(2)).thenReturn(Optional.of(sampleAlmacen(2, "Destino")));
                when(productoRepo.findAllById(Set.of(1L, 999L)))
                                .thenReturn(List.of(sampleProducto(1L, "Tornillo")));

                assertThatThrownBy(() -> service.create(req))
                                .isInstanceOf(RecursoNoEncontradoException.class)
                                .satisfies(e -> assertThat(((RecursoNoEncontradoException) e).errorCode())
                                                .isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO));
        }

        @Test
        @DisplayName("create sin motivo TRASLADO_SALIDA: ReglaNegocioException VALOR_INVALIDO")
        void create_motivoSalidaMissing() {
                TrasladoRequest req = new TrasladoRequest(1, 2,
                                List.of(new TrasladoDetalleRequest(1L, new BigDecimal("5.000"))));
                stubCreateHastaFolio();
                when(motivoRepo.findByClave("TRASLADO_SALIDA")).thenReturn(Optional.empty());

                assertThatThrownBy(() -> service.create(req))
                                .isInstanceOf(ReglaNegocioException.class)
                                .satisfies(e -> assertThat(((ReglaNegocioException) e).errorCode())
                                                .isEqualTo(ErrorCode.VALOR_INVALIDO));
        }

        @Test
        @DisplayName("create sin motivo TRASLADO_ENTRADA: ReglaNegocioException VALOR_INVALIDO")
        void create_motivoEntradaMissing() {
                TrasladoRequest req = new TrasladoRequest(1, 2,
                                List.of(new TrasladoDetalleRequest(1L, new BigDecimal("5.000"))));
                stubCreateHastaFolio();
                when(motivoRepo.findByClave("TRASLADO_SALIDA")).thenReturn(Optional.of(
                                MotivoMovimiento.builder().motivoId(1).clave("TRASLADO_SALIDA")
                                                .tipoDefault("SALIDA").nombre("Salida por traslado").build()));
                when(motivoRepo.findByClave("TRASLADO_ENTRADA")).thenReturn(Optional.empty());

                assertThatThrownBy(() -> service.create(req))
                                .isInstanceOf(ReglaNegocioException.class)
                                .satisfies(e -> assertThat(((ReglaNegocioException) e).errorCode())
                                                .isEqualTo(ErrorCode.VALOR_INVALIDO));
        }

        @Test
        @DisplayName("create multi-detalle: folio generado + 2 movimientos por detalle")
        void create_multiDetalle() {
                TrasladoRequest req = new TrasladoRequest(1, 2, List.of(
                                new TrasladoDetalleRequest(1L, new BigDecimal("10.000")),
                                new TrasladoDetalleRequest(2L, new BigDecimal("4.500"))));
                when(almacenRepo.findById(1)).thenReturn(Optional.of(sampleAlmacen(1, "Origen")));
                when(almacenRepo.findById(2)).thenReturn(Optional.of(sampleAlmacen(2, "Destino")));
                when(productoRepo.findAllById(Set.of(1L, 2L))).thenReturn(List.of(
                                sampleProducto(1L, "Tornillo"), sampleProducto(2L, "Clavo")));
                Traslado savedTraslado = sampleTraslado(1L, 1, 2);
                when(repo.save(any(Traslado.class))).thenReturn(savedTraslado);
                when(repo.findFolioById(1L)).thenReturn("TR-0001");
                when(motivoRepo.findByClave("TRASLADO_SALIDA")).thenReturn(Optional.of(
                                MotivoMovimiento.builder().motivoId(1).clave("TRASLADO_SALIDA")
                                                .tipoDefault("SALIDA").nombre("Salida por traslado").build()));
                when(motivoRepo.findByClave("TRASLADO_ENTRADA")).thenReturn(Optional.of(
                                MotivoMovimiento.builder().motivoId(2).clave("TRASLADO_ENTRADA")
                                                .tipoDefault("ENTRADA").nombre("Entrada por traslado").build()));
                when(detalleRepo.findByTrasladoId(1L)).thenReturn(List.of(
                                sampleDetalle(1L, 1L, new BigDecimal("10.000")),
                                sampleDetalle(1L, 2L, new BigDecimal("4.500"))));
                when(productoRepo.findAllById(List.of(1L, 2L))).thenReturn(List.of(
                                sampleProducto(1L, "Tornillo"), sampleProducto(2L, "Clavo")));

                TrasladoResponse resp = service.create(req);

                assertThat(resp.folio()).isEqualTo("TR-0001");
                assertThat(resp.detalles()).hasSize(2);
                assertThat(resp.detalles()).extracting(r -> r.productoNombre())
                                .containsExactly("Tornillo", "Clavo");
                @SuppressWarnings("unchecked")
                ArgumentCaptor<List<MovimientoInventario>> movCaptor = ArgumentCaptor.forClass(List.class);
                verify(movimientoRepo).saveAll(movCaptor.capture());
                List<MovimientoInventario> movs = movCaptor.getValue();
                assertThat(movs).hasSize(4);
                assertThat(movs).extracting(MovimientoInventario::getTipo)
                                .containsExactly("SALIDA", "ENTRADA", "SALIDA", "ENTRADA");
                assertThat(movs).extracting(MovimientoInventario::getMotivoId)
                                .containsExactly(1, 2, 1, 2);
                assertThat(movs).extracting(MovimientoInventario::getAlmacenId)
                                .containsExactly(1, 2, 1, 2);
                assertThat(movs).allSatisfy(m -> {
                        assertThat(m.getRefTabla()).isEqualTo("TRASLADO");
                        assertThat(m.getRefId()).isEqualTo(1L);
                        assertThat(m.getTrasladoId()).isEqualTo(1L);
                });
        }

        // ── findMotivoId ────────────────────────────────────────────────

        @Test
        @DisplayName("findMotivoId existente: retorna motivoId")
        void findMotivoId_found() {
                when(motivoRepo.findByClave("TRASLADO_SALIDA")).thenReturn(Optional.of(
                                MotivoMovimiento.builder().motivoId(7).clave("TRASLADO_SALIDA")
                                                .tipoDefault("SALIDA").nombre("Salida por traslado").build()));

                assertThat(service.findMotivoId("TRASLADO_SALIDA")).isEqualTo(7);
        }

        @Test
        @DisplayName("findMotivoId inexistente: ReglaNegocioException VALOR_INVALIDO")
        void findMotivoId_notFound() {
                when(motivoRepo.findByClave("OTRO")).thenReturn(Optional.empty());

                assertThatThrownBy(() -> service.findMotivoId("OTRO"))
                                .isInstanceOf(ReglaNegocioException.class)
                                .satisfies(e -> assertThat(((ReglaNegocioException) e).errorCode())
                                                .isEqualTo(ErrorCode.VALOR_INVALIDO));
        }

        private void stubCreateHastaFolio() {
                when(almacenRepo.findById(1)).thenReturn(Optional.of(sampleAlmacen(1, "Origen")));
                when(almacenRepo.findById(2)).thenReturn(Optional.of(sampleAlmacen(2, "Destino")));
                when(productoRepo.findAllById(Set.of(1L)))
                                .thenReturn(List.of(sampleProducto(1L, "Tornillo")));
                when(repo.save(any(Traslado.class))).thenReturn(sampleTraslado(1L, 1, 2));
                when(repo.findFolioById(1L)).thenReturn("TR-0001");
        }
}
