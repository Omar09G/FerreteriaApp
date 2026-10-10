package mx.ferreteria.api.common.web;

import java.time.Duration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;

/**
 * Buckets en memoria (Caffeine) por réplica.
 *
 * <p>
 * Es el modo por defecto ({@code app.rate-limit.distributed=false}): no
 * requiere infraestructura, pero con N réplicas la capacidad efectiva se
 * multiplica N veces (cada réplica cuenta por separado). Para HPA real usar
 * el modo Redis.
 */
final class LocalRateLimitBuckets implements RateLimitBuckets {

    private final Cache<String, Bucket> cache;

    LocalRateLimitBuckets(RateLimitProperties props) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(Math.max(1L, props.cacheMaxSize()))
                .expireAfterAccess(Duration.ofMinutes(Math.max(1L, props.cacheTtlMinutes())))
                .build();
    }

    @Override
    public Bucket resolve(String clave, RateLimitProperties.Grupo grupo) {
        return cache.get(clave, k -> nuevoBucket(grupo));
    }

    static Bucket nuevoBucket(RateLimitProperties.Grupo g) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(Math.max(1L, g.capacity()))
                .refillGreedy(Math.max(1L, g.refillPerSecond()), Duration.ofSeconds(1))
                .build();

        return Bucket.builder().addLimit(limit).build();
    }
}
