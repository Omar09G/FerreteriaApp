package mx.ferreteria.api.common.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Zona horaria canónica del negocio: {@code America/Mexico_City}.
 * <p>
 * Alineada con {@code app.zona}, {@code hibernate.jdbc.time_zone} y el
 * timezone de PostgreSQL (ver {@code application.yml} y
 * {@code V1__base.sql}: {@code ALTER DATABASE/ROLE ... SET timezone}).
 * <p>
 * Usar {@link #hoy()} en vez de {@code LocalDate.now()} en cada punto de
 * decisión de negocio (defaults de fecha, validaciones, cortes de periodo):
 * {@code LocalDate.now()} usa la zona por defecto de la JVM, que en
 * contenedores suele ser UTC y alrededor de medianoche entrega el día
 * equivocado. El pin de arranque en
 * {@link mx.ferreteria.api.FerreteriaApplication} cubre además entidades,
 * librerías y cualquier {@code now()} restante.
 */
public final class ZonaHoraria {

    /** Zona canónica. Constante única: no duplicar el literal en otro archivo. */
    public static final ZoneId ZONA = ZoneId.of("America/Mexico_City");

    private ZonaHoraria() {
    }

    /** Día actual en la zona del negocio. */
    public static LocalDate hoy() {
        return LocalDate.now(ZONA);
    }

    /** Instante actual (UTC, sin zona). Equivalente a {@link Instant#now()}. */
    public static Instant ahora() {
        return Instant.now();
    }

    /** Reloj del negocio para APIs que piden {@link Clock} o {@link ZoneId}. */
    public static Clock reloj() {
        return Clock.system(ZONA);
    }
}
