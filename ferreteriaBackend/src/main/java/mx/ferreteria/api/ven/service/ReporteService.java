package mx.ferreteria.api.ven.service;

import java.util.List;

import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.ven.dto.ReportDtos;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReporteService {

    private final JdbcTemplate jdbc;

    public List<ReportDtos.TopProductoResponse> topProductos() {
        return jdbc.query(
            "SELECT * FROM ven.vw_top_productos ORDER BY ranking_mes LIMIT 20",
            new BeanPropertyRowMapper<>(ReportDtos.TopProductoResponse.class));
    }

    public List<ReportDtos.MejorClienteResponse> mejoresClientes() {
        return jdbc.query(
            "SELECT * FROM ven.vw_mejores_clientes ORDER BY ranking_mes LIMIT 20",
            new BeanPropertyRowMapper<>(ReportDtos.MejorClienteResponse.class));
    }

    public List<ReportDtos.VentaTotalResponse> ventasTotales() {
        return jdbc.query(
            "SELECT * FROM ven.vw_ventas_totales ORDER BY fecha DESC LIMIT 30",
            new BeanPropertyRowMapper<>(ReportDtos.VentaTotalResponse.class));
    }

    public List<ReportDtos.MejorVendedorResponse> mejoresVendedores() {
        return jdbc.query(
            "SELECT * FROM ven.vw_mejores_vendedores ORDER BY ranking_mes LIMIT 20",
            new BeanPropertyRowMapper<>(ReportDtos.MejorVendedorResponse.class));
    }

    public List<ReportDtos.VentaPorHoraResponse> ventasPorHora() {
        return jdbc.query(
            "SELECT * FROM ven.vw_ventas_por_hora ORDER BY hora",
            new BeanPropertyRowMapper<>(ReportDtos.VentaPorHoraResponse.class));
    }

    public List<ReportDtos.MejorDiaVentaResponse> mejoresDiasVenta() {
        return jdbc.query(
            "SELECT * FROM ven.vw_mejores_dias_venta ORDER BY ranking",
            new BeanPropertyRowMapper<>(ReportDtos.MejorDiaVentaResponse.class));
    }

    public ReportDtos.ResumenDashboardResponse resumenDashboard() {
        return jdbc.queryForObject(
            "SELECT * FROM ven.vw_resumen_dashboard",
            new BeanPropertyRowMapper<>(ReportDtos.ResumenDashboardResponse.class));
    }

    public List<ReportDtos.CierreDiarioResponse> cierreDiario() {
        return jdbc.query(
            "SELECT * FROM fin.vw_cierre_diario ORDER BY fecha DESC LIMIT 30",
            new BeanPropertyRowMapper<>(ReportDtos.CierreDiarioResponse.class));
    }
}
