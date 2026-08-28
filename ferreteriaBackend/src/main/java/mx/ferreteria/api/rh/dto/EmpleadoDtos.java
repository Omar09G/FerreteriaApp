package mx.ferreteria.api.rh.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Contratos del CRUD de empleados (rh.empleados): base para la creación de
 * usuarios del sistema (seg.usuarios.empleado_id).
 */
public final class EmpleadoDtos {

    private EmpleadoDtos() { }

    public record EmpleadoCreateRequest(
            @NotNull Integer puestoId,
            @NotBlank @Size(max = 80) String nombre,
            @NotBlank @Size(max = 80) String apellidoPaterno,
            @Size(max = 80) String apellidoMaterno,
            @Size(max = 18) String curp,
            @Size(max = 11) String nss,
            @Size(max = 20) String telefono,
            @Size(max = 120) String email,
            @Size(max = 150) String calle,
            @Size(max = 100) String colonia,
            Integer ciudadId,
            @Size(max = 10) String cp,
            LocalDate fechaIngreso,
            @DecimalMin("0") BigDecimal sueldoDiario,
            @Size(max = 40) String username,
            @Size(min = 8, max = 100) String password,
            List<String> roles) {

        /** True si el alta además crea el usuario del sistema (seg.usuarios). */
        public boolean conUsuario() {
            return username != null;
        }
    }

    public record EmpleadoUpdateRequest(
            Integer puestoId,
            @Size(max = 80) String nombre,
            @Size(max = 80) String apellidoPaterno,
            @Size(max = 80) String apellidoMaterno,
            @Size(max = 18) String curp,
            @Size(max = 11) String nss,
            @Size(max = 20) String telefono,
            @Size(max = 120) String email,
            @Size(max = 150) String calle,
            @Size(max = 100) String colonia,
            Integer ciudadId,
            @Size(max = 10) String cp,
            LocalDate fechaIngreso,
            @DecimalMin("0") BigDecimal sueldoDiario,
            Boolean activo) { }

    public record EmpleadoResponse(
            int empleadoId,
            int puestoId,
            String puestoNombre,
            String nombre,
            String apellidoPaterno,
            String apellidoMaterno,
            String curp,
            String nss,
            String telefono,
            String email,
            String calle,
            String colonia,
            Integer ciudadId,
            String cp,
            LocalDate fechaIngreso,
            LocalDate fechaBaja,
            BigDecimal sueldoDiario,
            boolean activo) { }

    /** Información principal del empleado, para /me y respuestas de usuario. */
    public record EmpleadoResumen(
            int empleadoId,
            String nombreCompleto,
            String puestoNombre,
            String email,
            String telefono,
            boolean activo) { }

    public record EmpleadoOk(boolean ok) { }
}