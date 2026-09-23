package mx.ferreteria.api.notif.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.rh.entity.Nomina;
import mx.ferreteria.api.rh.repo.NominaRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NominaPdfServiceTest {

    @Mock
    NominaRepository nominaRepo;

    @InjectMocks
    NominaPdfService service;

    private Nomina sampleNomina() {
        return Nomina.builder()
                .nominaId(1L)
                .empleadoId(7)
                .periodoIni(LocalDate.of(2026, 9, 1))
                .periodoFin(LocalDate.of(2026, 9, 15))
                .diasPagados(new BigDecimal("15.0"))
                .percepciones(new BigDecimal("10000.00"))
                .deducciones(new BigDecimal("1500.50"))
                .netoPagar(new BigDecimal("8499.50"))
                .estado("PAGADA")
                .fechaPago(Instant.parse("2026-09-16T14:00:00Z"))
                .usuarioRegistraId(1)
                .notas("Pago quincenal")
                .build();
    }

    private static void assertEsPdf(byte[] bytes) {
        assertThat(bytes).isNotEmpty();
        assertThat(new String(bytes, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    @Test
    @DisplayName("generarNominaPdf inexistente: RecursoNoEncontradoException")
    void generarPdf_notFound() {
        when(nominaRepo.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generarNominaPdf(999L))
                .isInstanceOfSatisfying(RecursoNoEncontradoException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO));
    }

    @Test
    @DisplayName("generarNominaPdf completa: genera PDF con fecha de pago y notas")
    void generarPdf_completa() {
        when(nominaRepo.findById(1L)).thenReturn(Optional.of(sampleNomina()));

        byte[] pdf = service.generarNominaPdf(1L);

        assertEsPdf(pdf);
        verify(nominaRepo).findById(1L);
    }

    @Test
    @DisplayName("generarNominaPdf sin fecha de pago ni notas: genera PDF")
    void generarPdf_sinOpcionales() {
        Nomina n = sampleNomina();
        n.setFechaPago(null);
        n.setNotas(null);
        when(nominaRepo.findById(1L)).thenReturn(Optional.of(n));

        assertEsPdf(service.generarNominaPdf(1L));
    }

    @Test
    @DisplayName("generarNominaPdf con notas en blanco: omite linea de notas")
    void generarPdf_notasEnBlanco() {
        Nomina n = sampleNomina();
        n.setNotas("   ");
        when(nominaRepo.findById(1L)).thenReturn(Optional.of(n));

        assertEsPdf(service.generarNominaPdf(1L));
    }

    @Test
    @DisplayName("generarNominaPdf con notas vacias: omite linea de notas")
    void generarPdf_notasVacias() {
        Nomina n = sampleNomina();
        n.setNotas("");
        when(nominaRepo.findById(1L)).thenReturn(Optional.of(n));

        assertEsPdf(service.generarNominaPdf(1L));
    }

    @Test
    @DisplayName("generarNominaPdf con montos nulos: usa 0.00 y genera PDF")
    void generarPdf_montosNulos() {
        Nomina n = sampleNomina();
        n.setPercepciones(null);
        n.setDeducciones(null);
        n.setNetoPagar(null);
        n.setFechaPago(null);
        n.setNotas(null);
        when(nominaRepo.findById(1L)).thenReturn(Optional.of(n));

        assertEsPdf(service.generarNominaPdf(1L));
    }

    @Test
    @DisplayName("generarNominaPdf con fallo interno: IllegalStateException")
    void generarPdf_falloInterno() {
        Nomina rota = mock(Nomina.class);
        when(rota.getNotas()).thenThrow(new RuntimeException("boom"));
        when(nominaRepo.findById(1L)).thenReturn(Optional.of(rota));

        assertThatThrownBy(() -> service.generarNominaPdf(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(RuntimeException.class);
    }
}
