package mx.ferreteria.api.seg.service;

import java.io.File;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;

import lombok.extern.slf4j.Slf4j;
import mx.ferreteria.api.common.security.GeoIpProperties;

/**
 * Ubicación aproximada del login (ciudad/región/país) por GeoIP local
 * (MaxMind GeoLite2-City, sin llamadas externas ni latencia de red).
 *
 * <p>Fail-open a propósito: sin {@code GEOIP_DB_PATH}, con archivo ausente o
 * con IP privada/desconocida, devuelve vacío y el login guarda nulls. La
 * autenticación nunca depende de este servicio.</p>
 *
 * <p>Solo se persiste texto aproximado (nunca coordenadas): ver migración
 * V37 y aviso de privacidad.</p>
 */
@Slf4j
@Service
public class GeoIpService {

    /** Ubicación aproximada lista para persistir en seg.sesiones. */
    public record Ubicacion(String ciudad, String region, String pais) {
        /** "Ciudad, Región, País" omitiendo partes vacías; "" si no hay nada. */
        public String texto() {
            List<String> partes = new ArrayList<>(3);
            if (ciudad != null && !ciudad.isBlank()) {
                partes.add(ciudad);
            }
            if (region != null && !region.isBlank()) {
                partes.add(region);
            }
            if (pais != null && !pais.isBlank()) {
                partes.add(pais);
            }
            return String.join(", ", partes);
        }
    }

    private final DatabaseReader reader;

    public GeoIpService(GeoIpProperties props) {
        String ruta = props == null ? null : props.dbPath();
        DatabaseReader r = null;
        if (ruta != null && !ruta.isBlank()) {
            File f = new File(ruta);
            if (f.isFile()) {
                try {
                    r = new DatabaseReader.Builder(f).build();
                    log.info("geoip habilitado db={}", f.getAbsolutePath());
                } catch (Exception e) {
                    log.warn("geoip deshabilitado (no se pudo abrir {}): {}",
                            f.getAbsolutePath(), e.getMessage());
                }
            } else {
                log.info("geoip deshabilitado (sin archivo en {})", f.getAbsolutePath());
            }
        } else {
            log.info("geoip deshabilitado (sin GEOIP_DB_PATH)");
        }
        this.reader = r;
    }

    /** True cuando hay base cargada y se puede resolver. */
    public boolean habilitado() {
        return reader != null;
    }

    /** Resuelve la IP a ciudad/región/país; vacío si no se puede. */
    public Optional<Ubicacion> buscar(String ip) {
        if (ip == null || ip.isBlank() || reader == null) {
            return Optional.empty();
        }
        try {
            var respuesta = reader.city(InetAddress.getByName(ip.trim()));
            if (respuesta == null) {
                return Optional.empty();
            }
            String ciudad = nombre(respuesta.getCity() == null
                    ? null : respuesta.getCity().getNames());
            var subs = respuesta.getSubdivisions();
            String region = subs.isEmpty() ? null
                    : nombre(subs.get(subs.size() - 1).getNames());
            String pais = nombre(respuesta.getCountry() == null
                    ? null : respuesta.getCountry().getNames());
            Ubicacion u = new Ubicacion(ciudad, region, pais);
            return u.texto().isEmpty() ? Optional.empty() : Optional.of(u);
        } catch (GeoIp2Exception | java.io.IOException | IllegalArgumentException e) {
            // IP privada/reservada, fuera de la base o ilegible: sin ubicación.
            return Optional.empty();
        }
    }

    /** Nombre en español si existe, si no inglés, si no el primero. */
    static String nombre(Map<String, String> nombres) {
        if (nombres == null || nombres.isEmpty()) {
            return null;
        }
        if (nombres.containsKey("es")) {
            return nombres.get("es");
        }
        if (nombres.containsKey("en")) {
            return nombres.get("en");
        }
        return nombres.values().iterator().next();
    }
}
