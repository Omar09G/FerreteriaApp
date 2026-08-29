package mx.ferreteria.api.seg.repo;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import mx.ferreteria.api.seg.service.AuditoriaGateway;

/**
 * Adaptador JDBC del listado de auditoría (seg.auditoria). SQL nativo exacto
 * al esquema seg.* (02_tablas.sql). El usuario se resuelve con LEFT JOIN a
 * seg.usuarios para tolerar referencias a usuarios soft-deleted.
 */
@Repository
@RequiredArgsConstructor
public class AuditoriaRepository implements AuditoriaGateway {

    private static final String CAMPOS =
            "a.auditoria_id, a.esquema, a.tabla, a.registro_id, a.accion, "
          + "a.datos_anteriores::text AS datos_anteriores, "
          + "a.datos_nuevos::text AS datos_nuevos, "
          + "a.usuario_id, u.username AS usuario, a.creado_en";

    private final JdbcClient jdbc;

    @Override
    public List<AuditoriaRow> buscar(Filtro f) {
        var where = new ArrayList<String>();
        var params = new LinkedHashMap<String, Object>();
        addIfNotBlank(f.tabla(),       where, params, "a.tabla = :tabla",        "tabla", f.tabla());
        addIfNotBlank(f.accion(),      where, params, "a.accion = :accion",      "accion", f.accion());
        addIfNotBlank(f.esquema(),     where, params, "a.esquema = :esquema",    "esquema", f.esquema());
        addIfPresent(f.registroId(),   where, params, "a.registro_id = :rid",    "rid", f.registroId());
        addIfPresentLazy(f.desde(),        where, params, "a.creado_en >= :desde",   "desde", () -> Timestamp.from(f.desde()));
        addIfPresentLazy(f.hasta(),        where, params, "a.creado_en <  :hasta",   "hasta", () -> Timestamp.from(f.hasta()));
        addIfNotBlankLazy(f.usuario(),     where, params, "u.username ILIKE :usr",    "usr",  () -> likeWildcard(f.usuario().trim()));
        addIfNotBlankLazy(f.texto(),       where, params,
                "(a.datos_anteriores::text ILIKE :txt OR a.datos_nuevos::text ILIKE :txt)",
                "txt", () -> likeWildcard(f.texto().trim()));

        String whereSql = where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where);
        String sql = "SELECT " + CAMPOS + " FROM seg.auditoria a "
                   + "LEFT JOIN seg.usuarios u ON u.usuario_id = a.usuario_id"
                   + whereSql
                   + " ORDER BY a.auditoria_id DESC LIMIT :lim OFFSET :off";

        params.put("lim", f.limit());
        params.put("off", f.offset());

        return jdbc.sql(sql).params(params).query(this::mapRow).list();
    }

    @Override
    public long contar(Filtro f) {
        var where = new ArrayList<String>();
        var params = new LinkedHashMap<String, Object>();
        addIfNotBlank(f.tabla(),       where, params, "a.tabla = :tabla",        "tabla", f.tabla());
        addIfNotBlank(f.accion(),      where, params, "a.accion = :accion",      "accion", f.accion());
        addIfNotBlank(f.esquema(),     where, params, "a.esquema = :esquema",    "esquema", f.esquema());
        addIfPresent(f.registroId(),   where, params, "a.registro_id = :rid",    "rid", f.registroId());
        addIfPresentLazy(f.desde(),        where, params, "a.creado_en >= :desde",   "desde", () -> Timestamp.from(f.desde()));
        addIfPresentLazy(f.hasta(),        where, params, "a.creado_en <  :hasta",   "hasta", () -> Timestamp.from(f.hasta()));
        addIfNotBlankLazy(f.usuario(),     where, params, "u.username ILIKE :usr",    "usr",  () -> likeWildcard(f.usuario().trim()));
        addIfNotBlankLazy(f.texto(),       where, params,
                "(a.datos_anteriores::text ILIKE :txt OR a.datos_nuevos::text ILIKE :txt)",
                "txt", () -> likeWildcard(f.texto().trim()));

        String whereSql = where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where);
        String sql = "SELECT COUNT(*) FROM seg.auditoria a "
                   + "LEFT JOIN seg.usuarios u ON u.usuario_id = a.usuario_id"
                   + whereSql;

        Long total = jdbc.sql(sql).params(params).query(Long.class).single();
        return total == null ? 0L : total;
    }

    @Override
    public List<TablaRow> tablas() {
        return jdbc.sql("SELECT DISTINCT esquema, tabla FROM seg.auditoria ORDER BY esquema, tabla")
                .query((rs, n) -> new TablaRow(rs.getString(1), rs.getString(2)))
                .list();
    }

    /* ---------- helpers ---------- */

    private static void addIfNotBlank(String value, List<String> where, Map<String, Object> params,
                                      String condition, String key, Object paramValue) {
        if (value != null && !value.isBlank()) {
            where.add(condition);
            params.put(key, paramValue);
        }
    }

    /** Variante que evalúa perezoso: evita NPE cuando value es null. */
    private static void addIfNotBlankLazy(String value, List<String> where, Map<String, Object> params,
                                           String condition, String key, java.util.function.Supplier<Object> paramSupplier) {
        if (value != null && !value.isBlank()) {
            where.add(condition);
            params.put(key, paramSupplier.get());
        }
    }

    private static void addIfPresent(Object value, List<String> where, Map<String, Object> params,
                                     String condition, String key, Object paramValue) {
        if (value != null) {
            where.add(condition);
            params.put(key, paramValue);
        }
    }

    /** Variante perezosa para no invocar al supplier cuando el filtro es null. */
    private static void addIfPresentLazy(Object value, List<String> where, Map<String, Object> params,
                                          String condition, String key, java.util.function.Supplier<Object> paramSupplier) {
        if (value != null) {
            where.add(condition);
            params.put(key, paramSupplier.get());
        }
    }

    /**
     * Envuelve un texto como argumento ILIKE de Postgres escapando los
     * comodines {@code %} y {@code _} para que un usuario que los escribe
     * los busque literales en vez de disparar matcheos demasiado amplios.
     * {@code \\} se escapa primero para no romper el escape introducido.
     */
    private static String likeWildcard(String s) {
        String escaped = s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private AuditoriaRow mapRow(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        Timestamp ts = rs.getTimestamp("creado_en");
        return new AuditoriaRow(
                rs.getLong("auditoria_id"),
                rs.getString("esquema"),
                rs.getString("tabla"),
                rs.getLong("registro_id"),
                rs.getString("accion"),
                rs.getString("datos_anteriores"),
                rs.getString("datos_nuevos"),
                (Integer) rs.getObject("usuario_id"),
                rs.getString("usuario"),
                ts == null ? null : ts.toInstant());
    }
}