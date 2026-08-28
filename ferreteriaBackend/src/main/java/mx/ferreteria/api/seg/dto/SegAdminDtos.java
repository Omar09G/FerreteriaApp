package mx.ferreteria.api.seg.dto;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import mx.ferreteria.api.rh.dto.EmpleadoDtos.EmpleadoResumen;

/** Contratos del API de administración de seguridad (usuarios/roles/permisos). */
public final class SegAdminDtos {

    private SegAdminDtos() { }

    public record UsuarioCreateRequest(
            @NotBlank @Size(max = 40) String username,
            @NotBlank @Size(max = 120) String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            Integer empleadoId,
            List<@NotBlank @Size(max = 40) String> roles) { }

    public record UsuarioUpdateRequest(
            @Size(max = 40) String username,
            @Size(max = 120) String email,
            Integer empleadoId,
            Boolean activo) { }

    public record UsuarioPasswordRequest(
            @NotBlank @Size(min = 8, max = 100) String nuevaPassword) { }

    public record UsuarioRolesRequest(List<@NotBlank @Size(max = 40) String> roles) { }

    public record UsuarioResponse(
            int usuarioId,
            String username,
            String email,
            Integer empleadoId,
            boolean activo,
            List<String> roles,
            EmpleadoResumen empleado,
            Instant ultimoLogin,
            Instant creadoEn) { }

    public record RolRequest(
            @NotBlank @Size(max = 40) String clave,
            @NotBlank @Size(max = 80) String nombre,
            @Size(max = 200) String descripcion,
            Boolean activo) { }

    public record RolUpdateRequest(
            @Size(max = 80) String nombre,
            @Size(max = 200) String descripcion,
            Boolean activo) { }

    public record RolResponse(
            int rolId,
            String clave,
            String nombre,
            String descripcion,
            boolean activo,
            List<String> permisos) { }

    public record PermisosRequest(List<@NotBlank @Size(max = 40) String> permisos) { }

    public record PermisoResponse(
            int permisoId,
            String clave,
            String descripcion) { }

    public record OperacionOk(boolean ok) { }
}