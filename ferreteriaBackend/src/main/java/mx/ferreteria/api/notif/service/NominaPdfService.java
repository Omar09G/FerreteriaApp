package mx.ferreteria.api.notif.service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.rh.entity.Nomina;
import mx.ferreteria.api.rh.repo.NominaRepository;

/**
 * Recibo PDF de nómina pagada (OpenPDF). Datos solo de BD.
 */
@Service
@RequiredArgsConstructor
public class NominaPdfService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter
            .ofPattern("dd/MM/yyyy")
            .withZone(ZoneId.of("America/Mexico_City"));

    private final NominaRepository nominaRepo;

    @Transactional(readOnly = true)
    public byte[] generarNominaPdf(Long nominaId) {
        Nomina n = nominaRepo.findById(nominaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 40, 40, 40, 40);
            PdfWriter.getInstance(doc, out);
            doc.open();

            Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Font normal = FontFactory.getFont(FontFactory.HELVETICA, 10);

            Paragraph t = new Paragraph("Recibo de Nómina", bold);
            t.setAlignment(Element.ALIGN_CENTER);
            doc.add(t);
            doc.add(new Paragraph(" ", normal));
            doc.add(new Paragraph("Empleado ID: " + n.getEmpleadoId(), normal));
            doc.add(new Paragraph("Periodo: " + n.getPeriodoIni() + " al " + n.getPeriodoFin(), normal));
            doc.add(new Paragraph("Días pagados: " + n.getDiasPagados(), normal));
            doc.add(new Paragraph("Percepciones: " + money(n.getPercepciones()), normal));
            doc.add(new Paragraph("Deducciones: " + money(n.getDeducciones()), normal));
            Paragraph neto = new Paragraph("Neto a pagar: " + money(n.getNetoPagar()),
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11));
            neto.setAlignment(Element.ALIGN_RIGHT);
            doc.add(neto);
            doc.add(new Paragraph(" ", normal));
            doc.add(new Paragraph("Estado: " + n.getEstado(), normal));
            if (n.getFechaPago() != null) {
                doc.add(new Paragraph("Fecha de pago: " + FECHA.format(n.getFechaPago()), normal));
            }
            if (n.getNotas() != null && !n.getNotas().isBlank()) {
                doc.add(new Paragraph("Notas: " + n.getNotas(), normal));
            }
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el PDF de nómina", e);
        }
    }

    private static String money(BigDecimal n) {
        if (n == null) {
            return "0.00";
        }
        return String.format(java.util.Locale.US, "%.2f", n);
    }
}
