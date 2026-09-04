package mx.ferreteria.api.rh.repo;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import mx.ferreteria.api.rh.dto.EmpleadoDtos.EmpleadoResumen;
import mx.ferreteria.api.rh.service.EmpleadoGateway;

/**
 * Adaptador JDBC del CRUD de empleados. SQL nativo exacto a rh.empleados
 * (02_tablas.sql). Baja = fecha_baja + activo=false (nunca DELETE físico: es
 * referencia de seg.usuarios y rh.nominas).
 */
@Repository
@RequiredArgsConstructor
public class EmpleadoRepository implements EmpleadoGateway {

    private static final String CAMPOS = """
            e.empleado_id, e.puesto_id, p.nombre AS puesto_nombre, e.nombre,
            e.apellido_p, e.apellido_m, e.curp, e.nss, e.telefono, e.email,
            e.calle, e.colonia, e.ciudad_id, e.cp, e.fecha_ingreso, e.fecha_baja,
            e.sueldo_diario, e.activo""";

    private final JdbcClient jdbc;

    @Override
    public List<EmpleadoRow> findEmpleados(int limit, int offset) {
        return jdbc.sql("SELECT " + CAMPOS + " FROM rh.empleados e "
                + "JOIN cat.puestos p ON p.puesto_id = e.puesto_id "
                + "WHERE e.activo "
                + "ORDER BY e.empleado_id LIMIT :lim OFFSET :off")
                .param("lim", limit).param("off", offset)
                .query(this::mapRow)
                .list();
    }

    @Override
    public long countEmpleados() {
        Long n = jdbc.sql("SELECT count(*) FROM rh.empleados WHERE activo").query(Long.class).single();
        return n == null ? 0 : n;
    }

    @Override
    public Optional<EmpleadoRow> findById(int empleadoId) {
        return jdbc.sql("SELECT " + CAMPOS + " FROM rh.empleados e "
                + "JOIN cat.puestos p ON p.puesto_id = e.puesto_id "
                + "WHERE e.empleado_id = :id AND e.activo")
                .param("id", empleadoId)
                .query(this::mapRow)
                .optional();
    }

    @Override
    public Optional<EmpleadoResumen> resumenById(int empleadoId) {
        return jdbc.sql("""
                SELECT e.empleado_id,
                       trim(concat(e.nombre, ' ', e.apellido_p, ' ', e.apellido_m))
                         AS nombre_completo,
                       p.nombre AS puesto_nombre, e.email, e.telefono, e.activo
                FROM rh.empleados e
                JOIN cat.puestos p ON p.puesto_id = e.puesto_id
                WHERE e.empleado_id = :id AND e.activo
                """)
                .param("id", empleadoId)
                .query((rs, n) -> new EmpleadoResumen(rs.getInt("empleado_id"),
                        rs.getString("nombre_completo"), rs.getString("puesto_nombre"),
                        rs.getString("email"), rs.getString("telefono"), rs.getBoolean("activo")))
                .optional();
    }

    @Override
    public boolean existsAndActivo(int empleadoId) {
        Boolean ok = jdbc.sql("SELECT EXISTS (SELECT 1 FROM rh.empleados "
                + "WHERE empleado_id = :id AND activo AND fecha_baja IS NULL)")
                .param("id", empleadoId)
                .query(Boolean.class)
                .single();
        return Boolean.TRUE.equals(ok);
    }

    @Override
    public int create(int puestoId, String nombre, String apellidoPaterno, String apellidoMaterno,
            String curp, String nss, String telefono, String email, String calle,
            String colonia, Integer ciudadId, String cp, LocalDate fechaIngreso,
            BigDecimal sueldoDiario) {
        return jdbc.sql("""
                INSERT INTO rh.empleados (puesto_id, nombre, apellido_p, apellido_m,
                    curp, nss, telefono, email, calle, colonia, ciudad_id, cp,
                    fecha_ingreso, sueldo_diario)
                VALUES (:pto, :n, :ap, :am, :curp, :nss, :tel, :em, :calle, :col,
                    :cd, :cp, :ingreso, :sueldo)
                RETURNING empleado_id
                """)
                .param("pto", puestoId).param("n", nombre).param("ap", apellidoPaterno)
                .param("am", apellidoMaterno).param("curp", curp).param("nss", nss)
                .param("tel", telefono).param("em", email).param("calle", calle)
                .param("col", colonia).param("cd", ciudadId).param("cp", cp)
                .param("ingreso", fechaIngreso == null ? null : Date.valueOf(fechaIngreso))
                .param("sueldo", sueldoDiario)
                .query(Integer.class)
                .single();
    }

    @Override
    public void update(int empleadoId, Integer puestoId, String nombre, String apellidoPaterno,
            String apellidoMaterno, String curp, String nss, String telefono, String email,
            String calle, String colonia, Integer ciudadId, String cp, LocalDate fechaIngreso,
            BigDecimal sueldoDiario, Boolean activo) {
        jdbc.sql("""
                UPDATE rh.empleados
                SET puesto_id = COALESCE(:p, puesto_id),
                    nombre = COALESCE(:n, nombre),
                    apellido_p = COALESCE(:ap, apellido_p),
                    apellido_m = COALESCE(:am, apellido_m),
                    curp = COALESCE(:curp, curp),
                    nss = COALESCE(:nss, nss),
                    telefono = COALESCE(:tel, telefono),
                    email = COALESCE(:em, email),
                    calle = COALESCE(:calle, calle),
                    colonia = COALESCE(:col, colonia),
                    ciudad_id = COALESCE(:cd, ciudad_id),
                    cp = COALESCE(:cp, cp),
                    fecha_ingreso = COALESCE(:ingreso, fecha_ingreso),
                    sueldo_diario = COALESCE(:su, sueldo_diario),
                    activo = COALESCE(:a, activo)
                WHERE empleado_id = :id
                """)
                .param("id", empleadoId).param("p", puestoId).param("n", nombre)
                .param("ap", apellidoPaterno).param("am", apellidoMaterno)
                .param("curp", curp).param("nss", nss).param("tel", telefono)
                .param("em", email).param("calle", calle).param("col", colonia)
                .param("cd", ciudadId).param("cp", cp)
                .param("ingreso", fechaIngreso == null ? null : Date.valueOf(fechaIngreso))
                .param("su", sueldoDiario).param("a", activo)
                .update();
    }

    @Override
    public void baja(int empleadoId) {
        jdbc.sql("UPDATE rh.empleados SET fecha_baja = CURRENT_DATE, activo = false "
                + "WHERE empleado_id = :id AND activo = true")
                .param("id", empleadoId)
                .update();
    }

    private EmpleadoRow mapRow(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        Date ing = rs.getDate("fecha_ingreso");
        Date baja = rs.getDate("fecha_baja");
        return new EmpleadoRow(rs.getInt("empleado_id"), rs.getInt("puesto_id"),
                rs.getString("puesto_nombre"), rs.getString("nombre"),
                rs.getString("apellido_p"), rs.getString("apellido_m"),
                rs.getString("curp"), rs.getString("nss"), rs.getString("telefono"),
                rs.getString("email"), rs.getString("calle"), rs.getString("colonia"),
                (Integer) rs.getObject("ciudad_id"), rs.getString("cp"),
                ing == null ? null : ing.toLocalDate(), baja == null ? null : baja.toLocalDate(),
                rs.getBigDecimal("sueldo_diario"), rs.getBoolean("activo"));
    }
}