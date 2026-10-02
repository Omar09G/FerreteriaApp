package mx.ferreteria.api.ven.pdf;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;

import mx.ferreteria.api.ven.dto.ReportDtos;

/**
 * PDF del informe diario del dashboard (KPIs + cierre diario) con OpenPDF.
 * Función pura sobre DTOs: los datos se leen solo de BD vía
 * {@code ReporteService} antes de llamar aquí, nunca del cliente.
 */
@Service
public class DashboardInformePdfService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public byte[] generarInformePdf(ReportDtos.ResumenDashboardResponse resumen,
            List<ReportDtos.CierreDiarioResponse> cierres,
            LocalDate inicio, LocalDate fin) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 40, 40, 40, 40);
            PdfWriter.getInstance(doc, out);
            doc.open();

            Font titulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
            Font seccion = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Font normal = FontFactory.getFont(FontFactory.HELVETICA, 10);

            Paragraph t = new Paragraph("Informe diario - Panel de control", titulo);
            t.setAlignment(Element.ALIGN_CENTER);
            doc.add(t);
            Paragraph periodo = new Paragraph(
                    "Periodo: " + FECHA.format(inicio) + " al " + FECHA.format(fin), normal);
            periodo.setAlignment(Element.ALIGN_CENTER);
            doc.add(periodo);
            doc.add(new Paragraph(" ", normal));

            doc.add(new Paragraph("Indicadores", seccion));
            doc.add(new Paragraph("Ventas en rango: " + money(resumen.ventasEnRango()), normal));
            doc.add(new Paragraph("Tickets: " + count(resumen.ticketsEnRango()), normal));
            doc.add(new Paragraph("Ticket promedio: " + money(resumen.ticketPromedioEnRango()), normal));
            doc.add(new Paragraph("Devoluciones: " + count(resumen.devolucionesEnRango())
                    + " (" + money(resumen.totalDevueltoEnRango()) + ")", normal));
            doc.add(new Paragraph("Saldo por cobrar: " + money(resumen.saldoPorCobrar()), normal));
            doc.add(new Paragraph("Cobranza vencida: " + money(resumen.cobranzaVencida()), normal));
            doc.add(new Paragraph("Valor de inventario: " + money(resumen.valorInventario()), normal));
            doc.add(new Paragraph("Productos agotados: " + count(resumen.productosAgotados()), normal));
            doc.add(new Paragraph("Promociones activas: " + count(resumen.promocionesActivas()), normal));
            doc.add(new Paragraph("Cajas abiertas: " + count(resumen.cajasAbiertas()), normal));
            doc.add(new Paragraph(" ", normal));

            doc.add(new Paragraph("Cierre diario", seccion));
            if (cierres == null || cierres.isEmpty()) {
                doc.add(new Paragraph("Sin movimientos de cierre en el periodo.", normal));
            } else {
                for (ReportDtos.CierreDiarioResponse c : cierres) {
                    doc.add(new Paragraph("Fecha: " + (c.fecha() != null ? FECHA.format(c.fecha()) : "-")
                            + " - Tickets: " + count(c.tickets())
                            + " - Total: " + money(c.totalVendido()), normal));
                    doc.add(new Paragraph("  Utilidad: " + money(c.utilidadBruta())
                            + " - Margen: " + money(c.margenPctPromedio()) + "%"
                            + " - Efectivo depositado: " + money(c.efectivoDepositado())
                            + " - Diferencia: " + money(c.diferenciaTotal())
                            + " - Cuadrado: " + (Boolean.TRUE.equals(c.todoCuadrado()) ? "Si" : "No"),
                            normal));
                }
            }

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el PDF del informe diario", e);
        }
    }

    private static String money(BigDecimal n) {
        if (n == null) {
            return "0.00";
        }
        return String.format(java.util.Locale.US, "%.2f", n);
    }

    private static String count(Number n) {
        return n == null ? "0" : String.valueOf(n.longValue());
    }
}
