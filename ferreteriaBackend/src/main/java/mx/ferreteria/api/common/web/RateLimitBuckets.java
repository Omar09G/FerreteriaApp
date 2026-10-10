package mx.ferreteria.api.common.web;

import io.github.bucket4j.Bucket;

/**
 * Resolución de buckets de rate limit por clave
 * ({@code perfil:controller:usuarioOIp}).
 *
 * <p>
 * Dos implementaciones: {@code LocalRateLimitBuckets} (Caffeine en memoria,
 * una por réplica) y {@code RedisRateLimitBuckets} (bucket4j-redis, estado
 * compartido entre réplicas). La elección vive en
 * {@code RateLimitBucketsFactory} según {@code app.rate-limit.distributed}.
 */
public interface RateLimitBuckets {

    /**
     * Devuelve el bucket para la clave (creándolo con el grupo si no existe).
     */
    Bucket resolve(String clave, RateLimitProperties.Grupo grupo);

    /**
     * Libera recursos (conexiones). La implementación local es no-op.
     */
    default void close() {
        // sin recursos
    }
}
