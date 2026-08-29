package mx.ferreteria.api.fin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.fin.dto.FinDtos.GastoRequest;
import mx.ferreteria.api.fin.dto.FinDtos.IngresoOtroRequest;
import mx.ferreteria.api.fin.entity.Gasto;
import mx.ferreteria.api.fin.entity.IngresoOtro;
import mx.ferreteria.api.fin.repo.GastoRepository;
import mx.ferreteria.api.fin.repo.IngresoOtroRepository;

@ExtendWith(MockitoExtension.class)
class GastoServiceTest {

    @Mock GastoRepository gastoRepo;
    @Mock IngresoOtroRepository ingresoRepo;

    @InjectMocks
    GastoService service;

    private Gasto sampleGasto(Long id, Long turnoCajaId) {
        return Gasto.builder().gastoId(id)
                .folio("G-000001")
                .tipoGastoId(1).descripcion("Limpieza")
                .monto(new BigDecimal("250.00"))
                .fechaGasto(LocalDate.of(2026, 1, 10))
                .formaPagoId(1).proveedorId(3)
                .turnoCajaId(turnoCajaId).usuarioId(1)
                .creadoEn(Instant.now()).build();
    }

    private IngresoOtro sampleIngreso(Long id, Long turnoCajaId) {
        return IngresoOtro.builder().ingresoOtroId(id)
                .concepto("Renta de exhibidor")
                .monto(new BigDecimal("500.00"))
                .fecha(LocalDate.of(2026, 1, 10))
                .formaPagoId(1)
                .turnoCajaId(turnoCajaId).usuarioId(1)
                .creadoEn(Instant.now()).build();
    }

    private GastoRequest gastoRequest() {
        return new GastoRequest(2, "Cambio de descripcion", new BigDecimal("300.00"),
                LocalDate.of(2026, 1, 11), 2, null, null, null);
    }

    private IngresoOtroRequest ingresoRequest() {
        return new IngresoOtroRequest("Venta de chatarra", new BigDecimal("120.00"),
                LocalDate.of(2026, 1, 11), 2, null);
    }

    // ─── Gasto guardado sin turno: modificable ───────────────────────

    @Test
    @DisplayName("updateGasto modifica un gasto sin turno ligado")
    void updateGasto_sinTurno_actualiza() {
        Gasto g = sampleGasto(10L, null);
        when(gastoRepo.findById(10L)).thenReturn(Optional.of(g));
        when(gastoRepo.save(any(Gasto.class))).thenAnswer(inv -> inv.getArgument(0));

        var res = service.updateGasto(10L, gastoRequest());

        assertThat(res.monto()).isEqualByComparingTo("300.00");
        assertThat(res.descripcion()).isEqualTo("Cambio de descripcion");
        assertThat(res.tipoGastoId()).isEqualTo(2);
        assertThat(g.getTurnoCajaId()).isNull();
        verify(gastoRepo).save(g);
    }

    @Test
    @DisplayName("updateGasto bloqueado para gasto ligado a un turno")
    void updateGasto_conTurno_rechaza() {
        Gasto g = sampleGasto(10L, 7L);
        when(gastoRepo.findById(10L)).thenReturn(Optional.of(g));

        assertThatThrownBy(() -> service.updateGasto(10L, gastoRequest()))
                .isInstanceOf(ReglaNegocioException.class)
                .satisfies(e -> assertThat(((ReglaNegocioException) e).errorCode())
                        .isEqualTo(ErrorCode.REGISTRO_NO_MODIFICABLE));
    }

    @Test
    @DisplayName("updateGasto lanza 404 si no existe")
    void updateGasto_inexistente_recursoNoEncontrado() {
        when(gastoRepo.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateGasto(10L, gastoRequest()))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("deleteGasto elimina un gasto sin turno y bloquea uno ligado")
    void deleteGasto_respetaLigadoATurno() {
        Gasto libre = sampleGasto(10L, null);
        Gasto ligado = sampleGasto(11L, 7L);
        when(gastoRepo.findById(10L)).thenReturn(Optional.of(libre));
        when(gastoRepo.findById(11L)).thenReturn(Optional.of(ligado));

        service.deleteGasto(10L);
        verify(gastoRepo).delete(libre);

        assertThatThrownBy(() -> service.deleteGasto(11L))
                .isInstanceOf(ReglaNegocioException.class)
                .satisfies(e -> assertThat(((ReglaNegocioException) e).errorCode())
                        .isEqualTo(ErrorCode.REGISTRO_NO_MODIFICABLE));
    }

    // ─── Ingreso otro ────────────────────────────────────────────────

    @Test
    @DisplayName("updateIngreso modifica un ingreso sin turno ligado")
    void updateIngreso_sinTurno_actualiza() {
        IngresoOtro io = sampleIngreso(5L, null);
        when(ingresoRepo.findById(5L)).thenReturn(Optional.of(io));
        when(ingresoRepo.save(any(IngresoOtro.class))).thenAnswer(inv -> inv.getArgument(0));

        var res = service.updateIngreso(5L, ingresoRequest());

        assertThat(res.monto()).isEqualByComparingTo("120.00");
        assertThat(res.concepto()).isEqualTo("Venta de chatarra");
        verify(ingresoRepo).save(io);
    }

    @Test
    @DisplayName("updateIngreso bloqueado para ingreso ligado a un turno")
    void updateIngreso_conTurno_rechaza() {
        when(ingresoRepo.findById(5L)).thenReturn(Optional.of(sampleIngreso(5L, 7L)));

        assertThatThrownBy(() -> service.updateIngreso(5L, ingresoRequest()))
                .isInstanceOf(ReglaNegocioException.class)
                .satisfies(e -> assertThat(((ReglaNegocioException) e).errorCode())
                        .isEqualTo(ErrorCode.REGISTRO_NO_MODIFICABLE));
    }

    @Test
    @DisplayName("deleteIngreso elimina sin turno y bloquea uno ligado")
    void deleteIngreso_respetaLigadoATurno() {
        IngresoOtro libre = sampleIngreso(5L, null);
        IngresoOtro ligado = sampleIngreso(6L, 7L);
        when(ingresoRepo.findById(5L)).thenReturn(Optional.of(libre));
        when(ingresoRepo.findById(6L)).thenReturn(Optional.of(ligado));

        service.deleteIngreso(5L);
        verify(ingresoRepo).delete(libre);

        assertThatThrownBy(() -> service.deleteIngreso(6L))
                .isInstanceOf(ReglaNegocioException.class)
                .satisfies(e -> assertThat(((ReglaNegocioException) e).errorCode())
                        .isEqualTo(ErrorCode.REGISTRO_NO_MODIFICABLE));
    }
}