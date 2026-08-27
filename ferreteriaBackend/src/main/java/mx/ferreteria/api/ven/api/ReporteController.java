package mx.ferreteria.api.ven.api;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.ven.dto.ReportDtos;
import mx.ferreteria.api.ven.service.ReporteService;

@RestController
@RequestMapping("/api/v1/reportes")
@RequiredArgsConstructor
@Validated
public class ReporteController {

    private final ReporteService service;

    @GetMapping("/top-productos")
    public List<ReportDtos.TopProductoResponse> topProductos() {
        return service.topProductos();
    }

    @GetMapping("/mejores-clientes")
    public List<ReportDtos.MejorClienteResponse> mejoresClientes() {
        return service.mejoresClientes();
    }

    @GetMapping("/ventas-totales")
    public List<ReportDtos.VentaTotalResponse> ventasTotales() {
        return service.ventasTotales();
    }

    @GetMapping("/mejores-vendedores")
    public List<ReportDtos.MejorVendedorResponse> mejoresVendedores() {
        return service.mejoresVendedores();
    }

    @GetMapping("/horas-pico")
    public List<ReportDtos.VentaPorHoraResponse> horasPico() {
        return service.ventasPorHora();
    }

    @GetMapping("/mejores-dias")
    public List<ReportDtos.MejorDiaVentaResponse> mejoresDias() {
        return service.mejoresDiasVenta();
    }

    @GetMapping("/dashboard")
    public ReportDtos.ResumenDashboardResponse dashboard() {
        return service.resumenDashboard();
    }

    @GetMapping("/cierre-diario")
    public List<ReportDtos.CierreDiarioResponse> cierreDiario() {
        return service.cierreDiario();
    }
}
