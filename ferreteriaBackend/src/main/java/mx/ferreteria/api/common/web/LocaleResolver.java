package mx.ferreteria.api.common.web;

import java.util.Locale;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolucion canonica de locale para TODA la API (PLAN M7.1).
 * <p>Precedencia:
 * <ol>
 *   <li>Query param {@code ?lang=es|en} (case-insensitive, validado).</li>
 *   <li>Header {@code Accept-Language} (si empieza por {@code en}).</li>
 *   <li>Default {@code es-MX}.</li>
 * </ol>
 * Cuando el filtro {@link LangParamFilter} valida el query param, expone el locale
 * resuelto como atributo de request bajo la clave {@link #ATTR_LOCALE} para que
 * {@link RequestIdFilter}, {@code RateLimitInterceptor} y {@code GlobalExceptionHandler}
 * lo lean sin repetir la logica.
 */
public final class LocaleResolver {

    public static final String LANG_PARAM = "lang";
    public static final String ATTR_LOCALE = "app.locale";
    public static final Locale DEFAULT_LOCALE = Locale.of("es", "MX");
    public static final Locale ENGLISH = Locale.ENGLISH;

    private static final Set<String> SUPPORTED = Set.of("es", "en");

    private LocaleResolver() {
    }

    /**
     * Resuelve el locale efectivo del request. Si el filtro ya coloco el atributo
     * {@link #ATTR_LOCALE}, lo devuelve (camino caliente). Si no, recalcula desde
     * query param o header.
     */
    public static Locale resolve(HttpServletRequest req) {
        if (req == null) {
            return DEFAULT_LOCALE;
        }
        Object cached = req.getAttribute(ATTR_LOCALE);
        if (cached instanceof Locale l) {
            return l;
        }
        String langParam = req.getParameter(LANG_PARAM);
        if (langParam != null && !langParam.isBlank()) {
            String normalized = langParam.trim().toLowerCase(Locale.ROOT);
            if ("es".equals(normalized)) {
                return DEFAULT_LOCALE;
            }
            if ("en".equals(normalized)) {
                return ENGLISH;
            }
            return DEFAULT_LOCALE;
        }
        return readAcceptLanguage(req);
    }

    /**
     * Valida el query param {@code ?lang=}. Si esta ausente o vacio, devuelve
     * {@code valid=true} y un locale resuelto por header. Si tiene un valor, debe
     * ser {@code es} o {@code en} (case-insensitive); cualquier otro valor
     * devuelve {@code valid=false} con el valor crudo recibido.
     */
    public static Validation validateLangParam(HttpServletRequest req) {
        String langParam = req == null ? null : req.getParameter(LANG_PARAM);
        if (langParam == null || langParam.isBlank()) {
            return new Validation(true, null, readAcceptLanguage(req));
        }
        String normalized = langParam.trim().toLowerCase(Locale.ROOT);
        if ("es".equals(normalized)) {
            return new Validation(true, null, DEFAULT_LOCALE);
        }
        if ("en".equals(normalized)) {
            return new Validation(true, null, ENGLISH);
        }
        return new Validation(false, langParam, DEFAULT_LOCALE);
    }

    public static boolean isSupported(String lang) {
        if (lang == null) {
            return false;
        }
        return SUPPORTED.contains(lang.trim().toLowerCase(Locale.ROOT));
    }

    private static Locale readAcceptLanguage(HttpServletRequest req) {
        String al = req.getHeader("Accept-Language");
        if (al != null && al.toLowerCase(Locale.ROOT).startsWith("en")) {
            return ENGLISH;
        }
        return DEFAULT_LOCALE;
    }

    /** Resultado de la validacion del query param {@code ?lang=}. */
    public record Validation(boolean valid, String invalidValue, Locale resolved) {
    }
}
