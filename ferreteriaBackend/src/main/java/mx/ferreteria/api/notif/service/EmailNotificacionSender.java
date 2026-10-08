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
import mx.ferreteria.api.common.privacy.DatosSensibles;
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
        String nombre = clave != null && clave.contains("/")
                ? clave.substring(clave.lastIndexOf('/') + 1)
                : clave;
        enviarConAdjunto(to, asunto != null ? asunto : "Notificación Ferretería",
                textoPlano(tipo, asunto, total), html(tipo, asunto, total),
                pdf, nombre, "application/pdf");
        log.info("email enviado to={} tipo={}", DatosSensibles.enmascararEmail(to), tipo);
    }

    /**
     * Recordatorio de stock bajo: resumen en el cuerpo + Excel con el detalle.
     */
    public void sendStockBajo(String to, java.time.LocalDate fecha, int totalProductos,
            int agotados, int almacenes, byte[] xlsx, String nombreArchivo) {
        String titulo = "Stock bajo al " + fechaCorta(fecha);
        String intro = "Productos en riesgo de desabasto. El detalle completo va en el "
                + "Excel adjunto; priorice la recompra desde "
                + "<strong>Inventario → Existencias</strong>.";
        String detalle = EmailPlantilla.detalles(
                EmailPlantilla.fila("Productos en bajo stock", String.valueOf(totalProductos))
                        + EmailPlantilla.fila("Agotados (existencia 0)", String.valueOf(agotados))
                        + EmailPlantilla.fila("Almacenes afectados", String.valueOf(almacenes)));
        String plano = "Hola,\n\nStock bajo al " + fechaCorta(fecha) + ".\n\n"
                + "Productos en bajo stock: " + totalProductos + "\n"
                + "Agotados: " + agotados + "\n"
                + "Almacenes afectados: " + almacenes + "\n\n"
                + "Detalle en el Excel adjunto.\n\n— " + EmailPlantilla.MARCA;
        enviarConAdjunto(to, "Stock bajo — " + totalProductos + " productos ("
                + agotados + " agotados)", plano,
                EmailPlantilla.documento(titulo, intro, detalle,
                        "Si tiene alguna duda, contacte a su sucursal."),
                xlsx, nombreArchivo,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        log.info("email stock-bajo enviado to={} productos={} agotados={}",
                DatosSensibles.enmascararEmail(to), totalProductos, agotados);
    }

    private void enviarConAdjunto(String to, String asunto, String textoPlano, String html,
            byte[] adjunto, String nombreArchivo, String contentType) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(asunto);
            helper.setText(textoPlano, html);
            if (adjunto != null && nombreArchivo != null) {
                helper.addAttachment(nombreArchivo, new ByteArrayResource(adjunto), contentType);
            }
            mailSender.send(message);
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
        enviarTablas(to, asuntoCuentas(vencidas, pendientes),
                "Cuentas por pagar al " + fechaCorta(fecha),
                "Recordatorio de compromisos con proveedores. Revise los saldos y programe "
                        + "los pagos desde <strong>Compras → Cuentas por pagar</strong>.",
                vencidas, pendientes,
                () -> seccionTabla("Vencidas — prioridad de pago", true,
                        new String[] { "Proveedor", "Detalle", "Saldo" },
                        filasVencidas(vencidas)),
                () -> seccionTabla("Pendientes", false,
                        new String[] { "Proveedor", "Detalle", "Saldo" },
                        filasPendientes(pendientes)),
                textoPlanoCuentas(fecha, vencidas, pendientes));
    }

    /**
     * Recordatorio de cobranza a clientes (sin PDF): vencidas (rojo) y
     * pendientes con saldos y totales.
     */
    public void sendCobranza(String to, java.time.LocalDate fecha,
            java.util.List<mx.ferreteria.api.ven.dto.VenDtos.CuentaCobrarResponse> vencidas,
            java.util.List<mx.ferreteria.api.ven.dto.VenDtos.CuentaCobrarResponse> pendientes) {
        enviarTablas(to, asuntoCobranza(vencidas, pendientes),
                "Cuentas por cobrar al " + fechaCorta(fecha),
                "Recordatorio de saldos de clientes a crédito. Dé seguimiento a la "
                        + "cobranza desde <strong>Ventas → Cobranza</strong>.",
                vencidas, pendientes,
                () -> seccionTabla("Vencidas — prioridad de cobro", true,
                        new String[] { "Cliente", "Detalle", "Saldo" },
                        filasCobranza(vencidas, true)),
                () -> seccionTabla("Pendientes", false,
                        new String[] { "Cliente", "Detalle", "Saldo" },
                        filasCobranza(pendientes, false)),
                textoPlanoTablas("Cuentas por cobrar", fecha, vencidas, pendientes));
    }

    /**
     * Recordatorio de rentas (sin PDF): vencidas (rojo) y próximas a devolver.
     */
    public void sendRentas(String to, java.time.LocalDate fecha,
            java.util.List<mx.ferreteria.api.ven.dto.VenDtos.RentaResponse> vencidas,
            java.util.List<mx.ferreteria.api.ven.dto.VenDtos.RentaResponse> proximas) {
        enviarTablas(to, asuntoRentas(vencidas, proximas),
                "Rentas al " + fechaCorta(fecha),
                "Herramientas rentadas por devolver. Contacte al cliente y registre la "
                        + "devolución desde <strong>Ventas → Rentas</strong>.",
                vencidas, proximas,
                () -> seccionTabla("Vencidas — devolución pendiente", true,
                        new String[] { "Cliente", "Detalle", "Depósito" },
                        filasRentas(vencidas, true)),
                () -> seccionTabla("Próximas a devolver", false,
                        new String[] { "Cliente", "Detalle", "Depósito" },
                        filasRentas(proximas, false)),
                textoPlanoTablas("Rentas", fecha, vencidas, proximas));
    }

    private void enviarTablas(String to, String asunto, String titulo, String intro,
            java.util.List<?> vencidas, java.util.List<?> pendientes,
            java.util.function.Supplier<String> seccionVencidas,
            java.util.function.Supplier<String> seccionPendientes, String textoPlano) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(asunto);
            StringBuilder bloque = new StringBuilder();
            if (!vencidas.isEmpty()) {
                bloque.append(seccionVencidas.get());
            }
            if (!pendientes.isEmpty()) {
                bloque.append(seccionPendientes.get());
            }
            helper.setText(textoPlano, EmailPlantilla.documento(titulo, intro,
                    bloque.toString(), "Si tiene alguna duda, contacte a su sucursal."));
            mailSender.send(message);
            log.info("email recordatorio enviado to={} asunto={}", DatosSensibles.enmascararEmail(to), asunto);
        } catch (Exception e) {
            throw new ValidacionException(ErrorCode.SERVICIO_NO_DISPONIBLE);
        }
    }

    static String asuntoCobranza(
            java.util.List<mx.ferreteria.api.ven.dto.VenDtos.CuentaCobrarResponse> vencidas,
            java.util.List<mx.ferreteria.api.ven.dto.VenDtos.CuentaCobrarResponse> pendientes) {
        return "Cobranza — " + vencidas.size() + " vencidas (" + moneda(totalSaldosCobranza(vencidas))
                + ") · " + pendientes.size() + " pendientes ("
                + moneda(totalSaldosCobranza(pendientes)) + ")";
    }

    static String asuntoRentas(
            java.util.List<mx.ferreteria.api.ven.dto.VenDtos.RentaResponse> vencidas,
            java.util.List<mx.ferreteria.api.ven.dto.VenDtos.RentaResponse> proximas) {
        return "Rentas — " + vencidas.size() + " vencidas · " + proximas.size()
                + " próximas a devolver";
    }

    static BigDecimal totalSaldosCobranza(
            java.util.List<mx.ferreteria.api.ven.dto.VenDtos.CuentaCobrarResponse> cuentas) {
        return cuentas.stream()
                .map(c -> c.saldo() == null ? BigDecimal.ZERO : c.saldo())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String filasCobranza(
            java.util.List<mx.ferreteria.api.ven.dto.VenDtos.CuentaCobrarResponse> cuentas,
            boolean alerta) {
        StringBuilder sb = new StringBuilder();
        cuentas.stream().limit(MAX_FILAS_CORREO).forEach(c -> sb.append(
                EmailPlantilla.filaTabla(
                        c.clienteNombre() == null ? "—" : c.clienteNombre(),
                        (c.ventaFolio() == null ? "" : c.ventaFolio() + " · ")
                                + "vence " + fechaCorta(c.fechaVencimiento()),
                        moneda(c.saldo()), alerta)));
        if (cuentas.size() > MAX_FILAS_CORREO) {
            sb.append(EmailPlantilla.filaResto(cuentas.size() - MAX_FILAS_CORREO));
        }
        return sb.toString();
    }

    private static String filasRentas(
            java.util.List<mx.ferreteria.api.ven.dto.VenDtos.RentaResponse> rentas,
            boolean alerta) {
        StringBuilder sb = new StringBuilder();
        rentas.stream().limit(MAX_FILAS_CORREO).forEach(r -> sb.append(
                EmailPlantilla.filaTabla(
                        r.clienteNombre() == null ? "—" : r.clienteNombre(),
                        (r.folio() == null ? "" : r.folio() + " · ")
                                + "dev. " + fechaCorta(r.fechaDevEsperada()),
                        moneda(r.deposito()), alerta)));
        if (rentas.size() > MAX_FILAS_CORREO) {
            sb.append(EmailPlantilla.filaResto(rentas.size() - MAX_FILAS_CORREO));
        }
        return sb.toString();
    }

    private static String textoPlanoTablas(String tema, java.time.LocalDate fecha,
            java.util.List<?> vencidas, java.util.List<?> pendientes) {
        return "Hola,\n\n" + tema + " al " + fechaCorta(fecha) + ".\n\n"
                + "Vencidas: " + vencidas.size() + ". Pendientes/próximas: "
                + pendientes.size() + ".\n\nRevise el detalle en el sistema.\n\n— "
                + EmailPlantilla.MARCA;
    }

    /**
     * Aviso de turnos abiertos (corte sin cerrar): tabla con caja, apertura
     * y monto. Sin PDF: el corte se hace en el sistema.
     */
    public void sendTurnoAbierto(String to, java.time.LocalDate fecha,
            java.util.List<mx.ferreteria.api.fin.dto.FinDtos.TurnoCajaResponse> turnos) {
        StringBuilder filas = new StringBuilder();
        turnos.stream().limit(MAX_FILAS_CORREO).forEach(t -> filas.append(
                EmailPlantilla.filaTabla(
                        t.cajaNombre() == null ? "—" : t.cajaNombre(),
                        "Apertura " + fechaHoraCorta(t.aperturaEn()),
                        moneda(t.montoApertura()), true)));
        if (turnos.size() > MAX_FILAS_CORREO) {
            filas.append(EmailPlantilla.filaResto(turnos.size() - MAX_FILAS_CORREO));
        }
        String titulo = "Turnos abiertos al " + fechaCorta(fecha);
        String intro = turnos.size() == 1
                ? "Hay <strong>1 caja sin cerrar</strong>. Realice el corte desde "
                        + "<strong>Caja</strong> antes de terminar el día."
                : "Hay <strong>" + turnos.size() + " cajas sin cerrar</strong>. Realice "
                        + "los cortes desde <strong>Caja</strong> antes de terminar el día.";
        String plano = "Hola,\n\nTurnos abiertos al " + fechaCorta(fecha) + ":\n\n"
                + turnos.stream().limit(MAX_FILAS_CORREO)
                        .map(t -> "- " + (t.cajaNombre() == null ? "—" : t.cajaNombre())
                                + " (apertura " + fechaHoraCorta(t.aperturaEn()) + ", "
                                + monedaOGuion(t.montoApertura()) + ")")
                        .collect(java.util.stream.Collectors.joining("\n"))
                + "\n\nRealice los cortes en Caja.\n\n— " + EmailPlantilla.MARCA;
        enviarTablas(to,
                "Corte pendiente — " + turnos.size()
                        + (turnos.size() == 1 ? " caja abierta" : " cajas abiertas"),
                titulo, intro, turnos, java.util.List.of(),
                () -> seccionTabla("Cajas sin cerrar", true,
                        new String[] { "Caja", "Detalle", "Monto" }, filas.toString()),
                () -> "", plano);
    }

    static String fechaHoraCorta(java.time.Instant instante) {
        if (instante == null) {
            return "—";
        }
        return instante.atZone(mx.ferreteria.api.common.time.ZonaHoraria.ZONA)
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm"));
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
                    .append(" — ").append(monedaOGuion(v.saldo()))
                    .append(" (").append(v.diasVencido()).append("d de retraso)\n"));
            sb.append("\n");
        }
        if (!pendientes.isEmpty()) {
            sb.append("PENDIENTES:\n");
            pendientes.stream().limit(MAX_FILAS_CORREO).forEach(p -> sb.append("- ")
                    .append(p.proveedor()).append(" — ").append(p.compraFolio())
                    .append(" — ").append(monedaOGuion(p.saldo()))
                    .append(" (vence ").append(fechaCorta(p.fechaVencimiento())).append(")\n"));
        }
        sb.append("\nRevise los saldos en Compras → Cuentas por pagar.\n\n— ")
                .append(EmailPlantilla.MARCA);
        return sb.toString();
    }

    /** Moneda es-MX para textos de avisos (bandeja, WhatsApp, correo). */
    public static String moneda(BigDecimal total) {
        if (total == null) {
            return null;
        }
        return NumberFormat.getCurrencyInstance(new Locale("es", "MX")).format(total);
    }

    /** Moneda para texto plano: nunca "null", usa em-dash. */
    private static String monedaOGuion(BigDecimal total) {
        String monto = moneda(total);
        return monto == null ? "—" : monto;
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
