package mx.ferreteria.api.fin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.fin.dto.FinDtos.GastoRequest;
import mx.ferreteria.api.fin.dto.FinDtos.IngresoOtroRequest;
import mx.ferreteria.api.fin.entity.Gasto;
import mx.ferreteria.api.fin.entity.IngresoOtro;
import mx.ferreteria.api.fin.repo.GastoRepository;
import mx.ferreteria.api.fin.repo.IngresoOtroRepository;

@ExtendWith(MockitoExtension.class)
class GastoServiceTest {

    @Mock
    GastoRepository gastoRepo;

    @Mock
    IngresoOtroRepository ingresoRepo;

    @InjectMocks
    GastoService service;

    private Gasto sampleGasto(Long id) {
        return Gasto.builder()
                .gastoId(id).folio("G-00" + id)
                .tipoGastoId(1).descripcion("Renta local")
                .monto(new BigDecimal("15000.00"))
                .fechaGasto(LocalDate.now())
                .formaPagoId(1).usuarioId(1)
                .creadoEn(Instant.now())
                .build();
    }

    private IngresoOtro sampleIngreso(Long id) {
        return IngresoOtro.builder()
                .ingresoOtroId(id).concepto("Venta de chatarra")
                .monto(new BigDecimal("250.00"))
                .fecha(LocalDate.now())
                .formaPagoId(1).usuarioId(1)
                .creadoEn(Instant.now())
                .build();
    }

    @Test
    @DisplayName("listGastos: retorna pagina con items")
    void listGastos_returnsPage() {
        Pageable pg = PageRequest.of(0, 10);
        Gasto g = sampleGasto(1L);
        when(gastoRepo.findAllByOrderByCreadoEnDesc(pg))
                .thenReturn(new PageImpl<>(List.of(g), pg, 1));

        var result = service.listGastos(pg);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).folio()).isEqualTo("G-001");
    }

    @Test
    @DisplayName("createGasto: save y retorna GastoResponse")
    void createGasto_ok() {
        GastoRequest req = new GastoRequest(
                1, "Renta local", new BigDecimal("15000.00"),
                LocalDate.now(), 1, null, null, null);
        when(gastoRepo.save(any(Gasto.class))).thenReturn(sampleGasto(10L));

        var resp = service.createGasto(req);

        assertThat(resp.gastoId()).isEqualTo(10L);
        assertThat(resp.descripcion()).isEqualTo("Renta local");
        verify(gastoRepo).save(any(Gasto.class));
    }

    @Test
    @DisplayName("listIngresos: retorna pagina con items")
    void listIngresos_returnsPage() {
        Pageable pg = PageRequest.of(0, 10);
        IngresoOtro io = sampleIngreso(1L);
        when(ingresoRepo.findAllByOrderByCreadoEnDesc(pg))
                .thenReturn(new PageImpl<>(List.of(io), pg, 1));

        var result = service.listIngresos(pg);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).concepto()).isEqualTo("Venta de chatarra");
    }

    @Test
    @DisplayName("createIngreso: save y retorna IngresoOtroResponse")
    void createIngreso_ok() {
        IngresoOtroRequest req = new IngresoOtroRequest(
                "Venta de chatarra", new BigDecimal("250.00"),
                LocalDate.now(), 1, null);
        when(ingresoRepo.save(any(IngresoOtro.class))).thenReturn(sampleIngreso(5L));

        var resp = service.createIngreso(req);

        assertThat(resp.ingresoOtroId()).isEqualTo(5L);
        assertThat(resp.concepto()).isEqualTo("Venta de chatarra");
        verify(ingresoRepo).save(any(IngresoOtro.class));
    }
}