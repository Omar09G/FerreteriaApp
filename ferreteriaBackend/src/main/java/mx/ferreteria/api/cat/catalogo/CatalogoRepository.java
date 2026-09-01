package mx.ferreteria.api.cat.catalogo;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import mx.ferreteria.api.cat.catalogo.Campo.Tipo;

/**
 * Persistencia JDBC del CRUD genérico de catálogos. Los nombres de columna
 * SIEMPRE provienen del descriptor estático (Catalogos), nunca del cliente;
 * los valores se vinculan como parámetros (:v). La notación de esquema "x.y"
 * se traduce a un alias seguro "x_y" para las consultas.
 */
@Repository
public class CatalogoRepository {

    private final JdbcClient jdbc;

    public CatalogoRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static String alias(String tabla) {
        return tabla.replace('.', '_');
    }

    private static String fq(String tabla) {
        return String.join("\".\"", tabla.split("\\.")).concat("\"").replaceFirst("^", "\"");
    }

    private static String col(String tabla, String columna) {
        return alias(tabla) + "." + columna;
    }

    public long count(Catalogo c, String qTexto) {
        SqlParts where = whereInactivos(c, qTexto);
        var spec = jdbc.sql("SELECT count(*) FROM " + fq(c.tabla()) + " " + alias(c.tabla()) + where.sql());
        spec = bind(spec, where.params());
        Long n = spec.query(Long.class).single();
        return n == null ? 0 : n;
    }

    public List<Map<String, Object>> list(Catalogo c, int limit, int offset, String sort, String qTexto) {
        StringBuilder cols = new StringBuilder();
        String t = alias(c.tabla());
        for (Campo cnt : c.campos()) {
            if (cols.length() > 0) {
                cols.append(", ");
            }
            cols.append(col(c.tabla(), cnt.nombre())).append(" AS \"").append(cnt.nombre()).append("\"");
        }
        SqlParts where = whereInactivos(c, qTexto);
        StringBuilder sql = new StringBuilder("SELECT ").append(cols).append(", ").append(col(c.tabla(), c.pk()))
                .append(" AS \"__pk\" FROM ").append(fq(c.tabla())).append(" ").append(t).append(where.sql());
        if (sort != null && !sort.isBlank() && esSegura(sort)) {
            sql.append(" ORDER BY ").append(col(c.tabla(), sort));
        } else {
            sql.append(" ORDER BY ").append(col(c.tabla(), c.pk()));
        }
        sql.append(" LIMIT :lim OFFSET :off");
        var spec = jdbc.sql(sql.toString());
        spec = bind(spec, where.params());
        spec = spec.param("lim", limit).param("off", offset);
        return spec.query((rs, i) -> {
            Map<String, Object> fila = new LinkedHashMap<>();
            for (Campo cnt : c.campos()) {
                fila.put(cnt.nombre(), leer(rs, cnt));
            }
            fila.put("__pk", rs.getObject("__pk"));
            return fila;
        }).list();
    }

    private Object leer(ResultSet rs, Campo cnt) throws SQLException {
        switch (cnt.tipo()) {
            case BOOLEAN -> {
                Boolean b = rs.getBoolean(cnt.nombre());
                return rs.wasNull() ? null : b;
            }
            case NUMERO -> {
                return rs.getObject(cnt.nombre(), Integer.class);
            }
            case DECIMAL -> {
                return rs.getBigDecimal(cnt.nombre());
            }
            case FECHA -> {
                return rs.getDate(cnt.nombre());
            }
            default -> {
                return rs.getString(cnt.nombre());
            }
        }
    }

