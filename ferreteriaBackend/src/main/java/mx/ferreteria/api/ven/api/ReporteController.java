package mx.ferreteria.api.ven.api;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.common.web.RangoFechas;
import mx.ferreteria.api.ven.dto.ReportDtos;
import mx.ferreteria.api.ven.service.ReporteService;

/**
 * Reportes y dashboard consultables por periodo: TODOS los GET aceptan
 * fechaInicio/fechaFin (default: hoy) para ver un día o rango en particular.
 * Las consultas quedan acotadas por fecha (índices del día, mejor performance)
 * y los rankings se recalculan dentro del rango (ver INC-33).
 */
@RestController
@RequestMapping("/api/v1/reportes")
@RequiredArgsConstructor
@Validated
public class ReporteController {

    private final ReporteService service;

    @GetMapping("/top-productos")
    public List<ReportDtos.TopProductoResponse> topProductos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {
        var rango = rango(fechaInicio, fechaFin);
        return service.topProductos(rango.inicio(), rango.fin());
    }

    @GetMapping("/mejores-clientes")
    public List<ReportDtos.MejorClienteResponse> mejoresClientes(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {
        var rango = rango(fechaInicio, fechaFin);
        return service.mejoresClientes(rango.inicio(), rango.fin());
    }

    @GetMapping("/ventas-totales")
    public List<ReportDtos.VentaTotalResponse> ventasTotales(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {
        var rango = rango(fechaInicio, fechaFin);
        return service.ventasTotales(rango.inicio(), rango.fin());
    }

    @GetMapping("/mejores-vendedores")
    public List<ReportDtos.MejorVendedorResponse> mejoresVendedores(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {
        var rango = rango(fechaInicio, fechaFin);
        return service.mejoresVendedores(rango.inicio(), rango.fin());
    }

    @GetMapping("/horas-pico")
    public List<ReportDtos.VentaPorHoraResponse> horasPico(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {
        var rango = rango(fechaInicio, fechaFin);
        return service.ventasPorHora(rango.inicio(), rango.fin());
    }

    @GetMapping("/mejores-dias")
    public List<ReportDtos.MejorDiaVentaResponse> mejoresDias(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {
        var rango = rango(fechaInicio, fechaFin);
        return service.mejoresDiasVenta(rango.inicio(), rango.fin());
    }

    @GetMapping("/dashboard")
    public ReportDtos.ResumenDashboardResponse dashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {
        var rango = rango(fechaInicio, fechaFin);
        return service.resumenDashboard(rango.inicio(), rango.fin());
    }

    @GetMapping("/cierre-diario")
    public List<ReportDtos.CierreDiarioResponse> cierreDiario(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {
        var rango = rango(fechaInicio, fechaFin);
        return service.cierreDiario(rango.inicio(), rango.fin());
    }

    @GetMapping("/productos-sin-movimiento")
    public List<ReportDtos.ProductosSinMovimientoResponse> productosSinMovimiento() {
        return service.productosSinMovimiento();
    }

    @GetMapping("/mejores-categorias")
    public List<ReportDtos.MejoresCategoriasResponse> mejoresCategorias(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {
        var rango = rango(fechaInicio, fechaFin);
        return service.mejoresCategorias(rango.inicio(), rango.fin());
    }

    private static RangoFechas rango(LocalDate fechaInicio, LocalDate fechaFin) {
        return RangoFechas.of(fechaInicio, fechaFin);
    }
}