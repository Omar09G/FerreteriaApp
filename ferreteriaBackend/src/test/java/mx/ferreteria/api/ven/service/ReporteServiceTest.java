package mx.ferreteria.api.ven.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import mx.ferreteria.api.ven.dto.ReportDtos.CierreDiarioResponse;
import mx.ferreteria.api.ven.dto.ReportDtos.MejorClienteResponse;
import mx.ferreteria.api.ven.dto.ReportDtos.MejorDiaVentaResponse;
import mx.ferreteria.api.ven.dto.ReportDtos.MejorVendedorResponse;
import mx.ferreteria.api.ven.dto.ReportDtos.ResumenDashboardResponse;
import mx.ferreteria.api.ven.dto.ReportDtos.TopProductoResponse;
import mx.ferreteria.api.ven.dto.ReportDtos.VentaPorHoraResponse;
import mx.ferreteria.api.ven.dto.ReportDtos.VentaTotalResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static java.util.List.of;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class ReporteServiceTest {

    @Mock JdbcTemplate jdbc;

    @InjectMocks
    ReporteService service;

    private static final LocalDate INICIO = LocalDate.of(2026, 1, 1);
    private static final LocalDate FIN = LocalDate.of(2026, 1, 31);

    private void stubLista(Object r) {
        when(jdbc.query(anyString(), any(BeanPropertyRowMapper.class), any(Object[].class)))
                .thenReturn(List.of(r));
    }

    @Test
    @DisplayName("topProductos: consulta acotada por rango y mapea a TopProductoResponse")
    void topProductos() {
        TopProductoResponse r = new TopProductoResponse(
                INICIO, 1L, "P-001", "Martillo",
                "Herramientas", new BigDecimal("120.000"),
                new BigDecimal("15000.00"), new BigDecimal("9000.00"),
                new BigDecimal("6000.00"), 1L, 1L);
        stubLista(r);

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
        stubLista(r);

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
        stubLista(r);

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
        stubLista(r);

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
        stubLista(r);

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
        stubLista(r);

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
                new BigDecimal("1800000.00"), 3L, 2L, 1L);
        when(jdbc.queryForObject(anyString(), any(BeanPropertyRowMapper.class), any(Object[].class)))
                .thenReturn(r);

        var result = service.resumenDashboard(INICIO, FIN);

        assertThat(result.ventasEnRango()).isEqualByComparingTo("15000.00");
        assertThat(result.ticketsEnRango()).isEqualTo(25L);
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
        stubLista(r);

        var result = service.cierreDiario(INICIO, FIN);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).todoCuadrado()).isTrue();
    }
}