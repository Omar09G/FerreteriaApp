package mx.ferreteria.api.notif.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import mx.ferreteria.api.notif.entity.NotificacionJob;

@ExtendWith(MockitoExtension.class)
class EmailNotificacionSenderTest {

    @Mock
    JavaMailSender mailSender;

    private MimeMessage enviado(String to, String tipo, String asunto, BigDecimal total,
            byte[] pdf) throws Exception {
        var sesion = jakarta.mail.Session.getInstance(new java.util.Properties());
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(sesion));
        new EmailNotificacionSender(mailSender).send(to, tipo, asunto, total,
                pdf, pdf == null ? null : "tickets/1.pdf");
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        // Los headers de las partes anidadas se materializan al escribir el
        // mensaje (como hace el transporte real): se simula el cable.
        var bytes = new java.io.ByteArrayOutputStream();
        captor.getValue().writeTo(bytes);
        return new MimeMessage(sesion, new java.io.ByteArrayInputStream(bytes.toByteArray()));
    }

    /** Extrae el texto de la parte text/html (recursivo: mixed/related/alternative). */
    private static String htmlDe(MimeMessage m) throws Exception {
        String html = buscarHtml(m.getContent());
        if (html == null) {
            throw new IllegalStateException("sin parte text/html");
        }
        return html;
    }

    private static String buscarHtml(Object contenido) throws Exception {
        if (contenido instanceof Multipart mp) {
            for (int i = 0; i < mp.getCount(); i++) {
                Part p = mp.getBodyPart(i);
                if (p.isMimeType("text/html")) {
                    return String.valueOf(p.getContent());
                }
                String anidado = buscarHtml(p.getContent());
                if (anidado != null) {
                    return anidado;
                }
            }
            return null;
        }
        return null;
    }

    private static boolean tieneAdjuntoPdf(MimeMessage m) throws Exception {
        Multipart mp = (Multipart) m.getContent();
        for (int i = 0; i < mp.getCount(); i++) {
            Part p = mp.getBodyPart(i);
            if (p.isMimeType("application/pdf") && "1.pdf".equals(p.getFileName())) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("ticket: HTML con marca, folio, total MXN y PDF adjunto")
    void ticket_htmlConDatos() throws Exception {
        MimeMessage m = enviado("cte@acme.mx", NotificacionJob.TIPO_VENTA_TICKET,
                "Ticket V-1", new BigDecimal("116.00"), new byte[] { 1, 2, 3 });

        assertThat(m.getSubject()).isEqualTo("Ticket V-1");
        String html = htmlDe(m);
        assertThat(html).contains("El Tornillo Feliz");
        assertThat(html).contains("Ticket V-1");
        assertThat(html).contains("116");
        assertThat(html).doesNotContain("Adjuntamos el documento solicitado.");
        assertThat(tieneAdjuntoPdf(m)).isTrue();
    }

    @Test
    @DisplayName("nómina: muestra periodo y neto a pagar")
    void nomina_htmlConNeto() throws Exception {
        MimeMessage m = enviado("emp@x.mx", NotificacionJob.TIPO_NOMINA_PAGADA,
                "2026-09-16 – 2026-09-30", new BigDecimal("4500.50"), new byte[] { 1 });

        String html = htmlDe(m);
        assertThat(html).contains("Neto a pagar");
        assertThat(html).contains("4,500");
        assertThat(tieneAdjuntoPdf(m)).isTrue();
    }

    @Test
    @DisplayName("informe: cuerpo de KPIs sin fila de total")
    void informe_sinTotal() throws Exception {
        MimeMessage m = enviado("g@x.mx", NotificacionJob.TIPO_INFORME_DASHBOARD,
                "Informe diario Ferreteria - 2026-10-05", null, new byte[] { 1 });

        String html = htmlDe(m);
        assertThat(html).contains("Informe diario del negocio");
        assertThat(html).doesNotContain(">Total<");
    }

    @Test
    @DisplayName("asunto con HTML se escapa en el cuerpo (no rompe el layout)")
    void asunto_escapado() throws Exception {
        MimeMessage m = enviado("cte@acme.mx", NotificacionJob.TIPO_VENTA_TICKET,
                "<b>V-1</b>", new BigDecimal("10"), new byte[] { 1 });

        assertThat(htmlDe(m)).contains("&lt;b&gt;V-1&lt;/b&gt;");
        // El subject sí viaja literal (lo muestra el cliente de correo).
        assertThat(m.getSubject()).isEqualTo("<b>V-1</b>");
    }

    @Test
    @DisplayName("tipo desconocido: cuerpo genérico; sin PDF no hay adjunto")
    void generico_sinPdf_ok() throws Exception {
        MimeMessage m = enviado("cte@acme.mx", "OTRO", "Aviso", null, null);

        assertThat(htmlDe(m)).contains("Tienes un documento nuevo");
        assertThat(tieneAdjuntoPdf(m)).isFalse();
    }
}
