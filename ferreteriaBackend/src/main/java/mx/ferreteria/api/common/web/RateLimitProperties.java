package mx.ferreteria.api.common.web;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuracion del rate limit bucket4j (PLAN M7).
 * Perfiles por controller configurables en {@code app.rate-limit.grupos.<nombre>} y
 * parametrizables por variables de entorno (ver {@code application.yml}).
 *
 * Cada grupo define una capacidad (rafaga maxima) y un ritmo de recarga en tokens/segundo
 * (bucket4j {@code Refill.greedy}). Si el perfil solicitado no existe, se usa
 * {@code grupos["default"]}; si este tampoco existe, se aplica el fallback interno.
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("true") boolean scopeByUser,
        @DefaultValue("200000") long cacheMaxSize,
        @DefaultValue("30") long cacheTtlMinutes,
        Map<String, Grupo> grupos) {

    private static final int FALLBACK_CAPACITY = 120;
    private static final int FALLBACK_REFILL_PER_SECOND = 5;

    public record Grupo(
            @DefaultValue("120") int capacity,
            @DefaultValue("5") int refillPerSecond) {
    }

    /**
     * Resuelve el grupo para un nombre de perfil: primero la entrada exacta,
     * luego {@code "default"} y finalmente los valores internos de fallback.
     */
    public Grupo grupo(String nombre) {
        if (grupos != null) {
            Grupo g = grupos.get(nombre);
            if (g != null) {
                return g;
            }
            g = grupos.get("default");
            if (g != null) {
                return g;
            }
        }
        return new Grupo(FALLBACK_CAPACITY, FALLBACK_REFILL_PER_SECOND);
    }
}
