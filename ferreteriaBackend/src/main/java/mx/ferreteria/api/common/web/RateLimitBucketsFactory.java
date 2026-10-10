package mx.ferreteria.api.common.web;

/**
 * Elige el backend de buckets según {@code app.rate-limit.distributed}.
 * Pública porque se usa desde {@code common.config.RateLimitConfig}.
 *
 * <p>
 * Fail-fast al arrancar (estilo {@code JwtService}): con
 * {@code distributed=true} pero sin {@code redis-uri} se aborta en vez de
 * operar con un limitador silenciosamente local.
 */
public final class RateLimitBucketsFactory {

    private RateLimitBucketsFactory() {
        // utilitario
    }

    public static RateLimitBuckets create(RateLimitProperties props) {
        if (props.distributed()) {
            String uri = props.redisUri();
            if (uri == null || uri.isBlank()) {
                throw new IllegalStateException(
                        "RATE_LIMIT_DISTRIBUTED=true exige RATE_LIMIT_REDIS_URI (formato redis://host:port)");
            }
            return new RedisRateLimitBuckets(uri);
        }
        return new LocalRateLimitBuckets(props);
    }
}
