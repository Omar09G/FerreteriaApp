package mx.ferreteria.api.seg.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.rh.dto.EmpleadoDtos.EmpleadoResumen;
import mx.ferreteria.api.rh.service.EmpleadoGateway;
import mx.ferreteria.api.seg.dto.SegAdminDtos.PermisosRequest;
import mx.ferreteria.api.seg.dto.SegAdminDtos.RolRequest;
import mx.ferreteria.api.seg.dto.SegAdminDtos.RolUpdateRequest;
import mx.ferreteria.api.seg.dto.SegAdminDtos.UsuarioCreateRequest;
import mx.ferreteria.api.seg.dto.SegAdminDtos.UsuarioPasswordRequest;
import mx.ferreteria.api.seg.dto.SegAdminDtos.UsuarioRolesRequest;
import mx.ferreteria.api.seg.dto.SegAdminDtos.UsuarioUpdateRequest;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SegAdminServiceTest {

    @Mock
    SegAdminGateway gateway;

    @Mock
    AuthUserGateway auth;

    @Mock
    EmpleadoGateway empleados;

    final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    SegAdminService service;

    private static SegAdminGateway.UsuarioRow U1 =
            new SegAdminGateway.UsuarioRow(11, "cajero1", "cajero1@x.mx", 42, true,
                    Instant.parse("2026-01-01T12:00:00Z"), Instant.parse("2026-01-01T12:00:00Z"));

    private static final EmpleadoResumen EMPLEADO_ACTIVO = new EmpleadoResumen(
            42, "Juan Pérez", "Vendedor", "cajero1@x.mx", "555", true);

    void setUp() {
        service = new SegAdminService(gateway, auth, empleados, encoder);
    }

    private void stubRolValido() {
        when(gateway.rolClavesActivas())
                .thenReturn(Set.of("VENDEDOR", "ALMACENISTA", "ADMINISTRADOR"));
    }

    @Test
    @DisplayName("listUsuarios: pagina de UsuarioResponse con roles resueltos")
    void listUsuarios_paginatesWithRoles() {
        setUp();
        when(gateway.findUsuarios(20, 0)).thenReturn(List.of(U1));
        when(gateway.countUsuarios()).thenReturn(1L);
        when(auth.rolesOf(11)).thenReturn(List.of("VENDEDOR"));

        var page = service.listUsuarios(PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).roles()).containsExactly("VENDEDOR");
        verify(gateway).findUsuarios(20, 0);
    }

    @Test
    @DisplayName("createUsuario: hashea el password, crea y asigna roles VALIDADOS")
    void createUsuario_hashesPasswordAndAssignsValidatedRoles() {
        setUp();
        stubRolValido();
        when(gateway.createUsuario(eq("nuevo01"), eq("nuevo01@x.mx"), anyString(),
                any(), anyBoolean())).thenReturn(11);
        when(gateway.findUsuarioById(11)).thenReturn(Optional.of(U1));
        when(auth.rolesOf(11)).thenReturn(List.of("VENDEDOR"));

        var r = service.createUsuario(new UsuarioCreateRequest(
                "nuevo01", "nuevo01@x.mx", "Secreta123", null, List.of("VENDEDOR")));

        assertThat(r.usuarioId()).isEqualTo(11);
        assertThat(r.roles()).containsExactly("VENDEDOR");
        verify(gateway).reemplazarRoles(11, Set.of("VENDEDOR"));
    }

    @Test
    @DisplayName("createUsuario con rol inexistente -> 400 REFERENCIA_INVALIDA y sin insertar rol")
    void createUsuario_unknownRole_rejected() {
        setUp();
        stubRolValido();
        when(gateway.createUsuario(eq("mal"), eq("mal@x.mx"), anyString(), any(), anyBoolean()))
                .thenReturn(99);
        when(gateway.findUsuarioById(99)).thenReturn(Optional.of(U1));

        assertThatThrownBy(() -> service.createUsuario(new UsuarioCreateRequest(
                "mal", "mal@x.mx", "Secreta123", null, List.of("ROLE_FANTASMA"))))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.REFERENCIA_INVALIDA));
        verify(gateway, never()).reemplazarRoles(anyInt(), any());
    }

    @Test
    @DisplayName("setRoles: reemplazo atomico; lista vacia limpia roles")
    void setRoles_replacesAndEmptyClears() {
        setUp();
        stubRolValido();
        when(gateway.findUsuarioById(11)).thenReturn(Optional.of(U1));
        when(auth.rolesOf(11)).thenReturn(List.of());

        service.setRoles(11, new UsuarioRolesRequest(List.of()));
        verify(gateway).reemplazarRoles(11, Set.of());

        when(auth.rolesOf(11)).thenReturn(List.of("ALMACENISTA"));
        service.setRoles(11, new UsuarioRolesRequest(List.of("ALMACENISTA")));
        verify(gateway).reemplazarRoles(11, Set.of("ALMACENISTA"));
    }

    @Test
    @DisplayName("updateUsuario: delega parches basicos y devuelve usuario actualizado")
    void updateUsuario_patchesBasics() {
        setUp();
        when(gateway.findUsuarioById(11)).thenReturn(Optional.of(U1));
        when(auth.rolesOf(11)).thenReturn(List.of());

        var r = service.updateUsuario(11, new UsuarioUpdateRequest(null, null, null, false));

        verify(gateway).updateUsuarioBasico(11, null, null, null, false);
        assertThat(r.activo()).isTrue();  // el row stub no cambia; el update ya quedo verificado
    }

    @Test
    @DisplayName("resetPassword: exigue usuario existente, guarda hash BCrypt nuevo")
    void resetPassword_hashesNewPassword() {
        setUp();
        when(gateway.findUsuarioById(11)).thenReturn(Optional.of(U1));

        service.resetPassword(11, new UsuarioPasswordRequest("NuevaClave99"));

        org.mockito.ArgumentCaptor<String> hash =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(gateway).actualizarPassword(eq(11), hash.capture());
        assertThat(hash.getValue()).isNotEqualTo("NuevaClave99");
        assertThat(encoder.matches("NuevaClave99", hash.getValue())).isTrue();
    }

    @Test
    @DisplayName("deleteUsuario/getUsuario inexistente: soft-delete y 404")
    void deleteAndGet_guardanExistencias() {
        setUp();
        when(gateway.findUsuarioById(11)).thenReturn(Optional.of(U1));
        service.deleteUsuario(11);
        verify(gateway).borrarUsuario(11);

        when(gateway.findUsuarioById(123)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getUsuario(123))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO));
    }

    @Test
    @DisplayName("rol: crear con activo default true, actualizar y desactivar")
    void rolCrud() {
        setUp();
        when(gateway.createRol("SUPERVISOR", "Supervisor", null, true)).thenReturn(5);
        when(gateway.findRolById(5)).thenReturn(Optional.of(
                new SegAdminGateway.RolRow(5, "SUPERVISOR", "Supervisor", null, true)));
        when(gateway.permisosDe(5)).thenReturn(List.of());

        var creado = service.createRol(new RolRequest("SUPERVISOR", "Supervisor", null, null));
        assertThat(creado.clave()).isEqualTo("SUPERVISOR");
        assertThat(creado.activo()).isTrue();

        when(gateway.findRolById(5)).thenReturn(Optional.of(
                new SegAdminGateway.RolRow(5, "SUPERVISOR", "Supervisor", null, false)));
        service.updateRol(5, new RolUpdateRequest(null, null, false));
        verify(gateway).updateRol(5, null, null, false);

        service.deleteRol(5);
        verify(gateway).desactivarRol(5);
    }

    @Test
    @DisplayName("rol inexistente al actualizar/consultar permisos -> 404 RECURSO_NO_ENCONTRADO")
    void rolMissing_throws404() {
        setUp();
        when(gateway.findRolById(9)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getRol(9))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO));
    }

    @Test
    @DisplayName("setPermisos: valida claves contra catalogo y reemplaza sin duplicar")
    void setPermisos_validatesAndReplaces() {
        setUp();
        when(gateway.findRolById(5)).thenReturn(Optional.of(
                new SegAdminGateway.RolRow(5, "SUPERVISOR", "Supervisor", null, true)));
        when(gateway.permisoClaves()).thenReturn(Set.of("V.VENDER", "V.CANCELAR"));
        when(gateway.permisosDe(5)).thenReturn(List.of("V.VENDER"));

        var r = service.setPermisos(5, new PermisosRequest(List.of("V.VENDER")));
        assertThat(r).containsExactly("V.VENDER");
        verify(gateway).reemplazarPermisos(5, Set.of("V.VENDER"));

        assertThatThrownBy(() -> service.setPermisos(5,
                new PermisosRequest(List.of("X.INVENTADO"))))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.REFERENCIA_INVALIDA));
    }

    @Test
    @DisplayName("listPermisos/getPermiso: pagina y 404 cuando no existe")
    void permisosListAndGet() {
        setUp();
        var p = new SegAdminGateway.PermisoRow(1, "V.VENDER", "Registrar ventas");
        when(gateway.findPermisos(20, 0)).thenReturn(List.of(p));
        when(gateway.countPermisos()).thenReturn(1L);

        var page = service.listPermisos(PageRequest.of(0, 20));
        assertThat(page.getContent().get(0).clave()).isEqualTo("V.VENDER");

        when(gateway.findPermisoById(1)).thenReturn(Optional.of(p));
        assertThat(service.getPermiso(1).descripcion()).isEqualTo("Registrar ventas");

        when(gateway.findPermisoById(2)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getPermiso(2))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("createUsuario con empleado: email coherente se conserva y se incluye el resumen")
    void createUsuario_withEmpleado_validEmailConsistency() {
        setUp();
        stubRolValido();
        when(empleados.resumenById(42)).thenReturn(Optional.of(EMPLEADO_ACTIVO));
        when(gateway.createUsuario(eq("nuevo01"), eq("cajero1@x.mx"), anyString(),
                eq(42), anyBoolean())).thenReturn(11);
        when(gateway.findUsuarioById(11)).thenReturn(Optional.of(U1));
        when(auth.rolesOf(11)).thenReturn(List.of("VENDEDOR"));

        var r = service.createUsuario(new UsuarioCreateRequest(
                "nuevo01", "cajero1@x.mx", "Secreta123", 42, List.of("VENDEDOR")));

        assertThat(r.empleadoId()).isEqualTo(42);
        assertThat(r.empleado()).isEqualTo(EMPLEADO_ACTIVO);
        verify(gateway).createUsuario(eq("nuevo01"), eq("cajero1@x.mx"), anyString(),
                eq(42), eq(true));
    }

    @Test
    @DisplayName("createUsuario sin email y con empleado: email se toma del empleado")
    void createUsuario_empleadoEmailSink() {
        setUp();
        stubRolValido();
        when(empleados.resumenById(42)).thenReturn(Optional.of(EMPLEADO_ACTIVO));
        when(gateway.createUsuario(eq("nuevo01"), eq("cajero1@x.mx"), anyString(),
                eq(42), anyBoolean())).thenReturn(11);
        when(gateway.findUsuarioById(11)).thenReturn(Optional.of(U1));
        when(auth.rolesOf(11)).thenReturn(List.of());

        service.createUsuario(new UsuarioCreateRequest(
                "nuevo01", null, "Secreta123", 42, List.of()));

        verify(gateway).createUsuario(eq("nuevo01"), eq("cajero1@x.mx"), anyString(),
                eq(42), eq(true));
    }

    @Test
    @DisplayName("createUsuario con email distinto al del empleado -> 400 VALOR_INVALIDO")
    void createUsuario_empleadoEmailMismatch_rejected() {
        setUp();
        when(empleados.resumenById(42)).thenReturn(Optional.of(EMPLEADO_ACTIVO));

        assertThatThrownBy(() -> service.createUsuario(new UsuarioCreateRequest(
                "nuevo01", "otro@x.mx", "Secreta123", 42, List.of())))
                .isInstanceOfSatisfying(
                        mx.ferreteria.api.common.error.ValidacionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.VALOR_INVALIDO));
        verify(gateway, never()).createUsuario(anyString(), anyString(), anyString(), any(), anyBoolean());
    }

    @Test
    @DisplayName("createUsuario con empleado inexistente o inactivo -> 400 REFERENCIA_INVALIDA")
    void createUsuario_empleadoInvalido_rejected() {
        setUp();
        when(empleados.resumenById(999)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createUsuario(new UsuarioCreateRequest(
                "nuevo01", null, "Secreta123", 999, List.of())))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.REFERENCIA_INVALIDA));

        var inactivo = new EmpleadoResumen(42, "Juan", "Vendedor", null, null, false);
        when(empleados.resumenById(42)).thenReturn(Optional.of(inactivo));
        assertThatThrownBy(() -> service.createUsuario(new UsuarioCreateRequest(
                "nuevo01", null, "Secreta123", 42, List.of())))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.REFERENCIA_INVALIDA));
        verify(gateway, never()).createUsuario(anyString(), anyString(), anyString(), any(), anyBoolean());
    }

    @Test
    @DisplayName("toUsuario: el resumen del empleado se enriquece en cada respuesta")
    void usuarioResponse_incluyeEmpleado() {
        setUp();
        when(gateway.findUsuarios(20, 0)).thenReturn(List.of(U1));
        when(gateway.countUsuarios()).thenReturn(1L);
        when(auth.rolesOf(11)).thenReturn(List.of("VENDEDOR"));
        when(empleados.resumenById(42)).thenReturn(Optional.of(EMPLEADO_ACTIVO));

        var page = service.listUsuarios(PageRequest.of(0, 20));

        assertThat(page.getContent().get(0).empleado().nombreCompleto()).isEqualTo("Juan Pérez");
        assertThat(page.getContent().get(0).empleado().puestoNombre()).isEqualTo("Vendedor");
    }

    @Test
    @DisplayName("crearUsuarioConRoles (puerto rh): BCrypt + roles validados + reemplazo")
    void crearUsuarioConRoles_delegaCreaYValida() {
        setUp();
        stubRolValido();
        when(gateway.createUsuario(eq("juan.perez"), eq("cajero1@x.mx"), anyString(),
                eq(42), anyBoolean())).thenReturn(11);

        int id = service.crearUsuarioConRoles("juan.perez", "cajero1@x.mx", "Secreta123",
                42, List.of("VENDEDOR"));

        assertThat(id).isEqualTo(11);
        verify(gateway).reemplazarRoles(11, Set.of("VENDEDOR"));
        org.mockito.ArgumentCaptor<String> hash =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(gateway).createUsuario(eq("juan.perez"), eq("cajero1@x.mx"), hash.capture(),
                eq(42), eq(true));
        assertThat(encoder.matches("Secreta123", hash.getValue())).isTrue();
    }

    @Test
    @DisplayName("crearUsuarioConRoles con rol inexistente -> 400 REFERENCIA_INVALIDA")
    void crearUsuarioConRoles_rolInvalido_rejected() {
        setUp();
        stubRolValido();
        when(gateway.createUsuario(anyString(), anyString(), anyString(), any(), anyBoolean()))
                .thenReturn(11);

        assertThatThrownBy(() -> service.crearUsuarioConRoles("juan", "juan@x.mx", "Secreta123",
                42, List.of("ROLE_FANTASMA")))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.REFERENCIA_INVALIDA));
        verify(gateway, never()).reemplazarRoles(anyInt(), any());
    }
}