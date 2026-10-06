package mx.ferreteria.api.common.privacy;

/**
 * Dueño único de la ofuscación de datos sensibles para logs y desafíos OTP.
 *
 * <p>La BD guarda la verdad completa (RFC/CURP/NSS/email/teléfono se necesitan
 * para facturación, notificaciones y búsquedas); la ofuscación vive solo en el
 * borde de presentación: logs y destinos enmascarados del OTP. Sin estado,
 * sin dependencias de módulos: no crea ciclos.
 */
public final class DatosSensibles {

    private DatosSensibles() {
    }

    /**
     * Enmascara un correo para logs ({@code ca***@dominio}): evita PII en claro.
     */
    public static String enmascararEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        String local = email.substring(0, email.indexOf('@'));
        String dominio = email.substring(email.indexOf('@'));
        String visible = local.length() <= 2 ? local.charAt(0) + "*"
                : local.substring(0, 2) + "***";
        return visible + dominio;
    }

    /**
     * Enmascara un teléfono/whatsapp dejando solo los últimos 3 dígitos.
     */
    public static String enmascararTelefono(String telefono) {
        if (telefono == null) {
            return "***";
        }
        String digitos = telefono.replaceAll("\\D", "");
        if (digitos.length() <= 3) {
            return "***";
        }
        return "***" + digitos.substring(digitos.length() - 3);
    }

    /**
     * Enmascara identificadores (RFC/CURP/NSS) dejando solo los últimos 3
     * caracteres. Para uso en logs; nunca para persistir ni para respuestas API.
     */
    public static String enmascararIdentificador(String valor) {
        if (valor == null || valor.isBlank()) {
            return "***";
        }
        String limpio = valor.trim();
        if (limpio.length() <= 3) {
            return "***";
        }
        return "***" + limpio.substring(limpio.length() - 3);
    }
}
