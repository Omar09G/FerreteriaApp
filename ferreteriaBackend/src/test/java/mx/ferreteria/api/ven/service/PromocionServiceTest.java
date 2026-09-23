package mx.ferreteria.api.ven.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import mx.ferreteria.api.cat.entity.Categoria;
import mx.ferreteria.api.cat.entity.Cliente;
import mx.ferreteria.api.cat.entity.Producto;
import mx.ferreteria.api.cat.repo.ClienteRepository;
import mx.ferreteria.api.cat.repo.ProductoRepository;
import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.common.error.ValidacionException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.common.time.ZonaHoraria;
import mx.ferreteria.api.ven.dto.VenDtos.PromocionEvaluarItem;
import mx.ferreteria.api.ven.dto.VenDtos.PromocionEvaluarRequest;
import mx.ferreteria.api.ven.dto.VenDtos.PromocionRequest;
import mx.ferreteria.api.ven.entity.Promocion;
import mx.ferreteria.api.ven.entity.PromocionCategoria;
import mx.ferreteria.api.ven.entity.PromocionCategoriaId;
import mx.ferreteria.api.ven.entity.PromocionProducto;
import mx.ferreteria.api.ven.entity.PromocionProductoId;
import mx.ferreteria.api.ven.repo.PromocionCategoriaRepository;
import mx.ferreteria.api.ven.repo.PromocionProductoRepository;
import mx.ferreteria.api.ven.repo.PromocionRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PromocionServiceTest {

    @Mock PromocionRepository repo;
    @Mock PromocionProductoRepository productosRepo;
    @Mock PromocionCategoriaRepository categoriasRepo;
    @Mock ClienteRepository clienteRepo;
    @Mock ProductoRepository productoRepo;
    @Mock EntityManager em;
    @Mock Query nativeQuery;

    @InjectMocks
    PromocionService service;

    @BeforeEach
    void inyectarEntityManager() throws Exception {
        // @PersistenceContext no lo inyecta @InjectMocks de forma fiable:
        // contarUsosCliente() quedaria con em nulo y solo cubriria el catch.
        var campo = PromocionService.class.getDeclaredField("em");
        campo.setAccessible(true);
        campo.set(service, em);
    }

    private static final List<Short> TODA_SEMANA =
            List.of((short) 1, (short) 2, (short) 3, (short) 4, (short) 5, (short) 6, (short) 7);

    private Promocion promoLibre(long id, int usosActual) {
        return Promocion.builder()
                .promocionId(id)
                .nombre("Promo")
                .tipo("DESCUENTO_PRODUCTO")
                .valorPct(new BigDecimal("10.00"))
                .diasSemana(List.of((short) 1, (short) 2, (short) 3, (short) 4, (short) 5))
                .estado("ACTIVA")
                .soloMayoristas(false)
                .usosActual(usosActual)
                .vigenciaDesde(Instant.now())
                .creadoEn(Instant.now())
                .usuarioId(1)
                .build();
    }

    private PromocionRequest reqBase(String tipo, BigDecimal valorPct, BigDecimal valorMonto,
                                     BigDecimal lleva, BigDecimal paga, BigDecimal precio,
                                     List<Long> productos, List<Integer> categorias) {
        return new PromocionRequest(
                "Nombre", null, tipo, valorPct, valorMonto, precio,
                null, null, lleva, paga, null, null,
                null, null,
                List.of((short) 1, (short) 2, (short) 3, (short) 4, (short) 5, (short) 6, (short) 7),
                null, null, false, "ACTIVA", productos, categorias);
    }

    private PromocionRequest reqMin(String tipo, String estado, BigDecimal valorPct, BigDecimal valorMonto,
                                    BigDecimal lleva, BigDecimal paga, BigDecimal precioEsp) {
        return new PromocionRequest(
                "Nombre", null, tipo, valorPct, valorMonto, precioEsp,
                null, null, lleva, paga, null, null,
                null, null, TODA_SEMANA,
                null, null, false, estado, null, null);
    }

    private Promocion promoEval(long id, String tipo) {
        return Promocion.builder()
                .promocionId(id)
                .nombre("Promo " + id)
                .tipo(tipo)
                .valorPct(new BigDecimal("10.00"))
                .estado("ACTIVA")
                .soloMayoristas(false)
                .usosActual(0)
                .vigenciaDesde(Instant.now().minusSeconds(3600))
                .diasSemana(TODA_SEMANA)
                .creadoEn(Instant.now())
                .usuarioId(1)
                .build();
    }

    private PromocionEvaluarItem item(long productoId, String cantidad, String precio) {
        return new PromocionEvaluarItem(productoId, new BigDecimal(cantidad), new BigDecimal(precio));
    }

    private List<PromocionEvaluarItem> carritoBase() {
        return List.of(item(1L, "2", "100.00"));
    }

    private PromocionProducto pp(long promoId, long productoId) {
        return PromocionProducto.builder().promocionId(promoId).productoId(productoId).build();
    }

    private PromocionCategoria pc(long promoId, int categoriaId) {
        return PromocionCategoria.builder().promocionId(promoId).categoriaId(categoriaId).build();
    }

    private void dadoPromociones(Promocion... promos) {
        when(repo.findAll()).thenReturn(List.of(promos));
        when(productosRepo.findByPromocionIdIn(anyList())).thenReturn(List.of());
        when(categoriasRepo.findByPromocionIdIn(anyList())).thenReturn(List.of());
        when(productoRepo.findAllById(any())).thenAnswer(inv -> {
            // Null-safe: al re-stubear en un test, Mockito ejecuta este answer con arg nulo.
            Iterable<?> ids = inv.getArgument(0);
            List<Producto> out = new ArrayList<>();
            if (ids == null) {
                return out;
            }
            for (Object id : ids) {
                out.add(Producto.builder()
                        .productoId((Long) id)
                        .nombre("Prod " + id)
                        .categoria(Categoria.builder().categoriaId(99).nombre("Generica").build())
                        .build());
            }
            return out;
        });
    }

    private void dadoGuardarRelacionesVacias() {
        when(productosRepo.findByPromocionId(any())).thenReturn(List.of());
        when(categoriasRepo.findByPromocionId(any())).thenReturn(List.of());
    }

    private void dadoSaveConId(long id) {
        when(repo.save(any(Promocion.class))).thenAnswer(inv -> {
            Promocion p = inv.getArgument(0);
            p.setPromocionId(id);
            return p;
        });
    }

    @Test
    @DisplayName("obtener: si no existe, lanza RECURSO_NO_ENCONTRADO")
    void obtenerNoExiste() {
        when(repo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtener(99L))
                .isInstanceOf(ReglaNegocioException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO);
    }

    @Test
    @DisplayName("obtener: si existe, retorna response con productos y categorias")
    void obtenerEncontrado() {
        when(repo.findById(1L)).thenReturn(Optional.of(promoLibre(1L, 0)));
        when(productosRepo.findByPromocionId(1L)).thenReturn(List.of(pp(1L, 10L)));
        when(categoriasRepo.findByPromocionId(1L)).thenReturn(List.of(pc(1L, 3)));

        var resp = service.obtener(1L);

        assertThat(resp.promocionId()).isEqualTo(1L);
        assertThat(resp.nombre()).isEqualTo("Promo");
        assertThat(resp.productos()).containsExactly(10L);
        assertThat(resp.categorias()).containsExactly(3);
    }

    // ── listar ──────────────────────────────────────────────────────

    @Test
    @DisplayName("listar: con todos los filtros y pagina vacia retorna vacio")
    void listarVacioConFiltros() {
        Pageable pg = PageRequest.of(0, 10);
        when(repo.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), pg, 0));

        var page = service.listar("promo", "DESCUENTO_PRODUCTO", "ACTIVA",
                Instant.now().minusSeconds(86400), Instant.now().plusSeconds(86400), pg);

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
        verify(repo).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("listar: un solo elemento usa ruta simple con relaciones")
    void listarUnElemento() {
        Pageable pg = PageRequest.of(0, 10);
        Promocion p = promoLibre(1L, 0);
        when(repo.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(p), pg, 1));
        when(productosRepo.findByPromocionId(1L)).thenReturn(List.of(pp(1L, 10L)));
        when(categoriasRepo.findByPromocionId(1L)).thenReturn(List.of());

        var page = service.listar(null, null, null, null, null, pg);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).productos()).containsExactly(10L);
        assertThat(page.getContent().get(0).categorias()).isEmpty();
    }

    @Test
    @DisplayName("listar: varios elementos usa batch de relaciones (2 queries)")
    void listarVariosBatch() {
        Pageable pg = PageRequest.of(0, 10);
        when(repo.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(promoLibre(1L, 0), promoLibre(2L, 0)), pg, 2));
        when(productosRepo.findByPromocionIdIn(anyList()))
                .thenReturn(List.of(pp(1L, 10L), pp(2L, 20L)));
        when(categoriasRepo.findByPromocionIdIn(anyList()))
                .thenReturn(List.of(pc(1L, 3)));

        var page = service.listar("  Promo  ", null, null, null, null, pg);

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent().get(0).productos()).containsExactly(10L);
        assertThat(page.getContent().get(0).categorias()).containsExactly(3);
        assertThat(page.getContent().get(1).productos()).containsExactly(20L);
        assertThat(page.getContent().get(1).categorias()).isEmpty();
    }

    @Test
    @DisplayName("listar: varios sin relaciones usa listas vacias por defecto")
    void listarVariosSinRelaciones() {
        Pageable pg = PageRequest.of(1, 5);
        when(repo.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(promoLibre(1L, 0), promoLibre(2L, 0)), pg, 2));
        when(productosRepo.findByPromocionIdIn(anyList())).thenReturn(List.of());
        when(categoriasRepo.findByPromocionIdIn(anyList())).thenReturn(List.of());

        var page = service.listar(null, "DESCUENTO_PRODUCTO", null, null, null, pg);

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent()).allSatisfy(r -> {
            assertThat(r.productos()).isEmpty();
            assertThat(r.categorias()).isEmpty();
        });
    }

    @Test
    @DisplayName("listar: el Specification compone filtros y sus predicados son ejecutables")
    void listarEjecutaPredicados() {
        Pageable pg = PageRequest.of(0, 10);
        when(repo.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), pg, 0));

        service.listar("promo", "DESCUENTO_PRODUCTO", "ACTIVA",
                Instant.now().minusSeconds(86400), Instant.now().plusSeconds(86400), pg);

        ArgumentCaptor<Specification<Promocion>> cap = ArgumentCaptor.forClass(Specification.class);
        verify(repo).findAll(cap.capture(), any(Pageable.class));
        // Los lambdas del Specification solo se ejecutan contra un CriteriaBuilder:
        // invocar toPredicate con mocks cubre conjunction + los 5 filtros.
        assertThatCode(() -> cap.getValue().toPredicate(
                mock(Root.class), mock(CriteriaQuery.class), mock(CriteriaBuilder.class)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("crear: NXM sin lleva/paga, lanza VALOR_INVALIDO")
    void crearNxmInvalido() {
        var req = reqBase("NXM", null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.crear(req))
                .isInstanceOf(ValidacionException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALOR_INVALIDO);

        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("crear: NXM con paga >= lleva, lanza VALOR_INVALIDO")
    void crearNxmCoherencia() {
        var req = reqBase("NXM", null, null, new BigDecimal("3"), new BigDecimal("3"), null, null, null);

        assertThatThrownBy(() -> service.crear(req))
                .isInstanceOf(ValidacionException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALOR_INVALIDO);
    }

    @Test
    @DisplayName("crear: NXM con lleva cero, lanza VALOR_INVALIDO")
    void crearNxmLlevaCero() {
        var req = reqMin("NXM", "ACTIVA", null, null, BigDecimal.ZERO, BigDecimal.ONE, null);

        assertThatThrownBy(() -> service.crear(req))
                .isInstanceOf(ValidacionException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALOR_INVALIDO);

        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("crear: NXM valido persiste con lleva/paga")
    void crearNxmValido() {
        var req = reqMin("NXM", "ACTIVA", null, null, new BigDecimal("3"), new BigDecimal("2"), null);
        dadoGuardarRelacionesVacias();
        dadoSaveConId(5L);

        var resp = service.crear(req);

        ArgumentCaptor<Promocion> cap = ArgumentCaptor.forClass(Promocion.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().getLleva()).isEqualByComparingTo("3");
        assertThat(cap.getValue().getPaga()).isEqualByComparingTo("2");
        assertThat(resp.promocionId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("crear: DESCUENTO_PRODUCTO con valorPct, persiste y guarda relaciones")
    void crearDescuentoProducto() {
        var req = reqBase("DESCUENTO_PRODUCTO", new BigDecimal("15.00"), null, null, null, null,
                List.of(10L, 20L), List.of(3));
        when(productosRepo.findByPromocionId(any())).thenReturn(List.of());
        when(categoriasRepo.findByPromocionId(any())).thenReturn(List.of());
        when(repo.save(any(Promocion.class))).thenAnswer(inv -> {
            Promocion p = inv.getArgument(0);
            p.setPromocionId(1L);
            return p;
        });

        var resp = service.crear(req);

        ArgumentCaptor<Promocion> cap = ArgumentCaptor.forClass(Promocion.class);
        verify(repo).save(cap.capture());
        verify(productosRepo, times(2)).save(any());
        verify(categoriasRepo, times(1)).save(any());
        assertThat(cap.getValue().getUsuarioId()).isNotNull();
        assertThat(resp.promocionId()).isEqualTo(1L);
        assertThat(resp.usosActual()).isZero();
    }

    @Test
    @DisplayName("crear: DESCUENTO_PRODUCTO solo con monto es valido")
    void crearDescuentoMontoSolo() {
        var req = reqMin("DESCUENTO_PRODUCTO", "ACTIVA", null, new BigDecimal("5.00"), null, null, null);
        dadoGuardarRelacionesVacias();
        dadoSaveConId(6L);

        var resp = service.crear(req);

        verify(repo).save(any(Promocion.class));
        assertThat(resp.valorMonto()).isEqualByComparingTo("5.00");
    }

    @Test
    @DisplayName("crear: DESCUENTO_TOTAL_VENTA sin valores, lanza VALOR_INVALIDO")
    void crearDescuentoTotalSinValores() {
        var req = reqMin("DESCUENTO_TOTAL_VENTA", "ACTIVA", null, null, null, null, null);

        assertThatThrownBy(() -> service.crear(req))
                .isInstanceOf(ValidacionException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALOR_INVALIDO);

        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("crear: POR_CANTIDAD sin valores, lanza VALOR_INVALIDO")
    void crearPorCantidadSinValores() {
        var req = reqMin("POR_CANTIDAD", "ACTIVA", null, null, null, null, null);

        assertThatThrownBy(() -> service.crear(req))
                .isInstanceOf(ValidacionException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALOR_INVALIDO);

        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("crear: estado desconocido, lanza VALOR_INVALIDO")
    void crearEstadoInvalido() {
        var req = reqMin("DESCUENTO_PRODUCTO", "MAL_ESTADO", new BigDecimal("10"), null, null, null, null);

        assertThatThrownBy(() -> service.crear(req))
                .isInstanceOf(ValidacionException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALOR_INVALIDO);

        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("crear: PRECIO_ESPECIAL negativo, lanza VALOR_INVALIDO")
    void crearPrecioEspecialNegativo() {
        var req = reqMin("PRECIO_ESPECIAL", "ACTIVA", null, null, null, null, new BigDecimal("-5.00"));

        assertThatThrownBy(() -> service.crear(req))
                .isInstanceOf(ValidacionException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALOR_INVALIDO);

        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("crear: PRECIO_ESPECIAL valido persiste")
    void crearPrecioEspecialValido() {
        var req = reqMin("PRECIO_ESPECIAL", "ACTIVA", null, null, null, null, new BigDecimal("80.00"));
        dadoGuardarRelacionesVacias();
        dadoSaveConId(7L);

        var resp = service.crear(req);

        verify(repo).save(any(Promocion.class));
        assertThat(resp.precioEspecial()).isEqualByComparingTo("80.00");
    }

    @Test
    @DisplayName("crear: dias fuera de rango o nulos, lanza VALOR_INVALIDO")
    void crearDiasInvalidos() {
        var reqRango = reqMin("DESCUENTO_PRODUCTO", "ACTIVA", new BigDecimal("10"), null, null, null, null);
        var reqMal = new PromocionRequest(
                "Nombre", null, "DESCUENTO_PRODUCTO", new BigDecimal("10"), null, null,
                null, null, null, null, null, null, null, null,
                Arrays.asList((short) 1, (short) 8), null, null, false, "ACTIVA", null, null);
        var reqNulo = new PromocionRequest(
                "Nombre", null, "DESCUENTO_PRODUCTO", new BigDecimal("10"), null, null,
                null, null, null, null, null, null, null, null,
                Arrays.asList((short) 1, null), null, null, false, "ACTIVA", null, null);

        assertThat(reqRango.diasSemana()).hasSize(7);
        assertThatThrownBy(() -> service.crear(reqMal))
                .isInstanceOf(ValidacionException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALOR_INVALIDO);
        assertThatThrownBy(() -> service.crear(reqNulo))
                .isInstanceOf(ValidacionException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALOR_INVALIDO);

        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("crear: nulos aplican defaults (vigencia ahora, semana completa, ACTIVA, no mayorista)")
    void crearAplicaDefaults() {
        var req = new PromocionRequest(
                "  Promo Espacios  ", "desc", "DESCUENTO_TOTAL_VENTA", null, new BigDecimal("30.00"), null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);
        dadoGuardarRelacionesVacias();
        dadoSaveConId(8L);

        var resp = service.crear(req);

        ArgumentCaptor<Promocion> cap = ArgumentCaptor.forClass(Promocion.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().getNombre()).isEqualTo("Promo Espacios");
        assertThat(cap.getValue().getVigenciaDesde()).isNotNull();
        assertThat(cap.getValue().getDiasSemana())
                .containsExactly((short) 1, (short) 2, (short) 3, (short) 4, (short) 5, (short) 6, (short) 7);
        assertThat(cap.getValue().getEstado()).isEqualTo("ACTIVA");
        assertThat(cap.getValue().getSoloMayoristas()).isFalse();
        assertThat(cap.getValue().getUsosActual()).isZero();
        assertThat(resp.productos()).isEmpty();
        assertThat(resp.categorias()).isEmpty();
    }

    @Test
    @DisplayName("crear: dias vacios usan semana completa por defecto")
    void crearDiasVaciosUsaDefault() {
        var req = new PromocionRequest(
                "Nombre", null, "DESCUENTO_PRODUCTO", new BigDecimal("10"), null, null,
                null, null, null, null, null, null, null, null,
                List.of(), null, null, false, "ACTIVA", null, null);
        dadoGuardarRelacionesVacias();
        dadoSaveConId(9L);

        service.crear(req);

        ArgumentCaptor<Promocion> cap = ArgumentCaptor.forClass(Promocion.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().getDiasSemana()).hasSize(7);
    }

    @Test
    @DisplayName("crear: respeta vigencia, dias, estado y soloMayoristas provistos")
    void crearRespetaOpcionales() {
        Instant desde = Instant.now().minusSeconds(100);
        Instant hasta = Instant.now().plusSeconds(100);
        var req = new PromocionRequest(
                "Nombre", null, "DESCUENTO_PRODUCTO", new BigDecimal("10"), null, null,
                null, null, null, null, null, null, desde, hasta,
                List.of((short) 1, (short) 2), LocalTime.of(9, 0), LocalTime.of(18, 0),
                true, "PROGRAMADA", null, null);
        dadoGuardarRelacionesVacias();
        dadoSaveConId(10L);

        var resp = service.crear(req);

        ArgumentCaptor<Promocion> cap = ArgumentCaptor.forClass(Promocion.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().getVigenciaDesde()).isEqualTo(desde);
        assertThat(cap.getValue().getVigenciaHasta()).isEqualTo(hasta);
        assertThat(cap.getValue().getDiasSemana()).containsExactly((short) 1, (short) 2);
        assertThat(cap.getValue().getEstado()).isEqualTo("PROGRAMADA");
        assertThat(cap.getValue().getSoloMayoristas()).isTrue();
        assertThat(resp.estado()).isEqualTo("PROGRAMADA");
    }

    @Test
    @DisplayName("crear: con listas nulas elimina relaciones preexistentes")
    void crearEliminaRelacionesObsoletas() {
        var req = reqMin("DESCUENTO_PRODUCTO", "ACTIVA", new BigDecimal("10"), null, null, null, null);
        when(productosRepo.findByPromocionId(any())).thenReturn(List.of(pp(11L, 10L)));
        when(categoriasRepo.findByPromocionId(any())).thenReturn(List.of(pc(11L, 3)));
        dadoSaveConId(11L);

        service.crear(req);

        verify(productosRepo, never()).save(any());
        verify(categoriasRepo, never()).save(any());
        verify(productosRepo).deleteById(new PromocionProductoId(11L, 10L));
        verify(categoriasRepo).deleteById(new PromocionCategoriaId(11L, 3));
    }

    // ── actualizar ──────────────────────────────────────────────────

    @Test
    @DisplayName("actualizar: si no existe, lanza RECURSO_NO_ENCONTRADO")
    void actualizarNoExiste() {
        when(repo.findById(99L)).thenReturn(Optional.empty());
        var req = reqBase("DESCUENTO_PRODUCTO", new BigDecimal("10"), null, null, null, null, null, null);

        assertThatThrownBy(() -> service.actualizar(99L, req))
                .isInstanceOf(ReglaNegocioException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO);

        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("actualizar: tipo invalido, lanza VALOR_INVALIDO sin guardar")
    void actualizarTipoInvalido() {
        when(repo.findById(1L)).thenReturn(Optional.of(promoLibre(1L, 0)));
        var req = reqBase("XYZ", new BigDecimal("10"), null, null, null, null, null, null);

        assertThatThrownBy(() -> service.actualizar(1L, req))
                .isInstanceOf(ValidacionException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALOR_INVALIDO);

        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("actualizar: dias invalidos, lanza VALOR_INVALIDO")
    void actualizarDiasInvalidos() {
        when(repo.findById(1L)).thenReturn(Optional.of(promoLibre(1L, 0)));
        var req = new PromocionRequest(
                "Nombre", null, "DESCUENTO_PRODUCTO", new BigDecimal("10"), null, null,
                null, null, null, null, null, null, null, null,
                List.of((short) 0), null, null, false, "ACTIVA", null, null);

        assertThatThrownBy(() -> service.actualizar(1L, req))
                .isInstanceOf(ValidacionException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALOR_INVALIDO);

        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("actualizar: mapea campos, conserva autor/usos/creacion y sincroniza relaciones")
    void actualizarOk() {
        Promocion existente = promoLibre(1L, 2);
        existente.setUsuarioId(9);
        Instant creado = Instant.parse("2024-01-01T00:00:00Z");
        existente.setCreadoEn(creado);
        existente.setVigenciaDesde(Instant.parse("2024-01-02T00:00:00Z"));
        existente.setDiasSemana(List.of((short) 6));
        existente.setEstado("ACTIVA");
        when(repo.findById(1L)).thenReturn(Optional.of(existente));
        when(productosRepo.findByPromocionId(1L)).thenReturn(List.of(pp(1L, 10L), pp(1L, 20L)));
        when(categoriasRepo.findByPromocionId(1L)).thenReturn(List.of(pc(1L, 3), pc(1L, 4)));

        Instant nuevaDesde = Instant.parse("2024-02-01T00:00:00Z");
        var req = new PromocionRequest(
                "  Nuevo  ", "nueva desc", "DESCUENTO_TOTAL_VENTA",
                new BigDecimal("5.00"), null, null, null, null, null, null, null, null,
                nuevaDesde, null, List.of((short) 1, (short) 2), null, null,
                true, "FINALIZADA", List.of(20L, 30L), List.of(4, 5));

        var resp = service.actualizar(1L, req);

        assertThat(existente.getNombre()).isEqualTo("Nuevo");
        assertThat(existente.getTipo()).isEqualTo("DESCUENTO_TOTAL_VENTA");
        assertThat(existente.getVigenciaDesde()).isEqualTo(nuevaDesde);
        assertThat(existente.getDiasSemana()).containsExactly((short) 1, (short) 2);
        assertThat(existente.getEstado()).isEqualTo("FINALIZADA");
        assertThat(existente.getSoloMayoristas()).isTrue();
        assertThat(existente.getUsuarioId()).isEqualTo(9);
        assertThat(existente.getUsosActual()).isEqualTo(2);
        assertThat(existente.getCreadoEn()).isEqualTo(creado);
        verify(repo).save(existente);
        verify(productosRepo, times(1)).save(any());
        verify(productosRepo).deleteById(new PromocionProductoId(1L, 10L));
        verify(productosRepo, never()).deleteById(new PromocionProductoId(1L, 20L));
        verify(categoriasRepo, times(1)).save(any());
        verify(categoriasRepo).deleteById(new PromocionCategoriaId(1L, 3));
        verify(categoriasRepo, never()).deleteById(new PromocionCategoriaId(1L, 4));
        assertThat(resp.nombre()).isEqualTo("Nuevo");
        assertThat(resp.productos()).containsExactlyInAnyOrder(10L, 20L);
    }

    @Test
    @DisplayName("actualizar: nulos y dias vacios conservan vigencia, dias y estado")
    void actualizarNulosConservan() {
        Promocion existente = promoLibre(1L, 0);
        Instant desde = Instant.parse("2024-01-02T00:00:00Z");
        existente.setVigenciaDesde(desde);
        existente.setDiasSemana(List.of((short) 6));
        existente.setEstado("ACTIVA");
        when(repo.findById(1L)).thenReturn(Optional.of(existente));
        dadoGuardarRelacionesVacias();

        var reqNulos = new PromocionRequest(
                "Nombre", null, "DESCUENTO_PRODUCTO", new BigDecimal("10"), null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);
        var reqVacios = new PromocionRequest(
                "Nombre", null, "DESCUENTO_PRODUCTO", new BigDecimal("10"), null, null,
                null, null, null, null, null, null, null, null,
                List.of(), null, null, null, null, null, null);

        service.actualizar(1L, reqNulos);
        assertThat(existente.getVigenciaDesde()).isEqualTo(desde);
        assertThat(existente.getDiasSemana()).containsExactly((short) 6);
        assertThat(existente.getEstado()).isEqualTo("ACTIVA");
        assertThat(existente.getSoloMayoristas()).isFalse();

        service.actualizar(1L, reqVacios);
        assertThat(existente.getDiasSemana()).containsExactly((short) 6);

        verify(repo, times(2)).save(existente);
    }

    // ── eliminar ────────────────────────────────────────────────────

    @Test
    @DisplayName("eliminar: si no existe, lanza RECURSO_NO_ENCONTRADO")
    void eliminarNoExiste() {
        when(repo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.eliminar(99L))
                .isInstanceOf(ReglaNegocioException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO);

        verify(repo, never()).delete(any(Promocion.class));
    }

    @Test
    @DisplayName("eliminar: usosActual > 0 lanza REGISTRO_NO_MODIFICABLE (no se borra)")
    void eliminarConUsos() {
        when(repo.findById(1L)).thenReturn(Optional.of(promoLibre(1L, 3)));

        assertThatThrownBy(() -> service.eliminar(1L))
                .isInstanceOf(ReglaNegocioException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REGISTRO_NO_MODIFICABLE);

        verify(repo, never()).delete(any(Promocion.class));
    }

    @Test
    @DisplayName("eliminar: usosActual = 0 borra primero relaciones, luego la promoción")
    void eliminarOk() {
        when(repo.findById(1L)).thenReturn(Optional.of(promoLibre(1L, 0)));

        service.eliminar(1L);

        verify(productosRepo).deleteByPromocionId(1L);
        verify(categoriasRepo).deleteByPromocionId(1L);
        verify(repo).delete(any(Promocion.class));
    }

    @Test
    @DisplayName("eliminar: usosActual nulo se considera sin usos y borra")
    void eliminarUsosNulo() {
        Promocion p = promoLibre(1L, 0);
        p.setUsosActual(null);
        when(repo.findById(1L)).thenReturn(Optional.of(p));

        service.eliminar(1L);

        verify(productosRepo).deleteByPromocionId(1L);
        verify(categoriasRepo).deleteByPromocionId(1L);
        verify(repo).delete(p);
    }

    @Test
    @DisplayName("crear: tipo desconocido → VALOR_INVALIDO")
    void crearTipoInvalido() {
        var req = reqBase("XYZ", new BigDecimal("10"), null, null, null, null, null, null);
        assertThatThrownBy(() -> service.crear(req))
                .isInstanceOf(ValidacionException.class);
    }

    @Test
    @DisplayName("crear: PRECIO_ESPECIAL sin precio → VALOR_INVALIDO")
    void crearPrecioEspecialInvalido() {
        var req = reqBase("PRECIO_ESPECIAL", null, null, null, null, null, null, null);
        assertThatThrownBy(() -> service.crear(req))
                .isInstanceOf(ValidacionException.class);
    }

    // ── evaluar ─────────────────────────────────────────────────────

    @Test
    @DisplayName("evaluar: sin promociones retorna lista vacia")
    void evaluarSinPromociones() {
        when(repo.findAll()).thenReturn(List.of());

        var res = service.evaluar(new PromocionEvaluarRequest(null, carritoBase()));

        assertThat(res).isEmpty();
    }

    @Test
    @DisplayName("evaluar: DESCUENTO_TOTAL_VENTA con pct aplica a todo el ticket")
    void evaluarAplicaDescuentoTotalPct() {
        var p = promoEval(1L, "DESCUENTO_TOTAL_VENTA");
        p.setDiasSemana(null);
        p.setCompraMinTotal(new BigDecimal("100.00"));
        p.setCompraMinCantidad(new BigDecimal("2"));
        p.setVigenciaHasta(Instant.now().plusSeconds(3600));
        dadoPromociones(p);

        var res = service.evaluar(new PromocionEvaluarRequest(null, carritoBase()));

        assertThat(res).hasSize(1);
        assertThat(res.get(0).aplica()).isTrue();
        assertThat(res.get(0).beneficioEstimado()).isEqualByComparingTo("20.00");
        assertThat(res.get(0).motivo()).startsWith("Aplica");
    }

    @Test
    @DisplayName("evaluar: DESCUENTO_TOTAL_VENTA solo monto y sin vigenciaDesde")
    void evaluarMontoSoloSinDesde() {
        var p = promoEval(2L, "DESCUENTO_TOTAL_VENTA");
        p.setValorPct(null);
        p.setValorMonto(new BigDecimal("50.00"));
        p.setVigenciaDesde(null);
        p.setDiasSemana(List.of());
        dadoPromociones(p);

        var res = service.evaluar(new PromocionEvaluarRequest(null, carritoBase()));

        assertThat(res).hasSize(1);
        assertThat(res.get(0).aplica()).isTrue();
        assertThat(res.get(0).beneficioEstimado()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("evaluar: con pct y monto toma el menor, ordena por beneficio desc")
    void evaluarAmbosTomaMenor() {
        var a = promoEval(1L, "DESCUENTO_TOTAL_VENTA");
        var b = promoEval(2L, "DESCUENTO_TOTAL_VENTA");
        b.setValorMonto(new BigDecimal("5.00"));
        dadoPromociones(a, b);

        var res = service.evaluar(new PromocionEvaluarRequest(null, carritoBase()));

        assertThat(res).hasSize(2);
        assertThat(res.get(0).promocionId()).isEqualTo(1L);
        assertThat(res.get(0).beneficioEstimado()).isEqualByComparingTo("20.00");
        assertThat(res.get(1).promocionId()).isEqualTo(2L);
        assertThat(res.get(1).beneficioEstimado()).isEqualByComparingTo("5.00");
    }

    @Test
    @DisplayName("evaluar: DESCUENTO_TOTAL_VENTA sin valores da beneficio cero")
    void evaluarSinValoresDaCero() {
        var p = promoEval(1L, "DESCUENTO_TOTAL_VENTA");
        p.setValorPct(null);
        p.setValorMonto(null);
        dadoPromociones(p);

        var res = service.evaluar(new PromocionEvaluarRequest(null, carritoBase()));

        assertThat(res).hasSize(1);
        assertThat(res.get(0).aplica()).isFalse();
        assertThat(res.get(0).motivo()).contains("beneficio");
    }

    @Test
    @DisplayName("evaluar: estado no ACTIVA acumula fallos con separador")
    void evaluarEstadoNoActiva() {
        var p = promoEval(1L, "DESCUENTO_TOTAL_VENTA");
        p.setEstado("PROGRAMADA");
        p.setCompraMinTotal(new BigDecimal("99999.00"));
        dadoPromociones(p);

        var res = service.evaluar(new PromocionEvaluarRequest(null, carritoBase()));

        assertThat(res.get(0).aplica()).isFalse();
        assertThat(res.get(0).motivo()).contains("Estado").contains("; ");
    }

    @Test
    @DisplayName("evaluar: vigencia futura y vencida no aplican")
    void evaluarVigencias() {
        var futura = promoEval(1L, "DESCUENTO_TOTAL_VENTA");
        futura.setVigenciaDesde(Instant.now().plusSeconds(86400));
        var vencida = promoEval(2L, "DESCUENTO_TOTAL_VENTA");
        vencida.setVigenciaHasta(Instant.now().minusSeconds(86400));
        dadoPromociones(futura, vencida);

        var res = service.evaluar(new PromocionEvaluarRequest(null, carritoBase()));

        assertThat(res).allSatisfy(r -> assertThat(r.aplica()).isFalse());
        assertThat(res).anySatisfy(r -> assertThat(r.motivo()).contains("no inicia"));
        assertThat(res).anySatisfy(r -> assertThat(r.motivo()).contains("vencida"));
    }

    @Test
    @DisplayName("evaluar: dia no permitido no aplica")
    void evaluarDiaNoPermitido() {
        int hoy = ZonedDateTime.now(ZonaHoraria.ZONA).getDayOfWeek().getValue();
        var p = promoEval(1L, "DESCUENTO_TOTAL_VENTA");
        p.setDiasSemana(TODA_SEMANA.stream().filter(d -> d != hoy).map(Short.class::cast).toList());
        dadoPromociones(p);

        var res = service.evaluar(new PromocionEvaluarRequest(null, carritoBase()));

        assertThat(res.get(0).aplica()).isFalse();
        assertThat(res.get(0).motivo()).contains("Día");
    }

    @Test
    @DisplayName("evaluar: fuera de horario no aplica")
    void evaluarFueraHorario() {
        var p = promoEval(1L, "DESCUENTO_TOTAL_VENTA");
        p.setHoraDesde(LocalTime.of(23, 59, 59));
        p.setHoraHasta(LocalTime.of(23, 59, 58));
        dadoPromociones(p);

        var res = service.evaluar(new PromocionEvaluarRequest(null, carritoBase()));

        assertThat(res.get(0).aplica()).isFalse();
        assertThat(res.get(0).motivo()).contains("horario");
    }

    @Test
    @DisplayName("evaluar: en horario aplica, con y sin horaHasta")
    void evaluarEnHorario() {
        var conHasta = promoEval(1L, "DESCUENTO_TOTAL_VENTA");
        conHasta.setHoraDesde(LocalTime.MIN);
        conHasta.setHoraHasta(LocalTime.MAX);
        var sinHasta = promoEval(2L, "DESCUENTO_TOTAL_VENTA");
        sinHasta.setHoraDesde(LocalTime.MIN);
        sinHasta.setHoraHasta(null);
        dadoPromociones(conHasta, sinHasta);

        var res = service.evaluar(new PromocionEvaluarRequest(null, carritoBase()));

        assertThat(res).allSatisfy(r -> assertThat(r.aplica()).isTrue());
    }

    @Test
    @DisplayName("evaluar: solo mayoristas sin cliente no aplica")
    void evaluarSoloMayoristasSinCliente() {
        var p = promoEval(1L, "DESCUENTO_TOTAL_VENTA");
        p.setSoloMayoristas(true);
        p.setMaxUsosCliente(5);
        dadoPromociones(p);

        var res = service.evaluar(new PromocionEvaluarRequest(null, carritoBase()));

        assertThat(res.get(0).aplica()).isFalse();
        assertThat(res.get(0).motivo()).contains("Solo mayoristas").contains("(sin cliente)");
    }

    @Test
    @DisplayName("evaluar: solo mayoristas con cliente minorista no aplica")
    void evaluarSoloMayoristasMinorista() {
        var p = promoEval(1L, "DESCUENTO_TOTAL_VENTA");
        p.setSoloMayoristas(true);
        when(clienteRepo.findById(5L)).thenReturn(Optional.of(
                Cliente.builder().clienteId(5L).razonSocial("Menudeo").esMayorista(false).build()));
        dadoPromociones(p);

        var res = service.evaluar(new PromocionEvaluarRequest(5L, carritoBase()));

        assertThat(res.get(0).aplica()).isFalse();
        assertThat(res.get(0).motivo()).contains("Solo mayoristas")
                .doesNotContain("(sin cliente)");
    }

    @Test
    @DisplayName("evaluar: solo mayoristas con cliente mayorista aplica")
    void evaluarSoloMayoristasOk() {
        var p = promoEval(1L, "DESCUENTO_TOTAL_VENTA");
        p.setSoloMayoristas(true);
        when(clienteRepo.findById(5L)).thenReturn(Optional.of(
                Cliente.builder().clienteId(5L).razonSocial("Mayoreo").esMayorista(true).build()));
        dadoPromociones(p);

        var res = service.evaluar(new PromocionEvaluarRequest(5L, carritoBase()));

        assertThat(res.get(0).aplica()).isTrue();
    }

    @Test
    @DisplayName("evaluar: cliente inexistente se trata como sin cliente")
    void evaluarClienteNoEncontrado() {
        var p = promoEval(1L, "DESCUENTO_TOTAL_VENTA");
        p.setSoloMayoristas(true);
        when(clienteRepo.findById(99L)).thenReturn(Optional.empty());
        dadoPromociones(p);

        var res = service.evaluar(new PromocionEvaluarRequest(99L, carritoBase()));

        assertThat(res.get(0).aplica()).isFalse();
        assertThat(res.get(0).motivo()).contains("(sin cliente)");
    }

    @Test
    @DisplayName("evaluar: limite total alcanzado no aplica; usos nulos no bloquean")
    void evaluarMaxUsosTotal() {
        var llena = promoEval(1L, "DESCUENTO_TOTAL_VENTA");
        llena.setMaxUsosTotal(5);
        llena.setUsosActual(5);
        var sinUsos = promoEval(2L, "DESCUENTO_TOTAL_VENTA");
        sinUsos.setMaxUsosTotal(5);
        sinUsos.setUsosActual(null);
        dadoPromociones(llena, sinUsos);

        var res = service.evaluar(new PromocionEvaluarRequest(null, carritoBase()));

        assertThat(res).anySatisfy(r -> {
            assertThat(r.promocionId()).isEqualTo(1L);
            assertThat(r.aplica()).isFalse();
            assertThat(r.motivo()).contains("Límite total");
        });
        assertThat(res).anySatisfy(r -> {
            assertThat(r.promocionId()).isEqualTo(2L);
            assertThat(r.aplica()).isTrue();
        });
    }

    private void dadoConteoCliente(Object conteo) {
        when(em.createNativeQuery(anyString())).thenReturn(nativeQuery);
        when(nativeQuery.setParameter(anyString(), any())).thenReturn(nativeQuery);
        if (conteo instanceof RuntimeException ex) {
            when(nativeQuery.getSingleResult()).thenThrow(ex);
        } else {
            when(nativeQuery.getSingleResult()).thenReturn(conteo);
        }
    }

    @Test
    @DisplayName("evaluar: limite por cliente usa conteo de usos (alcanza y no alcanza)")
    void evaluarMaxUsosCliente() {
        dadoConteoCliente(3L);
        var llena = promoEval(1L, "DESCUENTO_TOTAL_VENTA");
        llena.setMaxUsosCliente(3);
        var holgada = promoEval(2L, "DESCUENTO_TOTAL_VENTA");
        holgada.setMaxUsosCliente(10);
        dadoPromociones(llena, holgada);

        var res = service.evaluar(new PromocionEvaluarRequest(5L, carritoBase()));

        assertThat(res).anySatisfy(r -> {
            assertThat(r.promocionId()).isEqualTo(1L);
            assertThat(r.aplica()).isFalse();
            assertThat(r.motivo()).contains("Límite por cliente");
        });
        assertThat(res).anySatisfy(r -> {
            assertThat(r.promocionId()).isEqualTo(2L);
            assertThat(r.aplica()).isTrue();
        });
    }

    @Test
    @DisplayName("evaluar: conteo de usos no numerico se trata como cero")
    void evaluarConteoNoNumerico() {
        dadoConteoCliente("no-numerico");
        var p = promoEval(1L, "DESCUENTO_TOTAL_VENTA");
        p.setMaxUsosCliente(1);
        dadoPromociones(p);

        var res = service.evaluar(new PromocionEvaluarRequest(5L, carritoBase()));

        assertThat(res.get(0).aplica()).isTrue();
    }

    @Test
    @DisplayName("evaluar: error en conteo de usos se trata como cero")
    void evaluarConteoConExcepcion() {
        dadoConteoCliente(new RuntimeException("bd caida"));
        var p = promoEval(1L, "DESCUENTO_TOTAL_VENTA");
        p.setMaxUsosCliente(1);
        dadoPromociones(p);

        var res = service.evaluar(new PromocionEvaluarRequest(5L, carritoBase()));

        assertThat(res.get(0).aplica()).isTrue();
    }

    @Test
    @DisplayName("evaluar: compra minima total no alcanzada no aplica")
    void evaluarCompraMinTotal() {
        var p = promoEval(1L, "DESCUENTO_TOTAL_VENTA");
        p.setCompraMinTotal(new BigDecimal("1000.00"));
        dadoPromociones(p);

        var res = service.evaluar(new PromocionEvaluarRequest(null, carritoBase()));

        assertThat(res.get(0).aplica()).isFalse();
        assertThat(res.get(0).motivo()).contains("mínima");
    }

    @Test
    @DisplayName("evaluar: cantidad minima no alcanzada no aplica")
    void evaluarCompraMinCantidad() {
        var p = promoEval(1L, "DESCUENTO_TOTAL_VENTA");
        p.setCompraMinCantidad(new BigDecimal("10"));
        dadoPromociones(p);

        var res = service.evaluar(new PromocionEvaluarRequest(null, carritoBase()));

        assertThat(res.get(0).aplica()).isFalse();
        assertThat(res.get(0).motivo()).contains("Cantidad mínima");
    }

    @Test
    @DisplayName("evaluar: sin coincidencia de producto/categoria no aplica")
    void evaluarSinMatchProducto() {
        var p = promoEval(1L, "DESCUENTO_PRODUCTO");
        dadoPromociones(p);
        when(productosRepo.findByPromocionIdIn(anyList())).thenReturn(List.of(pp(1L, 999L)));

        var res = service.evaluar(new PromocionEvaluarRequest(null, carritoBase()));

        assertThat(res.get(0).aplica()).isFalse();
        assertThat(res.get(0).motivo()).contains("Ningún producto");
    }

    @Test
    @DisplayName("evaluar: coincidencia por categoria aplica")
    void evaluarMatchCategoria() {
        var p = promoEval(1L, "DESCUENTO_PRODUCTO");
        dadoPromociones(p);
        when(categoriasRepo.findByPromocionIdIn(anyList())).thenReturn(List.of(pc(1L, 7)));
        var cat = Categoria.builder().categoriaId(7).nombre("Herramienta").build();
        when(productoRepo.findAllById(any())).thenReturn(
                List.of(Producto.builder().productoId(1L).nombre("Martillo").categoria(cat).build()));

        var res = service.evaluar(new PromocionEvaluarRequest(null, carritoBase()));

        assertThat(res.get(0).aplica()).isTrue();
        assertThat(res.get(0).beneficioEstimado()).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("evaluar: producto sin categoria usa monto por linea coincidente")
    void evaluarProductoSinCategoriaMonto() {
        var p = promoEval(1L, "DESCUENTO_PRODUCTO");
        p.setValorPct(null);
        p.setValorMonto(new BigDecimal("15.00"));
        dadoPromociones(p);
        when(productosRepo.findByPromocionIdIn(anyList())).thenReturn(List.of(pp(1L, 1L)));
        when(productoRepo.findAllById(any())).thenReturn(
                List.of(Producto.builder().productoId(1L).nombre("Suelto").categoria(null).build()));

        var res = service.evaluar(new PromocionEvaluarRequest(null, carritoBase()));

        assertThat(res.get(0).aplica()).isTrue();
        assertThat(res.get(0).beneficioEstimado()).isEqualByComparingTo("15.00");
    }

    @Test
    @DisplayName("evaluar: DESCUENTO_PRODUCTO solo suma lineas coincidentes")
    void evaluarDescuentoProductoSaltaLinea() {
        var p = promoEval(1L, "DESCUENTO_PRODUCTO");
        dadoPromociones(p);
        when(productosRepo.findByPromocionIdIn(anyList())).thenReturn(List.of(pp(1L, 1L)));

        var items = List.of(item(1L, "2", "100.00"), item(2L, "1", "50.00"));
        var res = service.evaluar(new PromocionEvaluarRequest(null, items));

        assertThat(res.get(0).aplica()).isTrue();
        assertThat(res.get(0).beneficioEstimado()).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("evaluar: POR_CANTIDAD con pct y monto aplica sobre todas las lineas")
    void evaluarPorCantidad() {
        var porPct = promoEval(1L, "POR_CANTIDAD");
        porPct.setCompraMinCantidad(new BigDecimal("2"));
        var porMonto = promoEval(2L, "POR_CANTIDAD");
        porMonto.setValorPct(null);
        porMonto.setValorMonto(new BigDecimal("7.00"));
        porMonto.setCompraMinCantidad(new BigDecimal("2"));
        dadoPromociones(porPct, porMonto);

        var items = List.of(item(1L, "2", "100.00"), item(2L, "1", "50.00"));
        var res = service.evaluar(new PromocionEvaluarRequest(null, items));

        assertThat(res).allSatisfy(r -> assertThat(r.aplica()).isTrue());
        assertThat(res.get(0).promocionId()).isEqualTo(1L);
        assertThat(res.get(0).beneficioEstimado()).isEqualByComparingTo("25.00");
        assertThat(res.get(1).promocionId()).isEqualTo(2L);
        assertThat(res.get(1).beneficioEstimado()).isEqualByComparingTo("14.00");
    }

    @Test
    @DisplayName("evaluar: PRECIO_ESPECIAL ahorra diferencia; sin ahorro no aplica")
    void evaluarPrecioEspecial() {
        var conAhorro = promoEval(1L, "PRECIO_ESPECIAL");
        conAhorro.setValorPct(null);
        conAhorro.setPrecioEspecial(new BigDecimal("80.00"));
        var sinAhorro = promoEval(2L, "PRECIO_ESPECIAL");
        sinAhorro.setValorPct(null);
        sinAhorro.setPrecioEspecial(new BigDecimal("150.00"));
        dadoPromociones(conAhorro, sinAhorro);
        when(productosRepo.findByPromocionIdIn(anyList()))
                .thenReturn(List.of(pp(1L, 1L), pp(2L, 1L)));

        var items = List.of(item(1L, "2", "100.00"), item(2L, "1", "50.00"));
        var res = service.evaluar(new PromocionEvaluarRequest(null, items));

        assertThat(res).anySatisfy(r -> {
            assertThat(r.promocionId()).isEqualTo(1L);
            assertThat(r.aplica()).isTrue();
            assertThat(r.beneficioEstimado()).isEqualByComparingTo("40.00");
        });
        assertThat(res).anySatisfy(r -> {
            assertThat(r.promocionId()).isEqualTo(2L);
            assertThat(r.aplica()).isFalse();
            assertThat(r.motivo()).contains("beneficio");
        });
    }

    @Test
    @DisplayName("evaluar: NXM calcula gratis por multiplo; sin multiplo no aplica")
    void evaluarNxm() {
        var aplica = promoEval(1L, "NXM");
        aplica.setValorPct(null);
        aplica.setLleva(new BigDecimal("3"));
        aplica.setPaga(new BigDecimal("2"));
        var sinMultiplo = promoEval(2L, "NXM");
        sinMultiplo.setValorPct(null);
        sinMultiplo.setLleva(new BigDecimal("8"));
        sinMultiplo.setPaga(new BigDecimal("7"));
        dadoPromociones(aplica, sinMultiplo);
        when(productosRepo.findByPromocionIdIn(anyList()))
                .thenReturn(List.of(pp(1L, 1L), pp(2L, 1L)));

        var items = List.of(item(1L, "7", "10.00"), item(2L, "7", "10.00"));
        var res = service.evaluar(new PromocionEvaluarRequest(null, items));

        assertThat(res).anySatisfy(r -> {
            assertThat(r.promocionId()).isEqualTo(1L);
            assertThat(r.aplica()).isTrue();
            assertThat(r.beneficioEstimado()).isEqualByComparingTo("20.00");
        });
        assertThat(res).anySatisfy(r -> {
            assertThat(r.promocionId()).isEqualTo(2L);
            assertThat(r.aplica()).isFalse();
            assertThat(r.motivo()).contains("beneficio");
        });
    }

    @Test
    @DisplayName("evaluar: NXM sin lleva/paga o con lleva cero no aplica")
    void evaluarNxmSinConfig() {
        var sinDatos = promoEval(1L, "NXM");
        sinDatos.setValorPct(null);
        sinDatos.setLleva(null);
        sinDatos.setPaga(null);
        var llevaCero = promoEval(2L, "NXM");
        llevaCero.setValorPct(null);
        llevaCero.setLleva(BigDecimal.ZERO);
        llevaCero.setPaga(BigDecimal.ONE);
        dadoPromociones(sinDatos, llevaCero);

        var res = service.evaluar(new PromocionEvaluarRequest(null, carritoBase()));

        assertThat(res).allSatisfy(r -> {
            assertThat(r.aplica()).isFalse();
            assertThat(r.motivo()).contains("beneficio");
        });
    }

    @Test
    @DisplayName("evaluar: tipo desconocido en BD da beneficio cero")
    void evaluarTipoDesconocido() {
        var p = promoEval(1L, "XYZ");
        dadoPromociones(p);

        var res = service.evaluar(new PromocionEvaluarRequest(null, carritoBase()));

        assertThat(res.get(0).aplica()).isFalse();
        assertThat(res.get(0).motivo()).contains("beneficio");
    }

    @Test
    @DisplayName("evaluar: ordena aplicables primero y luego por beneficio desc")
    void evaluarOrden() {
        var chica = promoEval(1L, "DESCUENTO_TOTAL_VENTA");
        chica.setValorPct(null);
        chica.setValorMonto(new BigDecimal("5.00"));
        var grande = promoEval(2L, "DESCUENTO_TOTAL_VENTA");
        var vencida = promoEval(3L, "DESCUENTO_TOTAL_VENTA");
        vencida.setVigenciaHasta(Instant.now().minusSeconds(60));
        dadoPromociones(chica, grande, vencida);

        var res = service.evaluar(new PromocionEvaluarRequest(null, carritoBase()));

        assertThat(res).extracting(r -> r.promocionId()).containsExactly(2L, 1L, 3L);
        assertThat(res.get(0).aplica()).isTrue();
        assertThat(res.get(2).aplica()).isFalse();
    }

    @Test
    @DisplayName("evaluar: con solo categorias, la linea sin match se salta en cada tipo")
    void evaluarSaltaLineaSoloCategoria() {
        var p1 = promoEval(1L, "DESCUENTO_PRODUCTO");
        var p2 = promoEval(2L, "POR_CANTIDAD");
        p2.setCompraMinCantidad(new BigDecimal("2"));
        var p3 = promoEval(3L, "PRECIO_ESPECIAL");
        p3.setValorPct(null);
        p3.setPrecioEspecial(new BigDecimal("80.00"));
        var p4 = promoEval(4L, "NXM");
        p4.setValorPct(null);
        p4.setLleva(new BigDecimal("3"));
        p4.setPaga(new BigDecimal("2"));
        var p5 = promoEval(5L, "PRECIO_ESPECIAL");
        p5.setValorPct(null);
        p5.setPrecioEspecial(null);
        dadoPromociones(p1, p2, p3, p4, p5);
        when(categoriasRepo.findByPromocionIdIn(anyList())).thenReturn(
                List.of(pc(1L, 7), pc(2L, 7), pc(3L, 7), pc(4L, 7), pc(5L, 7)));
        var cat7 = Categoria.builder().categoriaId(7).nombre("Siete").build();
        var cat99 = Categoria.builder().categoriaId(99).nombre("Otra").build();
        when(productoRepo.findAllById(any())).thenReturn(List.of(
                Producto.builder().productoId(1L).nombre("A").categoria(cat7).build(),
                Producto.builder().productoId(2L).nombre("B").categoria(cat99).build()));

        // prod1 coincide por categoria; prod2 no coincide con nada y debe saltarse.
        var items = List.of(item(1L, "7", "100.00"), item(2L, "7", "100.00"));
        var res = service.evaluar(new PromocionEvaluarRequest(null, items));

        assertThat(res).hasSize(5);
        assertThat(res).anySatisfy(r -> {
            assertThat(r.promocionId()).isEqualTo(1L);
            assertThat(r.aplica()).isTrue();
            assertThat(r.beneficioEstimado()).isEqualByComparingTo("70.00");
        });
        assertThat(res).anySatisfy(r -> {
            assertThat(r.promocionId()).isEqualTo(2L);
            assertThat(r.aplica()).isTrue();
            assertThat(r.beneficioEstimado()).isEqualByComparingTo("70.00");
        });
        assertThat(res).anySatisfy(r -> {
            assertThat(r.promocionId()).isEqualTo(3L);
            assertThat(r.aplica()).isTrue();
            assertThat(r.beneficioEstimado()).isEqualByComparingTo("140.00");
        });
        assertThat(res).anySatisfy(r -> {
            assertThat(r.promocionId()).isEqualTo(4L);
            assertThat(r.aplica()).isTrue();
            assertThat(r.beneficioEstimado()).isEqualByComparingTo("200.00");
        });
        assertThat(res).anySatisfy(r -> {
            assertThat(r.promocionId()).isEqualTo(5L);
            assertThat(r.aplica()).isTrue();
            assertThat(r.beneficioEstimado()).isEqualByComparingTo("700.00");
        });
    }

    @Test
    @DisplayName("evaluar: ticket vacio no calcula beneficio en tipos por producto")
    void evaluarTicketVacio() {
        var total = promoEval(1L, "DESCUENTO_TOTAL_VENTA");
        var porCantidad = promoEval(2L, "POR_CANTIDAD");
        dadoPromociones(total, porCantidad);

        var res = service.evaluar(new PromocionEvaluarRequest(null, List.of()));

        assertThat(res).hasSize(2);
        assertThat(res.get(0).promocionId()).isEqualTo(2L);
        assertThat(res.get(0).aplica()).isTrue();
        assertThat(res.get(0).motivo()).startsWith("Aplica");
        assertThat(res.get(0).beneficioEstimado()).isEqualByComparingTo("0.00");
        assertThat(res.get(1).promocionId()).isEqualTo(1L);
        assertThat(res.get(1).aplica()).isFalse();
        assertThat(res.get(1).motivo()).contains("beneficio");
    }
}
