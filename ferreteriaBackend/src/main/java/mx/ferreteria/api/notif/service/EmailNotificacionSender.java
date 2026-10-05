package mx.ferreteria.api.notif.service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.ferreteria.api.common.error.ValidacionException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.common.mail.EmailPlantilla;
import mx.ferreteria.api.notif.entity.NotificacionJob;

/**
 * Email transaccional con PDF adjunto (JavaMail) y cuerpo HTML con la
 * plantilla de marca {@link EmailPlantilla}. En dev apunta a Mailpit (:1025).
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.notif.enabled", havingValue = "true")
public class EmailNotificacionSender {

    private final JavaMailSender mailSender;

    /**
     * Envía el documento con cuerpo según el tipo (ticket, nómina, informe).
     *
     * @param tipo   uno de {@code NotificacionJob.TIPO_*} (null = genérico).
     * @param asunto folio, periodo o rango (se muestra como dato, escapado).
     * @param total  monto asociado (puede ser null: se omite la fila).
     */
    public void send(String to, String tipo, String asunto, BigDecimal total,
            byte[] pdf, String clave) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(asunto != null ? asunto : "Notificación Ferretería");
            helper.setText(textoPlano(tipo, asunto, total),
                    html(tipo, asunto, total));
            if (pdf != null && clave != null) {
                String nombre = clave.contains("/")
                        ? clave.substring(clave.lastIndexOf('/') + 1)
                        : clave;
                helper.addAttachment(nombre, new ByteArrayResource(pdf), "application/pdf");
            }
            mailSender.send(message);
            log.info("email enviado to={} tipo={}", to, tipo);
        } catch (Exception e) {
            throw new ValidacionException(ErrorCode.SERVICIO_NO_DISPONIBLE);
        }
    }

    /**
     * Recordatorio de cuentas por pagar (sin PDF): tablas de vencidas (rojo)
     * y pendientes con totales. Máximo 50 filas por sección + nota de resto.
     */
    public void sendCuentasPagar(String to, java.time.LocalDate fecha,
            java.util.List<mx.ferreteria.api.com.dto.ComDtos.FacturaVencidaResponse> vencidas,
            java.util.List<mx.ferreteria.api.com.dto.ComDtos.FacturaPendienteResponse> pendientes) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(asuntoCuentas(vencidas, pendientes));
            helper.setText(textoPlanoCuentas(fecha, vencidas, pendientes),
                    htmlCuentas(fecha, vencidas, pendientes));
            mailSender.send(message);
            log.info("email cuentas-pagar enviado to={} vencidas={} pendientes={}",
                    to, vencidas.size(), pendientes.size());
        } catch (Exception e) {
            throw new ValidacionException(ErrorCode.SERVICIO_NO_DISPONIBLE);
        }
    }

    static final int MAX_FILAS_CORREO = 50;

    static String asuntoCuentas(
            java.util.List<mx.ferreteria.api.com.dto.ComDtos.FacturaVencidaResponse> vencidas,
            java.util.List<mx.ferreteria.api.com.dto.ComDtos.FacturaPendienteResponse> pendientes) {
        BigDecimal totalV = vencidas.stream()
                .map(v -> v.saldo() == null ? BigDecimal.ZERO : v.saldo())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalP = pendientes.stream()
                .map(p -> p.saldo() == null ? BigDecimal.ZERO : p.saldo())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return "Cuentas por pagar — " + vencidas.size() + " vencidas (" + moneda(totalV) + ") · "
                + pendientes.size() + " pendientes (" + moneda(totalP) + ")";
    }

    private static String fechaCorta(java.time.LocalDate fecha) {
        if (fecha == null) {
            return "—";
        }
        return fecha.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    private static String htmlCuentas(java.time.LocalDate fecha,
            java.util.List<mx.ferreteria.api.com.dto.ComDtos.FacturaVencidaResponse> vencidas,
            java.util.List<mx.ferreteria.api.com.dto.ComDtos.FacturaPendienteResponse> pendientes) {
        StringBuilder bloque = new StringBuilder();
        if (!vencidas.isEmpty()) {
            bloque.append(seccionTabla("Vencidas — prioridad de pago", true,
                    new String[] { "Proveedor", "Detalle", "Saldo" },
                    filasVencidas(vencidas)));
        }
        if (!pendientes.isEmpty()) {
            bloque.append(seccionTabla("Pendientes", false,
                    new String[] { "Proveedor", "Detalle", "Saldo" },
                    filasPendientes(pendientes)));
        }
        return EmailPlantilla.documento(
                "Cuentas por pagar al " + fechaCorta(fecha),
                "Recordatorio de compromisos con proveedores. Revise los saldos y programe "
                        + "los pagos desde <strong>Compras → Cuentas por pagar</strong>.",
                bloque.toString(),
                "Si tiene alguna duda, contacte a su sucursal.");
    }

    private static String seccionTabla(String titulo, boolean alerta, String[] encabezados,
            String filas) {
        String colorTitulo = alerta ? "#991b1b" : "#7c2d12";
        return "<div style=\"text-align:left;font-family:Arial,Helvetica,sans-serif;font-size:14px;"
                + "font-weight:bold;color:" + colorTitulo + ";margin:6px 0 8px 0;\">"
                + EmailPlantilla.escapar(titulo) + "</div>"
                + EmailPlantilla.tabla(encabezados, filas, alerta);
    }

    private static String filasVencidas(
            java.util.List<mx.ferreteria.api.com.dto.ComDtos.FacturaVencidaResponse> vencidas) {
        StringBuilder sb = new StringBuilder();
        vencidas.stream().limit(MAX_FILAS_CORREO).forEach(v -> sb.append(
                EmailPlantilla.filaTabla(
                        v.proveedor() == null ? "—" : v.proveedor(),
                        (v.compraFolio() == null ? "" : v.compraFolio() + " · ")
                                + (v.diasVencido() == null ? 0 : v.diasVencido()) + "d de retraso",
                        moneda(v.saldo()), true)));
        if (vencidas.size() > MAX_FILAS_CORREO) {
            sb.append(EmailPlantilla.filaResto(vencidas.size() - MAX_FILAS_CORREO));
        }
        return sb.toString();
    }

    private static String filasPendientes(
            java.util.List<mx.ferreteria.api.com.dto.ComDtos.FacturaPendienteResponse> pendientes) {
        StringBuilder sb = new StringBuilder();
        pendientes.stream().limit(MAX_FILAS_CORREO).forEach(p -> sb.append(
                EmailPlantilla.filaTabla(
                        p.proveedor() == null ? "—" : p.proveedor(),
                        (p.compraFolio() == null ? "" : p.compraFolio() + " · ")
                                + "vence " + fechaCorta(p.fechaVencimiento()),
                        moneda(p.saldo()), false)));
        if (pendientes.size() > MAX_FILAS_CORREO) {
            sb.append(EmailPlantilla.filaResto(pendientes.size() - MAX_FILAS_CORREO));
        }
        return sb.toString();
    }

    private static String textoPlanoCuentas(java.time.LocalDate fecha,
            java.util.List<mx.ferreteria.api.com.dto.ComDtos.FacturaVencidaResponse> vencidas,
            java.util.List<mx.ferreteria.api.com.dto.ComDtos.FacturaPendienteResponse> pendientes) {
        StringBuilder sb = new StringBuilder("Hola,\n\n");
        sb.append("Cuentas por pagar al ").append(fechaCorta(fecha)).append(".\n\n");
        if (!vencidas.isEmpty()) {
            sb.append("VENCIDAS (prioridad de pago):\n");
            vencidas.stream().limit(MAX_FILAS_CORREO).forEach(v -> sb.append("- ")
                    .append(v.proveedor()).append(" — ").append(v.compraFolio())
                    .append(" — ").append(moneda(v.saldo()))
                    .append(" (").append(v.diasVencido()).append("d de retraso)\n"));
            sb.append("\n");
        }
        if (!pendientes.isEmpty()) {
            sb.append("PENDIENTES:\n");
            pendientes.stream().limit(MAX_FILAS_CORREO).forEach(p -> sb.append("- ")
                    .append(p.proveedor()).append(" — ").append(p.compraFolio())
                    .append(" — ").append(moneda(p.saldo()))
                    .append(" (vence ").append(fechaCorta(p.fechaVencimiento())).append(")\n"));
        }
        sb.append("\nRevise los saldos en Compras → Cuentas por pagar.\n\n— ")
                .append(EmailPlantilla.MARCA);
        return sb.toString();
    }

    static String moneda(BigDecimal total) {
        if (total == null) {
            return null;
        }
        return NumberFormat.getCurrencyInstance(new Locale("es", "MX")).format(total);
    }

    private static String titulo(String tipo) {
        if (NotificacionJob.TIPO_VENTA_TICKET.equals(tipo)) {
            return "Tu ticket de compra";
        }
        if (NotificacionJob.TIPO_NOMINA_PAGADA.equals(tipo)) {
            return "Tu recibo de n&oacute;mina";
        }
        if (NotificacionJob.TIPO_INFORME_DASHBOARD.equals(tipo)) {
            return "Informe diario del negocio";
        }
        return "Tienes un documento nuevo";
    }

    private static String intro(String tipo) {
        if (NotificacionJob.TIPO_VENTA_TICKET.equals(tipo)) {
            return "Gracias por su compra. Adjuntamos su ticket en PDF.";
        }
        if (NotificacionJob.TIPO_NOMINA_PAGADA.equals(tipo)) {
            return "Su pago fue procesado. Adjuntamos su recibo en PDF.";
        }
        if (NotificacionJob.TIPO_INFORME_DASHBOARD.equals(tipo)) {
            return "KPIs y cierre del periodo. Adjuntamos el informe en PDF.";
        }
        return "Adjuntamos el documento solicitado en PDF.";
    }

    private static String etiquetaTotal(String tipo) {
        if (NotificacionJob.TIPO_NOMINA_PAGADA.equals(tipo)) {
            return "Neto a pagar";
        }
        return "Total";
    }

    private static String html(String tipo, String asunto, BigDecimal total) {
        StringBuilder filas = new StringBuilder();
        if (asunto != null && !asunto.isBlank()) {
            String etiqueta = NotificacionJob.TIPO_NOMINA_PAGADA.equals(tipo) ? "Periodo"
                    : NotificacionJob.TIPO_INFORME_DASHBOARD.equals(tipo) ? "Rango"
                            : "Documento";
            filas.append(EmailPlantilla.fila(etiqueta, asunto));
        }
        String monto = moneda(total);
        if (monto != null) {
            filas.append(EmailPlantilla.fila(etiquetaTotal(tipo), monto));
        }
        return EmailPlantilla.documento(titulo(tipo), intro(tipo),
                EmailPlantilla.detalles(filas.toString()),
                "Si tiene alguna duda, contacte a su sucursal.");
    }

    private static String textoPlano(String tipo, String asunto, BigDecimal total) {
        StringBuilder sb = new StringBuilder("Hola,\n\n");
        sb.append(intro(tipo)).append("\n\n");
        if (asunto != null && !asunto.isBlank()) {
            sb.append(asunto).append("\n");
        }
        String monto = moneda(total);
        if (monto != null) {
            sb.append(etiquetaTotal(tipo)).append(": ").append(monto).append("\n");
        }
        sb.append("\n— ").append(EmailPlantilla.MARCA);
        return sb.toString();
    }
}
