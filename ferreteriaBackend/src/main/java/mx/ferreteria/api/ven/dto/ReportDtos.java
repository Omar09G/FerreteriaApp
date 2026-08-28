package mx.ferreteria.api.ven.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.*;

public final class ReportDtos {
    private ReportDtos() {}

    public record TopProductoResponse(
        LocalDate mes, Long productoId, String codigo, String producto,
        String categoria, BigDecimal unidadesVendidas, BigDecimal ingresoTotal,
        BigDecimal costoTotal, BigDecimal utilidad, Long rankingMes, Long rankingUnidades
    ) {}

    public record MejorClienteResponse(
        LocalDate mes, Long clienteId, String cliente,
        Long numCompras, BigDecimal totalComprado, BigDecimal ticketPromedio,
        Long rankingMes, Long rankingHistorico
    ) {}

    public record VentaTotalResponse(
        LocalDate fecha, Long numVentas, BigDecimal subtotal, BigDecimal iva,
        BigDecimal descuentos, BigDecimal totalVendido, BigDecimal ticketPromedio,
        BigDecimal costoVentas, BigDecimal utilidadBruta
    ) {}

    public record MejorVendedorResponse(
        LocalDate mes, Integer usuarioId, String vendedor,
        Long numVentas, BigDecimal totalVendido, BigDecimal ticketPromedio,
        BigDecimal utilidadGenerada, Long rankingMes, Long rankingHistorico
    ) {}

    public record VentaPorHoraResponse(
        Integer hora, Long numVentas, BigDecimal totalAcumulado,
        BigDecimal ticketPromedio, Long rankingHorario
    ) {}

    public record MejorDiaVentaResponse(
        Integer diaNum, String diaSemana, Long diasConVenta,
        Long numVentas, BigDecimal totalAcumulado, BigDecimal promedioPorDia, Long ranking
    ) {}

    public record ResumenDashboardResponse(
        BigDecimal ventasEnRango, Long ticketsEnRango,
        BigDecimal ticketPromedioEnRango,
        BigDecimal saldoPorCobrar, BigDecimal cobranzaVencida,
        BigDecimal valorInventario, Long productosAgotados,
        Long promocionesActivas, Long cajasAbiertas
    ) {}

    public record CierreDiarioResponse(
        LocalDate fecha, Long numCortes, Long tickets,
        BigDecimal totalVendido, BigDecimal utilidadBruta,
        BigDecimal margenPctPromedio, BigDecimal perdidas,
        BigDecimal entradasEfectivo, BigDecimal salidasEfectivo,
        BigDecimal efectivoDepositado, BigDecimal diferenciaTotal,
        BigDecimal ingresosDigitales, Boolean todoCuadrado
    ) {}
}
