package mx.ferreteria.api.ven.pdf;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import mx.ferreteria.api.common.pdf.PdfEstilo;
import mx.ferreteria.api.ven.dto.ReportDtos;

/**
 * PDF del informe diario del dashboard (KPIs + cierre diario) con OpenPDF.
 * Función pura sobre DTOs: los datos se leen solo de BD vía
 * {@code ReporteService} antes de llamar aquí, nunca del cliente.
 * Tablas con semáforo (verde/rojo) para lectura rápida en correo.
 */
@Service
public class DashboardInformePdfService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter GENERADO =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("America/Mexico_City"));

    public byte[] generarInformePdf(ReportDtos.ResumenDashboardResponse resumen,
            List<ReportDtos.CierreDiarioResponse> cierres,
            LocalDate inicio, LocalDate fin) {
        String periodo = periodoTexto(inicio, fin);
        try (java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 36, 36, 40, 44);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            PdfEstilo.preparar(doc, writer, "Informe diario");
            doc.open();

            PdfEstilo.encabezado(doc, "Informe diario del negocio", "Periodo: " + periodo);
            doc.add(PdfEstilo.nota("Generado el " + GENERADO.format(LocalDateTime.now()
                    .atZone(ZoneId.of("America/Mexico_City")).toInstant())
                    + " (hora CDMX) · Ventas, cobranza, inventario y caja en una sola hoja de ruta."));

            PdfEstilo.seccion(doc, "Qué pasó en el periodo");
            PdfPTable kpis = PdfEstilo.tabla(new float[] { 62f, 38f },
                    new String[] { "Indicador", "Valor" });
            boolean par = false;
            agregarKpi(kpis, "Ventas en rango", PdfEstilo.moneda(resumen.ventasEnRango()), par = !par);
            agregarKpi(kpis, "Tickets", PdfEstilo.conteo(resumen.ticketsEnRango()), par = !par);
            agregarKpi(kpis, "Ticket promedio", PdfEstilo.moneda(resumen.ticketPromedioEnRango()),
                    par = !par);
            agregarKpi(kpis, "Devoluciones",
                    PdfEstilo.conteo(resumen.devolucionesEnRango())
                            + " · " + PdfEstilo.moneda(resumen.totalDevueltoEnRango()),
                    par = !par);
            agregarKpi(kpis, "Saldo por cobrar", PdfEstilo.moneda(resumen.saldoPorCobrar()),
                    par = !par);
            agregarKpi(kpis, "Cobranza vencida (prioridad)",
                    PdfEstilo.moneda(resumen.cobranzaVencida()), par = !par);
            agregarKpi(kpis, "Valor de inventario", PdfEstilo.moneda(resumen.valorInventario()),
                    par = !par);
            agregarKpi(kpis, "Productos agotados", PdfEstilo.conteo(resumen.productosAgotados()),
                    par = !par);
            agregarKpi(kpis, "Promociones activas", PdfEstilo.conteo(resumen.promocionesActivas()),
                    par = !par);
            agregarKpi(kpis, "Cajas abiertas al cierre", PdfEstilo.conteo(resumen.cajasAbiertas()),
                    par = !par);
            doc.add(kpis);

            PdfEstilo.seccion(doc, "Cierre diario — ¿cuadró la caja?");
            if (cierres == null || cierres.isEmpty()) {
                doc.add(new com.lowagie.text.Paragraph(
                        "Sin movimientos de cierre en el periodo. Si esperaba ventas, "
                                + "revise Ventas → Historial y Caja → Cortes.",
                        PdfEstilo.fuenteNormal()));
            } else {
                PdfPTable cierre = PdfEstilo.tabla(
                        new float[] { 16f, 12f, 20f, 18f, 12f, 22f },
                        new String[] { "Fecha", "Tickets", "Total vendido", "Utilidad",
                                "Margen", "Caja" });
                boolean filaPar = false;
                for (ReportDtos.CierreDiarioResponse c : cierres) {
                    cierre.addCell(PdfEstilo.celdaDatoCentrada(
                            c.fecha() != null ? FECHA.format(c.fecha()) : "—", filaPar));
                    cierre.addCell(PdfEstilo.celdaDatoCentrada(PdfEstilo.conteo(c.tickets()),
                            filaPar));
                    cierre.addCell(PdfEstilo.celdaMoneda(c.totalVendido(), filaPar));
                    cierre.addCell(PdfEstilo.celdaMoneda(c.utilidadBruta(), filaPar));
                    cierre.addCell(PdfEstilo.celdaDatoCentrada(
                            PdfEstilo.cantidad(c.margenPctPromedio()) + " %", filaPar));
                    boolean cuadrado = Boolean.TRUE.equals(c.todoCuadrado());
                    String caja = cuadrado ? "Cuadró"
                            : "Diferencia " + PdfEstilo.moneda(c.diferenciaTotal());
                    cierre.addCell(PdfEstilo.celdaSemaforo(caja, cuadrado, filaPar));
                    filaPar = !filaPar;
                }
                doc.add(cierre);
                doc.add(PdfEstilo.nota("Efectivo depositado, diferencias y montos digitales "
                        + "se concilian en Caja → Cortes. En rojo lo que pide acción hoy."));
            }

            doc.add(PdfEstilo.espacio());
            doc.add(PdfEstilo.nota("Dudas con este informe: responda al remitente o revise "
                    + "el panel en el sistema."));

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el PDF del informe diario", e);
        }
    }

    private void agregarKpi(PdfPTable tabla, String indicador, String valor, boolean par) {
        boolean alerta = indicador.toLowerCase().contains("vencida")
                || indicador.toLowerCase().contains("agotado")
                || indicador.toLowerCase().contains("abiertas");
        tabla.addCell(PdfEstilo.celdaDato(indicador, par));
        com.lowagie.text.Font fuenteValor = alerta
                ? com.lowagie.text.FontFactory.getFont(
                        com.lowagie.text.FontFactory.HELVETICA_BOLD, 9, PdfEstilo.ROJO_ALERTA)
                : com.lowagie.text.FontFactory.getFont(
                        com.lowagie.text.FontFactory.HELVETICA_BOLD, 9, PdfEstilo.GRIS_TEXTO);
        tabla.addCell(PdfEstilo.celda(valor, fuenteValor, Element.ALIGN_RIGHT,
                alerta ? PdfEstilo.FONDO_ALERTA : (par ? PdfEstilo.FONDO_FILA_ALT : null),
                com.lowagie.text.Rectangle.BOX, 4, 2));
    }

    private static String periodoTexto(LocalDate inicio, LocalDate fin) {
        if (inicio == null || fin == null) {
            return "—";
        }
        if (inicio.equals(fin)) {
            return FECHA.format(inicio);
        }
        return FECHA.format(inicio) + " al " + FECHA.format(fin);
    }

    static String moneda(BigDecimal n) {
        return PdfEstilo.moneda(n);
    }

    static String count(Number n) {
        return PdfEstilo.conteo(n);
    }
}
