package mx.ferreteria.api.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RateLimitBucketsFactoryTest {

    private static RateLimitProperties props(boolean distributed, String redisUri) {
        return new RateLimitProperties(
                true, true, 100L, 5L, distributed, false /*trustForwardedFor*/, redisUri, null);
    }

    @Test
    @DisplayName("Por defecto (distributed=false): buckets locales Caffeine")
    void porDefectoLocal() {
        RateLimitBuckets buckets = RateLimitBucketsFactory.create(props(false, null));

        assertThat(buckets).isInstanceOf(LocalRateLimitBuckets.class);
    }

    @Test
    @DisplayName("distributed=true sin URI: fail-fast al arrancar, no limitador silencioso")
    void distribuidoSinUriFallaRapido() {
        assertThatThrownBy(() -> RateLimitBucketsFactory.create(props(true, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RATE_LIMIT_REDIS_URI");
    }

    @Test
    @DisplayName("distributed=true con URI en blanco: fail-fast igual")
    void distribuidoUriEnBlancoFallaRapido() {
        assertThatThrownBy(() -> RateLimitBucketsFactory.create(props(true, "   ")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RATE_LIMIT_REDIS_URI");
    }

    // Nota: la rama distributed=true con URI válida se prueba en
    // RedisRateLimitBucketsIT (construye conexión real): el builder de Lettuce
    // conecta eagerly, así que sin Redis vivo falla en construcción
    // (fail-fast en arranque, igual que JwtService).
}
