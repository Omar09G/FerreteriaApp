package mx.ferreteria.api.notif.service;

import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.common.pdf.PdfEstilo;
import mx.ferreteria.api.rh.entity.Nomina;
import mx.ferreteria.api.rh.repo.NominaRepository;

/**
 * Recibo PDF de nómina pagada (OpenPDF). Datos solo de BD.
 * Ficha del periodo + tabla de percepciones/deducciones/neto para
 * que el monto final se entienda de un vistazo en el correo.
 */
@Service
@RequiredArgsConstructor
public class NominaPdfService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter
            .ofPattern("dd/MM/yyyy")
            .withZone(ZoneId.of("America/Mexico_City"));
    private static final DateTimeFormatter PERIODO = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final NominaRepository nominaRepo;

    @Transactional(readOnly = true)
    public byte[] generarNominaPdf(Long nominaId) {
        Nomina n = nominaRepo.findById(nominaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        String periodo = periodoTexto(n);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 36, 36, 40, 44);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            PdfEstilo.preparar(doc, writer, "Recibo de nómina");
            doc.open();

            PdfEstilo.encabezado(doc, "Recibo de nómina", "Periodo: " + periodo);
            PdfEstilo.ficha(doc, new String[][] {
                    { "Empleado", "#" + n.getEmpleadoId() },
                    { "Periodo", periodo },
                    { "Días pagados", PdfEstilo.cantidad(n.getDiasPagados()) },
                    { "Estado", PdfEstilo.texto(n.getEstado(), "—") },
                    { "Fecha de pago", n.getFechaPago() != null
                            ? FECHA.format(n.getFechaPago()) : "Pendiente de pago" },
            });

            PdfEstilo.seccion(doc, "Desglose del pago");
            PdfPTable tabla = PdfEstilo.tabla(new float[] { 60f, 40f },
                    new String[] { "Concepto", "Monto" });
            tabla.addCell(PdfEstilo.celdaDato("Percepciones (sueldo + extras)", false));
            tabla.addCell(PdfEstilo.celdaMoneda(n.getPercepciones(), false));
            tabla.addCell(PdfEstilo.celdaDato("Deducciones (impuestos, préstamos…)", true));
            tabla.addCell(PdfEstilo.celda(
                    "− " + PdfEstilo.moneda(n.getDeducciones()),
                    com.lowagie.text.FontFactory.getFont(
                            com.lowagie.text.FontFactory.HELVETICA_BOLD, 9,
                            PdfEstilo.ROJO_ALERTA),
                    Element.ALIGN_RIGHT, PdfEstilo.FONDO_FILA_ALT, Rectangle.BOX, 4, 2));
            doc.add(tabla);
            doc.add(PdfEstilo.totalDestacado("Neto a pagar", n.getNetoPagar()));

            if (n.getNotas() != null && !n.getNotas().isBlank()) {
                PdfEstilo.seccion(doc, "Notas");
                doc.add(new Paragraph(n.getNotas(), PdfEstilo.fuenteNormal()));
            }

            doc.add(PdfEstilo.espacio());
            doc.add(PdfEstilo.nota("Si el monto no coincide con tu pago, contacta a "
                    + "Recursos Humanos con este recibo a la mano."));

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el PDF de nómina", e);
        }
    }

    private static String periodoTexto(Nomina n) {
        String ini = n.getPeriodoIni() != null ? PERIODO.format(n.getPeriodoIni()) : "—";
        String fin = n.getPeriodoFin() != null ? PERIODO.format(n.getPeriodoFin()) : "—";
        return ini + " al " + fin;
    }
}
