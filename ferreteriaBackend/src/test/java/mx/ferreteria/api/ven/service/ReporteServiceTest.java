package mx.ferreteria.api.ven.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import mx.ferreteria.api.ven.dto.ReportDtos.CierreDiarioResponse;
import mx.ferreteria.api.ven.dto.ReportDtos.MejorClienteResponse;
import mx.ferreteria.api.ven.dto.ReportDtos.MejorDiaVentaResponse;
import mx.ferreteria.api.ven.dto.ReportDtos.MejorVendedorResponse;
import mx.ferreteria.api.ven.dto.ReportDtos.ResumenDashboardResponse;
import mx.ferreteria.api.ven.dto.ReportDtos.TopProductoResponse;
import mx.ferreteria.api.ven.dto.ReportDtos.VentaPorHoraResponse;
import mx.ferreteria.api.ven.dto.ReportDtos.VentaTotalResponse;
import mx.ferreteria.api.ven.repo.ReporteRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReporteServiceTest {

    @Mock ReporteRepository reportRepo;

    @InjectMocks
    ReporteService service;

    private static final LocalDate INICIO = LocalDate.of(2026, 1, 1);
    private static final LocalDate FIN = LocalDate.of(2026, 1, 31);

    @Test
    @DisplayName("topProductos: consulta acotada por rango y mapea a TopProductoResponse")
    void topProductos() {
        TopProductoResponse r = new TopProductoResponse(
                INICIO, 1L, "P-001", "Martillo",
                "Herramientas", new BigDecimal("120.000"),
                new BigDecimal("15000.00"), new BigDecimal("9000.00"),
                new BigDecimal("6000.00"), 1L, 1L);
        doReturn(List.of(r)).when(reportRepo).findTopProductos(INICIO, FIN);

        var result = service.topProductos(INICIO, FIN);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).producto()).isEqualTo("Martillo");
        assertThat(result.get(0).rankingMes()).isEqualTo(1L);
    }

    @Test
    @DisplayName("mejoresClientes: mapea consulta por rango")
    void mejoresClientes() {
        MejorClienteResponse r = new MejorClienteResponse(
                INICIO, 1L, "Cliente A", 10L,
                new BigDecimal("50000.00"), new BigDecimal("5000.00"), 1L, 1L);
        doReturn(List.of(r)).when(reportRepo).findMejoresClientes(INICIO, FIN);

        var result = service.mejoresClientes(INICIO, FIN);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).cliente()).isEqualTo("Cliente A");
    }

    @Test
    @DisplayName("ventasTotales: mapea vista acotada por rango")
    void ventasTotales() {
        VentaTotalResponse r = new VentaTotalResponse(
                INICIO, 25L, new BigDecimal("25000.00"),
                new BigDecimal("4000.00"), new BigDecimal("500.00"),
                new BigDecimal("28500.00"), new BigDecimal("1140.00"),
                new BigDecimal("15000.00"), new BigDecimal("10000.00"));
        doReturn(List.of(r)).when(reportRepo).findVentasTotales(INICIO, FIN);

        var result = service.ventasTotales(INICIO, FIN);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).numVentas()).isEqualTo(25L);
    }

    @Test
    @DisplayName("mejoresVendedores: mapea consulta por rango")
    void mejoresVendedores() {
        MejorVendedorResponse r = new MejorVendedorResponse(
                INICIO, 1, "Juan Perez", 30L,
                new BigDecimal("60000.00"), new BigDecimal("2000.00"),
                new BigDecimal("30000.00"), 1L, 1L);
        doReturn(List.of(r)).when(reportRepo).findMejoresVendedores(INICIO, FIN);

        var result = service.mejoresVendedores(INICIO, FIN);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).vendedor()).isEqualTo("Juan Perez");
    }

    @Test
    @DisplayName("ventasPorHora: mapea consulta por rango")
    void ventasPorHora() {
        VentaPorHoraResponse r = new VentaPorHoraResponse(
                17, 20L, new BigDecimal("30000.00"),
                new BigDecimal("1500.00"), 1L);
        doReturn(List.of(r)).when(reportRepo).findVentasPorHora(INICIO, FIN);

        var result = service.ventasPorHora(INICIO, FIN);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).hora()).isEqualTo(17);
    }

    @Test
    @DisplayName("mejoresDiasVenta: mapea consulta por rango")
    void mejoresDiasVenta() {
        MejorDiaVentaResponse r = new MejorDiaVentaResponse(
                6, "Sabado", 4L, 25L,
                new BigDecimal("20000.00"), new BigDecimal("5000.00"), 1L);
        doReturn(List.of(r)).when(reportRepo).findMejoresDiasVenta(INICIO, FIN);

        var result = service.mejoresDiasVenta(INICIO, FIN);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).diaSemana()).isEqualTo("Sabado");
    }

    @Test
    @DisplayName("resumenDashboard: KPIs en rango + posición actual (mapea)")
    void resumenDashboard() {
        ResumenDashboardResponse r = new ResumenDashboardResponse(
                new BigDecimal("15000.00"), 25L, new BigDecimal("800.00"),
                new BigDecimal("40000.00"), new BigDecimal("5000.00"),
                new BigDecimal("1800000.00"), 3L, 2L, 1L, 2L,
                new BigDecimal("150.00"));
        doReturn(r).when(reportRepo).findResumenDashboard(INICIO, FIN);

        var result = service.resumenDashboard(INICIO, FIN);

        assertThat(result.ventasEnRango()).isEqualByComparingTo("15000.00");
        assertThat(result.ticketsEnRango()).isEqualTo(25L);
    }

    @Test
    @DisplayName("narrativa: compara hoy vs ayer y rescata la estrella")
    void narrativa() {
        var hoy = new ResumenDashboardResponse(
                new BigDecimal("12300.00"), 23L, new BigDecimal("534.78"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0L, 0L, 0L, 0L,
                BigDecimal.ZERO);
        var ayer = new ResumenDashboardResponse(
                new BigDecimal("10000.00"), 20L, new BigDecimal("500.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0L, 0L, 0L, 0L,
                BigDecimal.ZERO);
        var estrella = new TopProductoResponse(
                LocalDate.of(2026, 10, 5), 1L, "CEM-01", "Cemento Tolteca",
                "Materiales", new BigDecimal("40.000"), new BigDecimal("5000.00"),
                new BigDecimal("4000.00"), new BigDecimal("1000.00"), 1L, 1L);
        doReturn(hoy).when(reportRepo).findResumenDashboard(LocalDate.of(2026, 10, 5),
                LocalDate.of(2026, 10, 5));
        doReturn(ayer).when(reportRepo).findResumenDashboard(LocalDate.of(2026, 10, 4),
                LocalDate.of(2026, 10, 4));
        doReturn(java.util.List.of(estrella)).when(reportRepo)
                .findTopProductos(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 5));

        var result = service.narrativa(LocalDate.of(2026, 10, 5));

        assertThat(result.ventasHoy()).isEqualByComparingTo("12300.00");
        assertThat(result.ventasAyer()).isEqualByComparingTo("10000.00");
        assertThat(result.cambioPct()).isEqualByComparingTo("23.0");
        assertThat(result.ticketsHoy()).isEqualTo(23L);
        assertThat(result.productoEstrella()).isEqualTo("Cemento Tolteca");
        assertThat(result.estrellaIngreso()).isEqualByComparingTo("5000.00");
    }

    @Test
    @DisplayName("narrativa sin ventas ayer: cambioPct null; sin ventas hoy: sin estrella")
    void narrativa_bordes() {
        var cero = new ResumenDashboardResponse(
                BigDecimal.ZERO, 0L, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0L, 0L, 0L, 0L,
                BigDecimal.ZERO);
        doReturn(cero).when(reportRepo).findResumenDashboard(
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 5));
        doReturn(cero).when(reportRepo).findResumenDashboard(
                LocalDate.of(2026, 10, 4), LocalDate.of(2026, 10, 4));
        doReturn(java.util.List.of()).when(reportRepo)
                .findTopProductos(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 5));

        var result = service.narrativa(LocalDate.of(2026, 10, 5));

        assertThat(result.cambioPct()).isNull();
        assertThat(result.productoEstrella()).isNull();
        assertThat(result.ventasHoy()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("cierreDiario: mapea vista acotada por rango")
    void cierreDiario() {
        CierreDiarioResponse r = new CierreDiarioResponse(
                INICIO, 2L, 40L,
                new BigDecimal("45000.00"), new BigDecimal("18000.00"),
                new BigDecimal("40.00"), new BigDecimal("500.00"),
                new BigDecimal("40000.00"), new BigDecimal("1000.00"),
                new BigDecimal("39000.00"), BigDecimal.ZERO,
                new BigDecimal("5000.00"), true);
        doReturn(List.of(r)).when(reportRepo).findCierreDiario(INICIO, FIN);

        var result = service.cierreDiario(INICIO, FIN);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).todoCuadrado()).isTrue();
    }
}