    private SqlParts whereInactivos(Catalogo c, String qTexto) {
        List<String> conds = new ArrayList<>();
        Map<String, Object> params = new LinkedHashMap<>();
        Campo activo = c.campoActivo();
        if (activo != null) {
            conds.add(col(c.tabla(), "activo") + " = true");
        }
        if (qTexto != null && !qTexto.isBlank()) {
            List<Campo> textuales = c.campos().stream().filter(x -> x.tipo() == Tipo.TEXT).toList();
            if (!textuales.isEmpty()) {
                List<String> likes = new ArrayList<>();
                for (int i = 0; i < textuales.size(); i++) {
                    likes.add(col(c.tabla(), textuales.get(i).nombre()) + " ILIKE :q" + i);
                    params.put("q" + i, "%" + qTexto + "%");
                }
                conds.add("(" + String.join(" OR ", likes) + ")");
            }
        }
        return new SqlParts(conds.isEmpty() ? "" : " WHERE " + String.join(" AND ", conds), params);
    }

    private boolean esSegura(String sort) {
        return sort.matches("[A-Za-z_][A-Za-z0-9_]*");
    }

    public Optional<Map<String, Object>> findById(Catalogo c, Object pk) {
        StringBuilder cols = new StringBuilder();
        for (Campo cnt : c.campos()) {
            if (cols.length() > 0) {
                cols.append(", ");
            }
            cols.append(col(c.tabla(), cnt.nombre())).append(" AS \"").append(cnt.nombre()).append("\"");
        }
        return jdbc.sql("SELECT " + cols + " FROM " + fq(c.tabla()) + " " + alias(c.tabla())
                        + " WHERE " + col(c.tabla(), c.pk()) + " = :id")
                .param("id", pk)
                .query((rs, i) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    for (Campo cnt : c.campos()) {
                        m.put(cnt.nombre(), leer(rs, cnt));
                    }
                    m.put("__pk", rs.getObject("__pk"));
                    return m;
                }).optional();
    }

    /**
     * Resuelve opciones de un dropdown FK. fkCat es el catálogo de la tabla
     * referenciada; devuelve [{clave, ...columnasMostrar}].
     */
    public List<Map<String, Object>> opciones(Catalogo fkCat, String campoClave, List<String> columnasMostrar, String qTexto) {
        StringBuilder cols = new StringBuilder(col(fkCat.tabla(), campoClave) + " AS \"clave\"");
        for (String columna : columnasMostrar) {
            cols.append(", ").append(col(fkCat.tabla(), columna)).append(" AS \"").append(columna).append("\"");
        }
        List<String> conds = new ArrayList<>();
        Map<String, Object> params = new LinkedHashMap<>();
        Campo activo = fkCat.campoActivo();
        if (activo != null) {
            conds.add(col(fkCat.tabla(), "activo") + " = true");
        }
        if (qTexto != null && !qTexto.isBlank() && !columnasMostrar.isEmpty()) {
            List<String> likes = new ArrayList<>();
            for (int i = 0; i < columnasMostrar.size(); i++) {
                likes.add(col(fkCat.tabla(), columnasMostrar.get(i)) + " ILIKE :q" + i);
                params.put("q" + i, "%" + qTexto + "%");
            }
            conds.add("(" + String.join(" OR ", likes) + ")");
        }
        String sql = "SELECT " + cols + " FROM " + fq(fkCat.tabla()) + " " + alias(fkCat.tabla())
                + (conds.isEmpty() ? "" : " WHERE " + String.join(" AND ", conds))
                + " ORDER BY " + col(fkCat.tabla(), columnasMostrar.isEmpty() ? campoClave : columnasMostrar.get(0))
                + " LIMIT :lim";
        var spec = jdbc.sql(sql);
        spec = bind(spec, params);
        spec = spec.param("lim", 500);
        return spec.query((rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("clave", rs.getObject("clave"));
            for (String columna : columnasMostrar) {
                m.put(columna, rs.getString(columna));
            }
            return m;
        }).list();
    }

    public boolean existeValor(Catalogo c, Object valor, Campo campoUnico) {
        Long n = jdbc.sql("SELECT count(*) FROM " + fq(c.tabla()) + " WHERE " + campoUnico.nombre() + " = :v")
                .param("v", valor).query(Long.class).single();
        return n != null && n > 0;
    }

    public boolean existeValorExcepto(Catalogo c, Object valor, Campo campoUnico, Object pk) {
        Long n = jdbc.sql("SELECT count(*) FROM " + fq(c.tabla()) + " WHERE " + campoUnico.nombre()
                        + " = :v AND " + c.pk() + " <> :pk")
                .param("v", valor).param("pk", pk).query(Long.class).single();
        return n != null && n > 0;
    }

    /**
     * Insert. Devuelve la PK generada (numérica-identity) o null para PK string
     * (la inserta el cliente vía campo en `valores`).
     */
    public Object insert(Catalogo c, Map<String, Object> valores) {
        Campo pkCampo = pkCampoEnCampos(c);
        List<Campo> columnas = new ArrayList<>(c.campos().stream()
                .filter(Campo::esPropiedadEditable).toList());
        if (pkCampo != null) {
            columnas.add(0, pkCampo);
        }
        StringBuilder cols = new StringBuilder();
        StringBuilder marks = new StringBuilder();
        List<String> keys = new ArrayList<>();
        for (Campo cnt : columnas) {
            if (!valores.containsKey(cnt.nombre())) {
                continue;
            }
            if (cols.length() > 0) {
                cols.append(", ");
                marks.append(", ");
            }
            cols.append(cnt.nombre());
            marks.append(":").append(cnt.nombre());
            keys.add(cnt.nombre());
        }
        var spec = jdbc.sql("INSERT INTO " + fq(c.tabla()) + " (" + cols + ") VALUES (" + marks + ")");
        for (String k : keys) {
            spec = spec.param(k, valores.get(k));
        }
        spec.update();
        return null;
    }

    public void update(Catalogo c, Object pk, Map<String, Object> valores) {
        StringBuilder set = new StringBuilder();
        List<String> keys = new ArrayList<>();
        for (Campo cnt : c.campos()) {
            if (!cnt.esPropiedadEditable() || !valores.containsKey(cnt.nombre())) {
                continue;
            }
            if (set.length() > 0) {
                set.append(", ");
            }
            set.append(cnt.nombre()).append(" = :").append(cnt.nombre());
            keys.add(cnt.nombre());
        }
        var spec = jdbc.sql("UPDATE " + fq(c.tabla()) + " SET " + set + " WHERE " + c.pk() + " = :pk")
                .param("pk", pk);
        for (String k : keys) {
            spec = spec.param(k, valores.get(k));
        }
        spec.update();
    }

    public void desactivar(Catalogo c, Object pk) {
        jdbc.sql("UPDATE " + fq(c.tabla()) + " SET activo = false WHERE " + c.pk() + " = :pk")
                .param("pk", pk).update();
    }

    public boolean existeRegistro(Catalogo c, Object pk) {
        Long n = jdbc.sql("SELECT count(*) FROM " + fq(c.tabla()) + " WHERE " + c.pk() + " = :pk")
                .param("pk", pk).query(Long.class).single();
        return n != null && n > 0;
    }

    /** True si el pk es un campo declarado en campos (PK string; la inserta el cliente). */
    public Campo pkCampoEnCampos(Catalogo c) {
        return c.campos().stream().filter(x -> x.nombre().equals(c.pk())).findFirst().orElse(null);
    }

    /** Valida que exista una fila con pkRef = valorRef en la tabla referenciada. */
    public boolean referenciaValida(String tabla, Object valorRef, String pkRef) {
        Long n = jdbc.sql("SELECT count(*) FROM " + fq(tabla) + " WHERE " + pkRef + " = :v")
                .param("v", valorRef).query(Long.class).single();
        return n != null && n > 0;
    }

    private JdbcClient.StatementSpec bind(JdbcClient.StatementSpec spec, Map<String, Object> params) {
        for (Map.Entry<String, Object> e : params.entrySet()) {
            spec = spec.param(e.getKey(), e.getValue());
        }
        return spec;
    }

    record SqlParts(String sql, Map<String, Object> params) {
    }
}
