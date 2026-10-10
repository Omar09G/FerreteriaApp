package mx.ferreteria.api.common.web;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.proxy.RecoveryStrategy;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;

/**
 * Buckets compartidos en Redis (bucket4j-redis + Lettuce).
 *
 * <p>
 * Todas las réplicas resuelven la misma clave contra el mismo estado en
 * Redis, así que la capacidad configurada es global y el HPA puede escalar
 * sin multiplicar el límite. La configuración del bucket (capacidad/ritmo)
 * debe ser idéntica en todas las réplicas: viene del mismo
 * {@code app.rate-limit.grupos} en todas.
 *
 * <p>
 * <b>Degradación:</b> {@code RecoveryStrategy.RECONSTRUCT} — si Redis cae, el
 * bucket se reconstruye en memoria local (equivale al modo anterior, nunca
 * peor) en vez de tumbar la API. Las claves expiran por TTL (tiempo de
 * recarga al máximo + 1 min) para no acumular estado en Redis.
 */
final class RedisRateLimitBuckets implements RateLimitBuckets {

    private final RedisClient client;
    private final ProxyManager<String> proxies;

    RedisRateLimitBuckets(String redisUri) {
        this.client = RedisClient.create(redisUri);
        ProxyManager<byte[]> crudo = LettuceBasedProxyManager.builderFor(client)
                .withExpirationStrategy(ExpirationAfterWriteStrategy
                        .basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(1)))
                .build();
        this.proxies = crudo.withMapper(RedisRateLimitBuckets::aBytes);
        Runtime.getRuntime().addShutdownHook(new Thread(client::shutdown, "redis-ratelimit-cierre"));
    }

    @Override
    public Bucket resolve(String clave, RateLimitProperties.Grupo grupo) {
        return proxies.builder()
                .withRecoveryStrategy(RecoveryStrategy.RECONSTRUCT)
                .build(clave, configPara(grupo));
    }

    @Override
    public void close() {
        client.shutdown();
    }

    static BucketConfiguration configPara(RateLimitProperties.Grupo g) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(Math.max(1L, g.capacity()))
                .refillGreedy(Math.max(1L, g.refillPerSecond()), Duration.ofSeconds(1))
                .build();
        return BucketConfiguration.builder().addLimit(limit).build();
    }

    private static byte[] aBytes(String clave) {
        return clave.getBytes(StandardCharsets.UTF_8);
    }
}
