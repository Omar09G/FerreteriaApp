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

    @Test
    @DisplayName("stock bajo: resumen en el cuerpo y Excel adjunto")
    void stockBajo_excelAdjunto() throws Exception {
        var sesion = jakarta.mail.Session.getInstance(new java.util.Properties());
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(sesion));
        new EmailNotificacionSender(mailSender).sendStockBajo("g@x.mx",
                java.time.LocalDate.of(2026, 10, 5), 10, 3, 2,
                new byte[] { 1, 2, 3 }, "stock-bajo-2026-10-05.xlsx");
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        var bytes = new java.io.ByteArrayOutputStream();
        captor.getValue().writeTo(bytes);
        MimeMessage m = new MimeMessage(sesion,
                new java.io.ByteArrayInputStream(bytes.toByteArray()));

        assertThat(m.getSubject()).contains("10 productos").contains("3 agotados");
        String html = htmlDe(m);
        assertThat(html).contains("Productos en bajo stock");
        assertThat(html).contains("Agotados");
        Multipart mp = (Multipart) m.getContent();
        boolean xlsx = false;
        for (int i = 0; i < mp.getCount(); i++) {
            Part p = mp.getBodyPart(i);
            if (p.isMimeType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    && "stock-bajo-2026-10-05.xlsx".equals(p.getFileName())) {
                xlsx = true;
            }
        }
        assertThat(xlsx).isTrue();
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
    @DisplayName("turnos abiertos: tabla de cajas sin PDF")
    void turnos_tabla() throws Exception {
        var sesion = jakarta.mail.Session.getInstance(new java.util.Properties());
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(sesion));
        var turnos = java.util.List.of(
                new mx.ferreteria.api.fin.dto.FinDtos.TurnoCajaResponse(1L, 1, "Caja 1",
                        7, java.time.Instant.now().minusSeconds(7200),
                        new java.math.BigDecimal("1000.00"), null, null, null, null,
                        "ABIERTO", null));
        new EmailNotificacionSender(mailSender).sendTurnoAbierto("g@x.mx",
                java.time.LocalDate.of(2026, 10, 5), turnos);
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        var bytes = new java.io.ByteArrayOutputStream();
        captor.getValue().writeTo(bytes);
        MimeMessage m = new MimeMessage(sesion,
                new java.io.ByteArrayInputStream(bytes.toByteArray()));

        assertThat(m.getSubject()).contains("1 caja abierta");
        String html = htmlDe(m);
        assertThat(html).contains("Caja 1");
        assertThat(html).contains("1,000");
        assertThat(tieneAdjuntoPdf(m)).isFalse();
    }

    @Test
    @DisplayName("tipo desconocido: cuerpo genérico; sin PDF no hay adjunto")
    void generico_sinPdf_ok() throws Exception {
        MimeMessage m = enviado("cte@acme.mx", "OTRO", "Aviso", null, null);

        assertThat(htmlDe(m)).contains("Tienes un documento nuevo");
        assertThat(tieneAdjuntoPdf(m)).isFalse();
    }

    private MimeMessage enviadoTablas(String metodo, Object... args) throws Exception {
        var sesion = jakarta.mail.Session.getInstance(new java.util.Properties());
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(sesion));
        var sender = new EmailNotificacionSender(mailSender);
        if ("cobranza".equals(metodo)) {
            sender.sendCobranza("g@x.mx", java.time.LocalDate.of(2026, 10, 5),
                    (java.util.List<mx.ferreteria.api.ven.dto.VenDtos.CuentaCobrarResponse>) args[0],
                    (java.util.List<mx.ferreteria.api.ven.dto.VenDtos.CuentaCobrarResponse>) args[1]);
        } else {
            sender.sendRentas("g@x.mx", java.time.LocalDate.of(2026, 10, 5),
                    (java.util.List<mx.ferreteria.api.ven.dto.VenDtos.RentaResponse>) args[0],
                    (java.util.List<mx.ferreteria.api.ven.dto.VenDtos.RentaResponse>) args[1]);
        }
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        var bytes = new java.io.ByteArrayOutputStream();
        captor.getValue().writeTo(bytes);
        return new MimeMessage(sesion, new java.io.ByteArrayInputStream(bytes.toByteArray()));
    }

    private static mx.ferreteria.api.ven.dto.VenDtos.CuentaCobrarResponse cuentaCobrar(
            String cliente, String saldo, java.time.LocalDate vto) {
        return new mx.ferreteria.api.ven.dto.VenDtos.CuentaCobrarResponse(1L, 10L, "V-1",
                5L, cliente, new BigDecimal("1000.00"), BigDecimal.ZERO,
                new BigDecimal(saldo), vto, "VIGENTE", java.time.Instant.now(),
                java.util.List.of());
    }

    private static mx.ferreteria.api.ven.dto.VenDtos.RentaResponse renta(
            String estado, java.time.LocalDate dev) {
        return new mx.ferreteria.api.ven.dto.VenDtos.RentaResponse(1L, "R-1", 5L,
                "Cliente A", 1, "Central", java.time.Instant.now(), dev, null,
                new BigDecimal("200.00"), new BigDecimal("600.00"), 1, null,
                estado, 7, java.util.List.of());
    }

    @Test
    @DisplayName("cobranza: tablas de vencidas y pendientes con saldos, sin PDF")
    void cobranza_tablas() throws Exception {
        MimeMessage m = enviadoTablas("cobranza",
                java.util.List.of(cuentaCobrar("Cliente A", "500.00",
                        java.time.LocalDate.of(2026, 9, 20))),
                java.util.List.of(cuentaCobrar("Cliente B", "300.00",
                        java.time.LocalDate.of(2026, 10, 20))));

        assertThat(m.getSubject()).contains("1 vencidas").contains("1 pendientes");
        String html = htmlDe(m);
        assertThat(html).contains("Cliente A");
        assertThat(html).contains("Cliente B");
        assertThat(html).contains("500");
        assertThat(tieneAdjuntoPdf(m)).isFalse();
    }

    @Test
    @DisplayName("rentas: vencidas en rojo y próximas, con depósito")
    void rentas_tablas() throws Exception {
        MimeMessage m = enviadoTablas("rentas",
                java.util.List.of(renta("VENCIDA", java.time.LocalDate.of(2026, 10, 1))),
                java.util.List.of(renta("ABIERTA", java.time.LocalDate.of(2026, 10, 7))));

        assertThat(m.getSubject()).contains("1 vencidas").contains("1 próximas");
        String html = htmlDe(m);
        assertThat(html).contains("Cliente A");
        assertThat(html).contains("R-1");
        assertThat(tieneAdjuntoPdf(m)).isFalse();
    }
}
