package mx.ferreteria.api.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * GeoIP local para la ubicación aproximada del login (MaxMind GeoLite2-City).
 * Sin archivo configurado el servicio se deshabilita solo (fail-open a null).
 */
@ConfigurationProperties(prefix = "app.auth.geoip")
public record GeoIpProperties(
        /** Ruta al GeoLite2-City.mmdb. Vacía = geolocalización deshabilitada. */
        @DefaultValue("") String dbPath) {
}
