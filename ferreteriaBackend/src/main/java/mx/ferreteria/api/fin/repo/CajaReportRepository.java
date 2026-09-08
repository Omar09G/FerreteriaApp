package mx.ferreteria.api.fin.repo;

import java.math.BigDecimal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import mx.ferreteria.api.fin.entity.Caja;

/**
 * Consultas nativas del módulo fin ejecutadas desde CajaService. Centraliza
 * aquí el acceso a BD que antes vivía en {@code CajaService} con
 * {@code JdbcTemplate} para que toda la lógica de persistencia quede dentro de
 * repositorios.
 *
 * <p>Se extiende {@link JpaRepository} únicamente para que Spring Data detecte
 * la interfaz como repositorio; los métodos CRUD heredados no se usan.</p>
 */
public interface CajaReportRepository extends JpaRepository<Caja, Integer> {

    /**
     * Folio de la compra ligada a un pago a proveedor. Se llega al folio
     * navegando por la cuenta por pagar para soportar tanto pagos directos
     * como abonos parciales.
     */
    @Query(value = """
            SELECT c.folio
            FROM com.pagos_proveedor p
            JOIN com.cuentas_pagar cp ON cp.cuenta_pagar_id = p.cuenta_pagar_id
            JOIN com.compras c ON c.compra_id = cp.compra_id
            WHERE p.pago_proveedor_id = :pagoProveedorId
            """, nativeQuery = true)
    String findFolioPagoProveedor(@Param("pagoProveedorId") Long pagoProveedorId);

    /**
     * Resumen económico de un turno para la previsualización del corte:
     * monto de apertura y agregados de entradas/salidas en efectivo. Se
     * proyecta al record anidado {@code CajaService.ResumenTurnoRow} via
     * default method (Spring Data no convierte Tuple native query a record).
     */
    @Query(value = """
            SELECT t.monto_apertura AS monto_apertura,
                   COALESCE(SUM(mc.monto) FILTER (
                       WHERE mc.tipo = 'ENTRADA'
                         AND COALESCE(fp.es_efectivo, true)), 0) AS entradas_efectivo,
                   COALESCE(SUM(mc.monto) FILTER (
                       WHERE mc.tipo = 'SALIDA'
                         AND COALESCE(fp.es_efectivo, true)), 0) AS salidas_efectivo
            FROM fin.turnos_caja t
            LEFT JOIN fin.movimientos_caja mc ON mc.turno_caja_id = t.turno_caja_id
                AND mc.concepto <> 'APERTURA'
            LEFT JOIN cat.formas_pago fp ON fp.forma_pago_id = mc.forma_pago_id
            WHERE t.turno_caja_id = :turnoId
            GROUP BY t.monto_apertura
            """, nativeQuery = true)
    Object[] findResumenTurnoRaw(@Param("turnoId") Long turnoId);

    default mx.ferreteria.api.fin.service.CajaService.ResumenTurnoRow findResumenTurno(Long turnoId) {
        Object[] row = findResumenTurnoRaw(turnoId);
        if (row == null) {
            return new mx.ferreteria.api.fin.service.CajaService.ResumenTurnoRow(
                    java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO);
        }
        java.math.BigDecimal apertura = row[0] == null ? java.math.BigDecimal.ZERO : (java.math.BigDecimal) row[0];
        java.math.BigDecimal entradas = row[1] == null ? java.math.BigDecimal.ZERO : (java.math.BigDecimal) row[1];
        java.math.BigDecimal salidas = row[2] == null ? java.math.BigDecimal.ZERO : (java.math.BigDecimal) row[2];
        return new mx.ferreteria.api.fin.service.CajaService.ResumenTurnoRow(apertura, entradas, salidas);
    }

    /**
     * Cierra el turno llamando a la función PL/pgSQL {@code fin.fn_cerrar_turno},
     * que materializa el corte y devuelve su identificador.
     */
    @Query(value = "SELECT fin.fn_cerrar_turno(:turnoId, :montoReal, :notas, :usuarioId)",
            nativeQuery = true)
    Long cerrarTurno(@Param("turnoId") Long turnoId,
            @Param("montoReal") BigDecimal montoReal,
            @Param("notas") String notas,
            @Param("usuarioId") Integer usuarioId);
}