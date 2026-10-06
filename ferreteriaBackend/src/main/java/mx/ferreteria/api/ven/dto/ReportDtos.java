package mx.ferreteria.api.ven.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class ReportDtos {
        private ReportDtos() {
        }

        public record TopProductoResponse(
                        LocalDate mes, Long productoId, String codigo, String producto,
                        String categoria, BigDecimal unidadesVendidas, BigDecimal ingresoTotal,
                        BigDecimal costoTotal, BigDecimal utilidad, Long rankingMes, Long rankingUnidades) {
        }

        public record MejorClienteResponse(
                        LocalDate mes, Long clienteId, String cliente,
                        Long numCompras, BigDecimal totalComprado, BigDecimal ticketPromedio,
                        Long rankingMes, Long rankingHistorico) {
        }

        public record VentaTotalResponse(
                        LocalDate fecha, Long numVentas, BigDecimal subtotal, BigDecimal iva,
                        BigDecimal descuentos, BigDecimal totalVendido, BigDecimal ticketPromedio,
                        BigDecimal costoVentas, BigDecimal utilidadBruta) {
        }

        public record MejorVendedorResponse(
                        LocalDate mes, Integer usuarioId, String vendedor,
                        Long numVentas, BigDecimal totalVendido, BigDecimal ticketPromedio,
                        BigDecimal utilidadGenerada, Long rankingMes, Long rankingHistorico) {
        }

        public record VentaPorHoraResponse(
                        Integer hora, Long numVentas, BigDecimal totalAcumulado,
                        BigDecimal ticketPromedio, Long rankingHorario) {
        }

        public record MejorDiaVentaResponse(
                        Integer diaNum, String diaSemana, Long diasConVenta,
                        Long numVentas, BigDecimal totalAcumulado, BigDecimal promedioPorDia, Long ranking) {
        }

        public record ResumenDashboardResponse(
                        BigDecimal ventasEnRango, Long ticketsEnRango,
                        BigDecimal ticketPromedioEnRango,
                        BigDecimal saldoPorCobrar, BigDecimal cobranzaVencida,
                        BigDecimal valorInventario, Long productosAgotados,
                        Long promocionesActivas, Long cajasAbiertas,
                        Long devolucionesEnRango, BigDecimal totalDevueltoEnRango) {
        }

        /**
         * Narrativa del día para el dashboard ("hoy vs ayer + estrella"):
         * ventas y tickets de hoy y ayer, cambio porcentual (null si ayer
         * fue 0) y producto con más ingreso del día (null si no hubo ventas).
         */
        public record NarrativaResponse(
                        LocalDate fecha,
                        BigDecimal ventasHoy, BigDecimal ventasAyer,
                        BigDecimal cambioPct,
                        Long ticketsHoy,
                        BigDecimal ticketPromedioHoy,
                        String productoEstrella,
                        BigDecimal estrellaIngreso,
                        BigDecimal estrellaUnidades) {
        }

        public record CierreDiarioResponse(
                        LocalDate fecha, Long numCortes, Long tickets,
                        BigDecimal totalVendido, BigDecimal utilidadBruta,
                        BigDecimal margenPctPromedio, BigDecimal perdidas,
                        BigDecimal entradasEfectivo, BigDecimal salidasEfectivo,
                        BigDecimal efectivoDepositado, BigDecimal diferenciaTotal,
                        BigDecimal ingresosDigitales, Boolean todoCuadrado) {
        }

        public record ProductosSinMovimientoResponse(
                        Long productoId, String codigo, String producto,
                        String categoria, Long stock, BigDecimal costoActual, BigDecimal dineroDetenidoEnEstante,
                        LocalDate ultimaVenta, Long diasSinVender,
                        String prioridadPromocion, String imagenUrl) {
        }

        public record MejoresCategoriasResponse(
                        LocalDate mes, Long categoriaId, String categoria,
                        Long unidadesVendidas, BigDecimal ingreso, BigDecimal utilidad,
                        Long rankingMes, Long rankingHistorico) {
        }
}
