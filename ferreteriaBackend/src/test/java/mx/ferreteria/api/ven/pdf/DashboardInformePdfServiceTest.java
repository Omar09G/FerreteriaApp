package mx.ferreteria.api.ven.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import mx.ferreteria.api.ven.dto.ReportDtos;

class DashboardInformePdfServiceTest {

    private final DashboardInformePdfService pdf = new DashboardInformePdfService();

    @Test
    @DisplayName("genera PDF no vacío con KPIs y cierre")
    void generarInformePdf_contenidoOk() {
        var resumen = new ReportDtos.ResumenDashboardResponse(
                new BigDecimal("15000.00"), 25L, new BigDecimal("600.00"),
                new BigDecimal("40000.00"), new BigDecimal("5000.00"),
                new BigDecimal("1800000.00"), 3L, 2L, 1L, 2L,
                new BigDecimal("150.00"));
        var cierre = new ReportDtos.CierreDiarioResponse(
                LocalDate.of(2026, 10, 2), 2L, 40L,
                new BigDecimal("45000.00"), new BigDecimal("18000.00"),
                new BigDecimal("40.00"), new BigDecimal("500.00"),
                new BigDecimal("40000.00"), new BigDecimal("1000.00"),
                new BigDecimal("39000.00"), BigDecimal.ZERO,
                new BigDecimal("5000.00"), true);

        byte[] bytes = pdf.generarInformePdf(resumen, List.of(cierre),
                LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 2));

        assertThat(bytes).isNotEmpty();
        assertThat(new String(bytes, 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("genera PDF con lista de cierres vacía")
    void generarInformePdf_sinCierres_ok() {
        var resumen = new ReportDtos.ResumenDashboardResponse(
                BigDecimal.ZERO, 0L, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, 0L, 0L, 0L, 0L, BigDecimal.ZERO);

        byte[] bytes = pdf.generarInformePdf(resumen, List.of(),
                LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 2));

        assertThat(bytes).isNotEmpty();
    }
}
