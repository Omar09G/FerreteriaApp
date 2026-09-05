package mx.ferreteria.api.cat.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Cache local (Caffeine) para datos de lectura frecuente y baja volatilidad:
 * catalogos (BACK-ESC-004), formas de pago, impuestos, unidades, marcas.
 * TTL 5 min — los catalogos cambian raramente; el @CacheEvict en writes
 * (AbstractCatalogoService) mantiene coherencia inmediata.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CATALOGOS = "catalogos";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cm = new CaffeineCacheManager(CATALOGOS);
        cm.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .recordStats());
        return cm;
    }
}