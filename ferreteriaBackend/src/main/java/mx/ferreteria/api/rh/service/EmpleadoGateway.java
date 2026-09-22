package mx.ferreteria.api.rh.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import mx.ferreteria.api.rh.dto.EmpleadoDtos.EmpleadoResumen;

/**
 * Puerta de persistencia del CRUD de empleados (rh.empleados). Base del alta de
 * usuarios: seg.usuarios.empleado_id → rh.empleados. Implementación JDBC en
 * {rh.repo}.
 */
public interface EmpleadoGateway {

    record EmpleadoRow(int empleadoId, int puestoId, String puestoNombre, String nombre,
                       String apellidoPaterno, String apellidoMaterno, String curp, String nss,
                       String telefono, String email, String calle, String colonia, Integer ciudadId,
                       String cp, LocalDate fechaIngreso, LocalDate fechaBaja,
                       BigDecimal sueldoDiario, boolean activo, String fotoUrl) { }

    record EmpleadoSueldo(int empleadoId, BigDecimal sueldoDiario) { }

    List<EmpleadoRow> findEmpleados(int limit, int offset);

    long countEmpleados();

    Optional<EmpleadoRow> findById(int empleadoId);

    Optional<EmpleadoResumen> resumenById(int empleadoId);

    /** Resumen agrupado por empleadoId. Vacío si la colección es vacía. Para evitar N+1 al listar. */
    Map<Integer, EmpleadoResumen> resumenByIds(Collection<Integer> empleadoIds);

    boolean existsAndActivo(int empleadoId);

    List<EmpleadoSueldo> findActivosConSueldo();

    /**
     * Datos de alta/parche de empleado. Agrupa los 15 campos para no
     * propagar listas posicionales de parámetros (propensas a swaps
     * silenciosos entre Strings).
     */
    record EmpleadoDatos(Integer puestoId, String nombre, String apellidoPaterno,
            String apellidoMaterno, String curp, String nss, String telefono, String email,
            String calle, String colonia, Integer ciudadId, String cp, LocalDate fechaIngreso,
            BigDecimal sueldoDiario, String fotoUrl) { }

    int create(EmpleadoDatos datos);

    void update(int empleadoId, EmpleadoDatos datos, Boolean activo);

    void baja(int empleadoId);
}