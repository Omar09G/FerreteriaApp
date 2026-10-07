package mx.ferreteria.api.seg.repo;

import java.util.List;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Destinatarios del informe diario: usuarios activos con rol GERENTE o
 * ADMINISTRADOR que tengan correo en {@code seg.usuarios} o WhatsApp en
 * {@code rh.empleados}. Solo SQL de lectura, espejo de 02_tablas.sql.
 */
@Repository
@RequiredArgsConstructor
public class InformeDestinatarioRepository {

    private final JdbcClient jdbc;

    public record DestinatarioInforme(String email, String whatsapp) {
    }

    public List<DestinatarioInforme> findGerentesYAdministradores() {
        return jdbc.sql("""
                        SELECT NULLIF(u.email, '') AS email, NULLIF(e.whatsapp, '') AS whatsapp
                        FROM seg.usuarios u
                        JOIN seg.usuario_roles ur ON ur.usuario_id = u.usuario_id
                        JOIN seg.roles r ON r.rol_id = ur.rol_id
                        LEFT JOIN rh.empleados e ON e.empleado_id = u.empleado_id
                        WHERE r.clave IN ('GERENTE', 'ADMINISTRADOR')
                          AND u.activo AND u.eliminado_en IS NULL
                          AND (NULLIF(u.email, '') IS NOT NULL OR NULLIF(e.whatsapp, '') IS NOT NULL)
                        ORDER BY u.usuario_id
                        """)
                .query((rs, n) -> new DestinatarioInforme(rs.getString("email"), rs.getString("whatsapp")))
                .list();
    }

    /**
     * IDs de GERENTES y ADMINISTRADORES activos para la bandeja en tiempo
     * real (SSE). Sin filtro de contacto: la bandeja no necesita email ni
     * WhatsApp, solo el usuario.
     */
    public List<Integer> findGerenteAdminIds() {
        return jdbc.sql("""
                        SELECT DISTINCT u.usuario_id
                        FROM seg.usuarios u
                        JOIN seg.usuario_roles ur ON ur.usuario_id = u.usuario_id
                        JOIN seg.roles r ON r.rol_id = ur.rol_id
                        WHERE r.clave IN ('GERENTE', 'ADMINISTRADOR')
                          AND u.activo AND u.eliminado_en IS NULL
                        ORDER BY u.usuario_id
                        """)
                .query((rs, n) -> rs.getInt("usuario_id"))
                .list();
    }

    /** Usuario activo ligado a un empleado (para avisos propios: nómina). */
    public java.util.Optional<Integer> findUsuarioIdByEmpleadoId(Integer empleadoId) {
        return jdbc.sql("""
                        SELECT u.usuario_id
                        FROM seg.usuarios u
                        WHERE u.empleado_id = :empleadoId
                          AND u.activo AND u.eliminado_en IS NULL
                        """)
                .param("empleadoId", empleadoId)
                .query((rs, n) -> rs.getInt("usuario_id"))
                .optional();
    }
}
