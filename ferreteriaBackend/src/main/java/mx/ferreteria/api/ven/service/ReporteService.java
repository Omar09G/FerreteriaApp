package mx.ferreteria.api.ven.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.ven.dto.ReportDtos;
import mx.ferreteria.api.ven.repo.ReporteRepository;

/**
 * Reportes y dashboard (PLAN §16/§21). Todas las consultas reciben rango
 * [fechaInicio, fechaFin] (default: hoy) para consultar operaciones de un día
 * o periodo en específico y acotar la lectura a índices por fecha
 * (performance). Los rankings se recalculan DENTRO del rango solicitado.
 *
 * Las queries nativas viven en {@link ReporteRepository}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReporteService {

    private final ReporteRepository reportRepo;

    public List<ReportDtos.TopProductoResponse> topProductos(LocalDate inicio, LocalDate fin) {
        return reportRepo.findTopProductos(inicio, fin);
    }

    public List<ReportDtos.MejorClienteResponse> mejoresClientes(LocalDate inicio, LocalDate fin) {
        return reportRepo.findMejoresClientes(inicio, fin);
    }

    public List<ReportDtos.VentaTotalResponse> ventasTotales(LocalDate inicio, LocalDate fin) {
        return reportRepo.findVentasTotales(inicio, fin);
    }

    public List<ReportDtos.MejorVendedorResponse> mejoresVendedores(LocalDate inicio, LocalDate fin) {
        return reportRepo.findMejoresVendedores(inicio, fin);
    }

    public List<ReportDtos.VentaPorHoraResponse> ventasPorHora(LocalDate inicio, LocalDate fin) {
        return reportRepo.findVentasPorHora(inicio, fin);
    }

    public List<ReportDtos.MejorDiaVentaResponse> mejoresDiasVenta(LocalDate inicio, LocalDate fin) {
        return reportRepo.findMejoresDiasVenta(inicio, fin);
    }

    public ReportDtos.ResumenDashboardResponse resumenDashboard(LocalDate inicio, LocalDate fin) {
        return reportRepo.findResumenDashboard(inicio, fin);
    }

    public List<ReportDtos.CierreDiarioResponse> cierreDiario(LocalDate inicio, LocalDate fin) {
        return reportRepo.findCierreDiario(inicio, fin);
    }

    public List<ReportDtos.ProductosSinMovimientoResponse> productosSinMovimiento() {
        return reportRepo.findProductosSinMovimiento();
    }

    public List<ReportDtos.MejoresCategoriasResponse> mejoresCategorias(LocalDate inicio, LocalDate fin) {
        return reportRepo.findMejoresCategorias(inicio, fin);
    }
}