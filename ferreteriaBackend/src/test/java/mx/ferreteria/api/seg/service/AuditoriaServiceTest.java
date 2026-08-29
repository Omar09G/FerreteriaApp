package mx.ferreteria.api.seg.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import mx.ferreteria.api.common.error.ValidacionException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.seg.service.AuditoriaGateway.AuditoriaRow;
import mx.ferreteria.api.seg.service.AuditoriaGateway.TablaRow;

@ExtendWith(MockitoExtension.class)
class AuditoriaServiceTest {

    @Mock AuditoriaGateway gateway;
    @InjectMocks AuditoriaService service;

    private AuditoriaRow sample(long id) {
        return new AuditoriaRow(id, "ven", "promociones", 1L, "INSERT",
                null, "{\"x\":1}", 1, "admin", Instant.now());
    }

    @Test
    @DisplayName("buscar: delega al gateway con fechas convertidas a rango abierto UTC")
    void buscarDelega() {
        when(gateway.buscar(any())).thenReturn(List.of(sample(1L), sample(2L)));
        when(gateway.contar(any())).thenReturn(2L);

        var page = service.buscar("ven", "promociones", "INSERT", "adm", 1L,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), "texto",
                PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(2L);
        assertThat(page.getContent()).hasSize(2);
        ArgumentCaptor<AuditoriaGateway.Filtro> cap = ArgumentCaptor.forClass(AuditoriaGateway.Filtro.class);
        verify(gateway).buscar(cap.capture());
        AuditoriaGateway.Filtro f = cap.getValue();
        assertThat(f.esquema()).isEqualTo("ven");
        assertThat(f.tabla()).isEqualTo("promociones");
        assertThat(f.registroId()).isEqualTo(1L);
        assertThat(f.texto()).isEqualTo("texto");
        assertThat(f.desde()).isNotNull();
        assertThat(f.hasta()).isNotNull();
        assertThat(f.hasta()).isAfter(f.desde());
    }

    @Test
    @DisplayName("buscar: rango invertido → VALOR_INVALIDO")
    void buscarRangoInvertido() {
        assertThatThrownBy(() -> service.buscar(null, null, null, null, null,
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 1, 1), null,
                PageRequest.of(0, 20)))
                .isInstanceOf(ValidacionException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALOR_INVALIDO);
    }

    @Test
    @DisplayName("tablas: mapea esquema.tabla")
    void tablasMapea() {
        when(gateway.tablas()).thenReturn(List.of(
                new TablaRow("ven", "promociones"),
                new TablaRow("inv", "productos")));

        var out = service.tablas();

        assertThat(out).hasSize(2);
        assertThat(out.get(0).esquema()).isEqualTo("ven");
        assertThat(out.get(1).tabla()).isEqualTo("productos");
    }
}