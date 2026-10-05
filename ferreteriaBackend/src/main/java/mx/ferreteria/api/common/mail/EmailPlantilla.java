package mx.ferreteria.api.common.mail;

/**
 * Plantilla única de correos transaccionales (OTP, tickets, nómina,
 * informe): encabezado de marca, tarjeta de contenido y pie. Layout de
 * tablas con CSS inline (compatible con Gmail, Outlook y móviles).
 * Clase estática sin estado: no crea ciclos entre módulos.
 */
public final class EmailPlantilla {

    public static final String MARCA = "El Tornillo Feliz";
    public static final String SUBTITULO = "Sistema de punto de venta";
    static final String COLOR_MARCA = "#c2410c";

    private EmailPlantilla() {
    }

    /** Escapa texto para interpolarlo en HTML sin romper el layout. */
    public static String escapar(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** Fila de detalle (etiqueta → valor) para el bloque de datos. */
    public static String fila(String etiqueta, String valor) {
        return "<tr>"
                + "<td style=\"padding:6px 0;font-family:Arial,Helvetica,sans-serif;"
                + "font-size:14px;color:#78716c;\">" + escapar(etiqueta) + "</td>"
                + "<td align=\"right\" style=\"padding:6px 0;font-family:Arial,Helvetica,sans-serif;"
                + "font-size:14px;font-weight:bold;color:#292524;\">" + escapar(valor) + "</td>"
                + "</tr>";
    }

    /**
     * Documento completo.
     *
     * @param titulo   encabezado del contenido (hola + título).
     * @param introHtml párrafo introductorio (ya en HTML).
     * @param bloqueHtml contenido principal (tabla de detalles, código, etc.).
     * @param notaHtml  nota final pequeña (puede ser null).
     */
    public static String documento(String titulo, String introHtml, String bloqueHtml,
            String notaHtml) {
        String nota = notaHtml == null ? "" : notaHtml;
        return "<!DOCTYPE html><html lang=\"es\"><head><meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1.0\"></head>"
                + "<body style=\"margin:0;padding:0;background-color:#f5f1ea;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">"
                + "<tr><td align=\"center\" style=\"padding:32px 16px;\">"
                + "<table role=\"presentation\" width=\"520\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
                + "style=\"max-width:520px;background:#ffffff;border-radius:12px;overflow:hidden; "
                + "border:1px solid #e7e0d3;\">"
                + "<tr><td align=\"center\" style=\"background:" + COLOR_MARCA + ";padding:22px 24px;\">"
                + "<div style=\"font-family:Arial,Helvetica,sans-serif;font-size:22px;font-weight:bold;"
                + "color:#ffffff;\">&#x1F528; " + MARCA + "</div>"
                + "<div style=\"font-family:Arial,Helvetica,sans-serif;font-size:13px;color:#ffedd5; "
                + "margin-top:4px;\">" + SUBTITULO + "</div>"
                + "</td></tr>"
                + "<tr><td style=\"padding:28px 28px 8px 28px;font-family:Arial,Helvetica,sans-serif; "
                + "font-size:15px;color:#292524;\">"
                + "Hola,"
                + "</td></tr>"
                + "<tr><td style=\"padding:0 28px;font-family:Arial,Helvetica,sans-serif;font-size:15px; "
                + "font-weight:bold;color:#292524;\">"
                + escapar(titulo)
                + "</td></tr>"
                + "<tr><td style=\"padding:8px 28px 0 28px;font-family:Arial,Helvetica,sans-serif;"
                + "font-size:15px;color:#292524;line-height:1.5;\">"
                + introHtml
                + "</td></tr>"
                + "<tr><td align=\"center\" style=\"padding:20px 28px;\">" + bloqueHtml + "</td></tr>"
                + "<tr><td style=\"padding:0 28px;font-family:Arial,Helvetica,sans-serif; "
                + "font-size:13px;color:#78716c;line-height:1.5;\">"
                + nota
                + "</td></tr>"
                + "<tr><td align=\"center\" style=\"padding:20px 28px 24px 28px; "
                + "font-family:Arial,Helvetica,sans-serif;font-size:12px;color:#a8a29e;\">"
                + "Este es un mensaje autom&aacute;tico, no respondas a este correo.<br>"
                + "&copy; " + MARCA
                + "</td></tr>"
                + "</table></td></tr></table></body></html>";
    }

    /** Envuelve filas de detalle en una tabla de ancho completo. */
    public static String detalles(String filasHtml) {
        return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                + "border=\"0\" style=\"background:#fff7ed;border:1px solid #fed7aa;"
                + "border-radius:10px;padding:10px 18px;\">"
                + filasHtml + "</table>";
    }

    /**
     * Tabla de partidas con encabezados (proveedor, detalle, saldo).
     *
     * @param alerta true = montos en rojo (deudas vencidas).
     */
    public static String tabla(String[] encabezados, String filasHtml, boolean alerta) {
        StringBuilder th = new StringBuilder();
        for (int i = 0; i < encabezados.length; i++) {
            boolean ultimo = i == encabezados.length - 1;
            th.append("<th align=\"").append(ultimo ? "right" : "left").append("\" ").append("style=\"font-family:Arial,Helvetica,sans-serif;font-size:12px;").append("font-weight:bold;color:#78716c;padding:6px 8px;").append("border-bottom:1px solid #fed7aa;\">")
                    .append(escapar(encabezados[i])).append("</th>");
        }
        String borde = alerta ? "#fecaca" : "#fed7aa";
        String fondo = alerta ? "#fef2f2" : "#fff7ed";
        return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                + "border=\"0\" style=\"background:" + fondo + ";border:1px solid " + borde + ";"
                + "border-radius:10px;padding:6px 10px;margin-bottom:14px;\">"
                + "<tr>" + th + "</tr>" + filasHtml + "</table>";
    }

    /** Fila de partida: proveedor, detalle y saldo (alineado a la derecha). */
    public static String filaTabla(String proveedor, String detalle, String saldo,
            boolean alerta) {
        String colorSaldo = alerta ? "#b91c1c" : "#292524";
        return "<tr>"
                + "<td style=\"font-family:Arial,Helvetica,sans-serif;font-size:13px;"
                + "color:#292524;padding:6px 8px;border-bottom:1px solid #f0ebe0;\">"
                + escapar(proveedor) + "</td>"
                + "<td style=\"font-family:Arial,Helvetica,sans-serif;font-size:12px;"
                + "color:#78716c;padding:6px 8px;border-bottom:1px solid #f0ebe0;\">"
                + escapar(detalle) + "</td>"
                + "<td align=\"right\" style=\"font-family:Arial,Helvetica,sans-serif;font-size:13px;"
                + "font-weight:bold;color:" + colorSaldo + ";padding:6px 8px;"
                + "border-bottom:1px solid #f0ebe0;white-space:nowrap;\">"
                + escapar(saldo) + "</td>"
                + "</tr>";
    }

    /** Fila de “+N más en el sistema” cuando se trunca la lista. */
    public static String filaResto(int restantes) {
        return "<tr><td colspan=\"3\" align=\"center\" "
                + "style=\"font-family:Arial,Helvetica,sans-serif;font-size:12px;"
                + "color:#78716c;padding:8px;\">"
                + "… y " + restantes + " m&aacute;s en el sistema.</td></tr>";
    }
}
