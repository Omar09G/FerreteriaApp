package mx.ferreteria.api.inv.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import mx.ferreteria.api.inv.entity.Inventario;
import mx.ferreteria.api.inv.repo.InventarioRepository;

@ExtendWith(MockitoExtension.class)
class StockPortAdapterTest {

    @Mock
    InventarioRepository inventarioRepo;

    @InjectMocks
    StockPortAdapter adapter;

    private static Inventario inv(Long productoId, BigDecimal stock) {
        return Inventario.builder().productoId(productoId).almacenId(1).stock(stock).build();
    }

    @Test
    @DisplayName("batch: una sola consulta, stock nulo se reporta como CERO")
    void batch_unaSolaConsulta() {
        when(inventarioRepo.findByAlmacenIdAndProductoIdIn(1, List.of(1L, 2L)))
                .thenReturn(List.of(inv(1L, new BigDecimal("5")), inv(2L, null)));

        Map<Long, BigDecimal> stock = adapter.stockPorProductos(1, List.of(1L, 2L));

        assertThat(stock).isEqualTo(Map.of(1L, new BigDecimal("5"), 2L, BigDecimal.ZERO));
        verify(inventarioRepo).findByAlmacenIdAndProductoIdIn(1, List.of(1L, 2L));
    }

    @Test
    @DisplayName("batch sin almacén o vacío: vacío sin consultar")
    void batch_sinAlmacen_vacio() {
        assertThat(adapter.stockPorProductos(null, List.of(1L))).isEmpty();
        assertThat(adapter.stockPorProductos(1, List.of())).isEmpty();
        verifyNoInteractions(inventarioRepo);
    }

    @Test
    @DisplayName("unitario: sin registro reporta CERO")
    void unitario_sinRegistro_cero() {
        when(inventarioRepo.findByAlmacenIdAndProductoId(1, 9L)).thenReturn(null);

        assertThat(adapter.stockDeProducto(1, 9L)).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("unitario: devuelve el stock tal cual")
    void unitario_devuelveStock() {
        when(inventarioRepo.findByAlmacenIdAndProductoId(1, 1L))
                .thenReturn(inv(1L, new BigDecimal("7.5")));

        assertThat(adapter.stockDeProducto(1, 1L)).isEqualTo(new BigDecimal("7.5"));
    }
}
