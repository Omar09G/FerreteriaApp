package mx.ferreteria.api.rh.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
                       BigDecimal sueldoDiario, boolean activo) { }

    List<EmpleadoRow> findEmpleados(int limit, int offset);

    long countEmpleados();

    Optional<EmpleadoRow> findById(int empleadoId);

    Optional<EmpleadoResumen> resumenById(int empleadoId);

    boolean existsAndActivo(int empleadoId);

    int create(int puestoId, String nombre, String apellidoPaterno, String apellidoMaterno,
               String curp, String nss, String telefono, String email, String calle, String colonia,
               Integer ciudadId, String cp, LocalDate fechaIngreso, BigDecimal sueldoDiario);

    void update(int empleadoId, Integer puestoId, String nombre, String apellidoPaterno,
                String apellidoMaterno, String curp, String nss, String telefono, String email,
                String calle, String colonia, Integer ciudadId, String cp, LocalDate fechaIngreso,
                BigDecimal sueldoDiario, Boolean activo);

    void baja(int empleadoId);
}