package mx.ferreteria.api.seg.service;

/** Metadatos de la petición para trazabilidad de sesiones (PLAN §5). */
public record RequestMeta(String ip, String userAgent) {
    public static final RequestMeta UNKNOWN = new RequestMeta(null, null);
}
