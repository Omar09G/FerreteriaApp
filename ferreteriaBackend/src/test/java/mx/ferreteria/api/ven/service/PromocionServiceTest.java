package mx.ferreteria.api.ven.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.common.error.ValidacionException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.ven.dto.VenDtos.PromocionRequest;
import mx.ferreteria.api.ven.entity.Promocion;
import mx.ferreteria.api.ven.repo.PromocionCategoriaRepository;
import mx.ferreteria.api.ven.repo.PromocionProductoRepository;
import mx.ferreteria.api.ven.repo.PromocionRepository;

@ExtendWith(MockitoExtension.class)
class PromocionServiceTest {

    @Mock PromocionRepository repo;
    @Mock PromocionProductoRepository productosRepo;
    @Mock PromocionCategoriaRepository categoriasRepo;

    @InjectMocks
    PromocionService service;

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

    @Test
    @DisplayName("obtener: si no existe, lanza RECURSO_NO_ENCONTRADO")
    void obtenerNoExiste() {
        when(repo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtener(99L))
                .isInstanceOf(ReglaNegocioException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO);
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
        assertThat(resp.usosActual()).isEqualTo(0);
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
}