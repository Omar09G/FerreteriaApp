package mx.ferreteria.api.seg.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
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
import mx.ferreteria.api.seg.dto.SegAdminDtos.PermisoRequest;
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
            42, "Juan Pérez", "Vendedor", "cajero1@x.mx", "555", true, null);

    @BeforeEach
    void setUp() {
        service = new SegAdminService(gateway, auth, empleados, encoder);
    }

    private void stubRolValido() {
        when(gateway.rolClavesActivas())
                .thenReturn(Set.of("VENDEDOR", "ALMACENISTA", "ADMINISTRADOR"));
    }

    @Test
    @DisplayName("listUsuarios: pagina de UsuarioResponse con roles resueltos (batch)")
    void listUsuarios_paginatesWithRoles() {
        when(gateway.findUsuarios(20, 0)).thenReturn(List.of(U1));
        when(gateway.countUsuarios()).thenReturn(1L);
        when(auth.rolesOfBatch(Set.of(11))).thenReturn(Map.of(11, List.of("VENDEDOR")));

        var page = service.listUsuarios(PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).roles()).containsExactly("VENDEDOR");
        verify(gateway).findUsuarios(20, 0);
        verify(auth, never()).rolesOf(anyInt());
    }

    @Test
    @DisplayName("listUsuarios: con múltiples usuarios ejecuta UNA sola query batch de roles")
    void listUsuarios_batchRoles_singleQuery() {
        var u2 = new SegAdminGateway.UsuarioRow(12, "cajero2", "c2@x.mx", null, true,
                Instant.parse("2026-01-01T12:00:00Z"), Instant.parse("2026-01-01T12:00:00Z"));
        when(gateway.findUsuarios(20, 0)).thenReturn(List.of(U1, u2));
        when(gateway.countUsuarios()).thenReturn(2L);
        when(auth.rolesOfBatch(Set.of(11, 12)))
                .thenReturn(Map.of(11, List.of("VENDEDOR"), 12, List.of("ALMACENISTA")));

        var page = service.listUsuarios(PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent().get(0).roles()).containsExactly("VENDEDOR");
        assertThat(page.getContent().get(1).roles()).containsExactly("ALMACENISTA");
        verify(auth, times(1)).rolesOfBatch(any());
        verify(auth, never()).rolesOf(anyInt());
    }

    @Test
    @DisplayName("createUsuario: hashea el password, crea y asigna roles VALIDADOS")
    void createUsuario_hashesPasswordAndAssignsValidatedRoles() {
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
        when(gateway.findUsuarioById(11)).thenReturn(Optional.of(U1));
        when(auth.rolesOf(11)).thenReturn(List.of());

        var r = service.updateUsuario(11, new UsuarioUpdateRequest(null, null, null, false));

        verify(gateway).updateUsuarioBasico(11, null, null, null, false);
        assertThat(r.activo()).isTrue();  // el row stub no cambia; el update ya quedo verificado
    }

    @Test
    @DisplayName("resetPassword: exigue usuario existente, guarda hash BCrypt nuevo")
    void resetPassword_hashesNewPassword() {
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
        when(gateway.findRolById(9)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getRol(9))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO));
    }

    @Test
    @DisplayName("setPermisos: valida claves contra catalogo y reemplaza sin duplicar")
    void setPermisos_validatesAndReplaces() {
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
    @DisplayName("deleteUsuario revoca refresh y cierra sesiones en la misma transacción")
    void deleteUsuario_revocaCredenciales() {
        when(gateway.findUsuarioById(11)).thenReturn(Optional.of(U1));
        service.deleteUsuario(11);
        verify(gateway).borrarUsuario(11);
        verify(gateway).revocarCredenciales(11);
    }

    @Test
    @DisplayName("resetPassword revoca refresh y cierra sesiones además de guardar hash")
    void resetPassword_revocaCredenciales() {
        when(gateway.findUsuarioById(11)).thenReturn(Optional.of(U1));
        service.resetPassword(11, new UsuarioPasswordRequest("NuevaClave99"));
        verify(gateway).actualizarPassword(eq(11), anyString());
        verify(gateway).revocarCredenciales(11);
    }

    @Test
    @DisplayName("deletePermiso en uso: 409 sin borrar (sin cascada silenciosa)")
    void deletePermiso_enUso_rechaza() {
        var p = new SegAdminGateway.PermisoRow(1, "V.VENDER", "Registrar ventas");
        when(gateway.findPermisoById(1)).thenReturn(Optional.of(p));
        when(gateway.countRolesConPermiso(1)).thenReturn(2L);
        assertThatThrownBy(() -> service.deletePermiso(1))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.REGISTRO_EN_USO));
        verify(gateway, never()).deletePermiso(1);
    }

    @Test
    @DisplayName("deletePermiso sin uso: borra")
    void deletePermiso_sinUso_borra() {
        var p = new SegAdminGateway.PermisoRow(1, "V.VENDER", "Registrar ventas");
        when(gateway.findPermisoById(1)).thenReturn(Optional.of(p));
        when(gateway.countRolesConPermiso(1)).thenReturn(0L);
        service.deletePermiso(1);
        verify(gateway).deletePermiso(1);
    }

    @Test
    @DisplayName("listPermisos/getPermiso: pagina y 404 cuando no existe")
    void permisosListAndGet() {
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
        when(empleados.resumenById(999)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createUsuario(new UsuarioCreateRequest(
                "nuevo01", null, "Secreta123", 999, List.of())))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.REFERENCIA_INVALIDA));

        var inactivo = new EmpleadoResumen(42, "Juan", "Vendedor", null, null, false, null);
        when(empleados.resumenById(42)).thenReturn(Optional.of(inactivo));
        assertThatThrownBy(() -> service.createUsuario(new UsuarioCreateRequest(
                "nuevo01", null, "Secreta123", 42, List.of())))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.REFERENCIA_INVALIDA));
        verify(gateway, never()).createUsuario(anyString(), anyString(), anyString(), any(), anyBoolean());
    }

    @Test
    @DisplayName("toUsuario: el resumen del empleado se enriquece en cada respuesta (batch)")
    void usuarioResponse_incluyeEmpleado() {
        when(gateway.findUsuarios(20, 0)).thenReturn(List.of(U1));
        when(gateway.countUsuarios()).thenReturn(1L);
        when(auth.rolesOfBatch(Set.of(11))).thenReturn(Map.of(11, List.of("VENDEDOR")));
        when(empleados.resumenByIds(Set.of(42))).thenReturn(Map.of(42, EMPLEADO_ACTIVO));

        var page = service.listUsuarios(PageRequest.of(0, 20));

        assertThat(page.getContent().get(0).empleado().nombreCompleto()).isEqualTo("Juan Pérez");
        assertThat(page.getContent().get(0).empleado().puestoNombre()).isEqualTo("Vendedor");
    }

    @Test
    @DisplayName("crearUsuarioConRoles (puerto rh): BCrypt + roles validados + reemplazo")
    void crearUsuarioConRoles_delegaCreaYValida() {
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
        stubRolValido();
        when(gateway.createUsuario(anyString(), anyString(), anyString(), any(), anyBoolean()))
                .thenReturn(11);

        assertThatThrownBy(() -> service.crearUsuarioConRoles("juan", "juan@x.mx", "Secreta123",
                42, List.of("ROLE_FANTASMA")))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.REFERENCIA_INVALIDA));
        verify(gateway, never()).reemplazarRoles(anyInt(), any());
    }

    @Test
    @DisplayName("crearUsuarioConRoles con roles null -> reemplaza con conjunto vacio")
    void crearUsuarioConRoles_rolesNull_limpia() {
        when(gateway.createUsuario(anyString(), anyString(), anyString(), any(), anyBoolean()))
                .thenReturn(11);

        int id = service.crearUsuarioConRoles("juan", "juan@x.mx", "Secreta123", 42, null);

        assertThat(id).isEqualTo(11);
        verify(gateway).reemplazarRoles(11, Set.of());
    }

    @Test
    @DisplayName("createUsuario: si el creado no se recupera -> 500 ERROR_INTERNO")
    void createUsuario_noRecuperado_errorInterno() {
        when(gateway.createUsuario(eq("nuevo01"), anyString(), anyString(), any(), anyBoolean()))
                .thenReturn(11);
        when(gateway.findUsuarioById(11)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createUsuario(new UsuarioCreateRequest(
                "nuevo01", "nuevo01@x.mx", "Secreta123", null, List.of())))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.ERROR_INTERNO));
        verify(gateway, never()).reemplazarRoles(anyInt(), any());
    }

    @Test
    @DisplayName("createUsuario: segunda lectura vacia usa el row ya creado (fallback)")
    void createUsuario_segundaLecturaVacia_usaCreado() {
        stubRolValido();
        when(gateway.createUsuario(anyString(), anyString(), anyString(), any(), anyBoolean()))
                .thenReturn(11);
        when(gateway.findUsuarioById(11)).thenReturn(Optional.of(U1), Optional.empty());
        when(auth.rolesOf(11)).thenReturn(List.of("VENDEDOR"));

        var r = service.createUsuario(new UsuarioCreateRequest(
                "nuevo01", "nuevo01@x.mx", "Secreta123", null, List.of("VENDEDOR")));

        assertThat(r.usuarioId()).isEqualTo(11);
        assertThat(r.roles()).containsExactly("VENDEDOR");
    }

    @Test
    @DisplayName("getUsuario existente: resuelve roles y resumen de empleado")
    void getUsuario_ok_conEmpleadoYRoles() {
        when(gateway.findUsuarioById(11)).thenReturn(Optional.of(U1));
        when(auth.rolesOf(11)).thenReturn(List.of("VENDEDOR"));
        when(empleados.resumenById(42)).thenReturn(Optional.of(EMPLEADO_ACTIVO));

        var r = service.getUsuario(11);

        assertThat(r.username()).isEqualTo("cajero1");
        assertThat(r.roles()).containsExactly("VENDEDOR");
        assertThat(r.empleado().nombreCompleto()).isEqualTo("Juan Pérez");
    }

    @Test
    @DisplayName("usuario inexistente en update/resetPassword/setRoles/delete -> 404")
    void usuarioInexistente_operaciones_404() {
        when(gateway.findUsuarioById(404)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateUsuario(404,
                new UsuarioUpdateRequest(null, null, null, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO));
        assertThatThrownBy(() -> service.resetPassword(404, new UsuarioPasswordRequest("NuevaClave99")))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO));
        assertThatThrownBy(() -> service.setRoles(404, new UsuarioRolesRequest(List.of())))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO));
        assertThatThrownBy(() -> service.deleteUsuario(404))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO));
        verify(gateway, never()).borrarUsuario(anyInt());
        verify(gateway, never()).actualizarPassword(anyInt(), anyString());
    }

    @Test
    @DisplayName("updateUsuario con empleado inconsistente/inexistente/inactivo -> 400 sin parchear")
    void updateUsuario_empleadoInvalido_rejected() {
        when(gateway.findUsuarioById(11)).thenReturn(Optional.of(U1));
        when(empleados.resumenById(42)).thenReturn(Optional.of(EMPLEADO_ACTIVO));

        assertThatThrownBy(() -> service.updateUsuario(11,
                new UsuarioUpdateRequest(null, "otro@x.mx", 42, null)))
                .isInstanceOfSatisfying(
                        mx.ferreteria.api.common.error.ValidacionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.VALOR_INVALIDO));

        when(empleados.resumenById(999)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateUsuario(11,
                new UsuarioUpdateRequest(null, null, 999, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.REFERENCIA_INVALIDA));

        var inactivo = new EmpleadoResumen(42, "Juan", "Vendedor", null, null, false, null);
        when(empleados.resumenById(42)).thenReturn(Optional.of(inactivo));
        assertThatThrownBy(() -> service.updateUsuario(11,
                new UsuarioUpdateRequest(null, null, 42, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.REFERENCIA_INVALIDA));

        verify(gateway, never()).updateUsuarioBasico(anyInt(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("updateUsuario con empleado valido: parchea y devuelve resumen")
    void updateUsuario_ok_conEmpleado() {
        when(gateway.findUsuarioById(11)).thenReturn(Optional.of(U1));
        when(empleados.resumenById(42)).thenReturn(Optional.of(EMPLEADO_ACTIVO));
        when(auth.rolesOf(11)).thenReturn(List.of("VENDEDOR"));

        var r = service.updateUsuario(11,
                new UsuarioUpdateRequest("cajero1", "cajero1@x.mx", 42, true));

        verify(gateway).updateUsuarioBasico(11, "cajero1", "cajero1@x.mx", 42, true);
        assertThat(r.empleado().nombreCompleto()).isEqualTo("Juan Pérez");
    }

    @Test
    @DisplayName("setRoles con rol inexistente -> 400 sin reemplazar; roles null limpia")
    void setRoles_invalidoYNull() {
        stubRolValido();
        when(gateway.findUsuarioById(11)).thenReturn(Optional.of(U1));
        when(auth.rolesOf(11)).thenReturn(List.of());

        assertThatThrownBy(() -> service.setRoles(11,
                new UsuarioRolesRequest(List.of("ROLE_FANTASMA"))))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.REFERENCIA_INVALIDA));
        verify(gateway, never()).reemplazarRoles(anyInt(), any());

        service.setRoles(11, new UsuarioRolesRequest(null));
        verify(gateway).reemplazarRoles(11, Set.of());
    }

    @Test
    @DisplayName("listUsuarios vacia: no consulta empleados y pagina en cero")
    void listUsuarios_vacia_sinBatchEmpleados() {
        when(gateway.findUsuarios(20, 0)).thenReturn(List.of());
        when(gateway.countUsuarios()).thenReturn(0L);

        var page = service.listUsuarios(PageRequest.of(0, 20));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
        verify(empleados, never()).resumenByIds(any());
    }

    @Test
    @DisplayName("listUsuarios: roles y empleado ausentes del batch -> vacio y null")
    void listUsuarios_batchAusente_defaults() {
        when(gateway.findUsuarios(20, 0)).thenReturn(List.of(U1));
        when(gateway.countUsuarios()).thenReturn(1L);
        when(auth.rolesOfBatch(Set.of(11))).thenReturn(Map.of());
        when(empleados.resumenByIds(Set.of(42))).thenReturn(Map.of());

        var page = service.listUsuarios(PageRequest.of(0, 20));

        assertThat(page.getContent().get(0).roles()).isEmpty();
        assertThat(page.getContent().get(0).empleado()).isNull();
    }

    @Test
    @DisplayName("listUsuarios segunda pagina: aplica offset del pageable")
    void listUsuarios_segundaPagina_offset() {
        when(gateway.findUsuarios(20, 20)).thenReturn(List.of());
        when(gateway.countUsuarios()).thenReturn(0L);

        var page = service.listUsuarios(PageRequest.of(1, 20));

        assertThat(page.getContent()).isEmpty();
        verify(gateway).findUsuarios(20, 20);
    }

    @Test
    @DisplayName("vinculo empleado: casos borde (sin email, null/null, case-insensitive)")
    void vinculoEmpleado_casosBorde() {
        stubRolValido();
        var sinEmail = new EmpleadoResumen(42, "Juan Pérez", "Vendedor", null, null, true, null);
        when(empleados.resumenById(42)).thenReturn(Optional.of(sinEmail));
        when(gateway.createUsuario(anyString(), any(), anyString(), any(), anyBoolean()))
                .thenReturn(11);
        when(gateway.findUsuarioById(11)).thenReturn(Optional.of(U1));
        when(auth.rolesOf(11)).thenReturn(List.of());

        service.createUsuario(new UsuarioCreateRequest("a", "a@x.mx", "Secreta123", 42, List.of()));
        verify(gateway).createUsuario(eq("a"), eq("a@x.mx"), anyString(), eq(42), eq(true));

        service.createUsuario(new UsuarioCreateRequest("b", null, "Secreta123", 42, List.of()));
        verify(gateway).createUsuario(eq("b"), isNull(), anyString(), eq(42), eq(true));

        when(empleados.resumenById(43)).thenReturn(Optional.of(
                new EmpleadoResumen(43, "Ana", "Cajera", "Ana@X.mx", null, true, null)));
        service.createUsuario(new UsuarioCreateRequest("c", "ana@x.mx", "Secreta123", 43, List.of()));
        verify(gateway).createUsuario(eq("c"), eq("ana@x.mx"), anyString(), eq(43), eq(true));
    }

    @Test
    @DisplayName("listRoles: pagina con permisos batch sin N+1; vacia retorna cero")
    void listRoles_paginaConPermisosBatch() {
        var r5 = new SegAdminGateway.RolRow(5, "SUPERVISOR", "Supervisor", null, true);
        var r6 = new SegAdminGateway.RolRow(6, "CAJERO", "Cajero", "desc", false);
        when(gateway.findRoles(20, 0)).thenReturn(List.of(r5, r6));
        when(gateway.permisosDeBatch(Set.of(5, 6)))
                .thenReturn(Map.of(5, List.of("V.VENDER"), 6, List.of()));
        when(gateway.countRoles()).thenReturn(2L);

        var page = service.listRoles(PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent().get(0).permisos()).containsExactly("V.VENDER");
        assertThat(page.getContent().get(1).permisos()).isEmpty();
        verify(gateway, never()).permisosDe(anyInt());

        when(gateway.findRoles(10, 0)).thenReturn(List.of());
        when(gateway.countRoles()).thenReturn(0L);
        assertThat(service.listRoles(PageRequest.of(0, 10)).getContent()).isEmpty();
    }

    @Test
    @DisplayName("getRol existente: resuelve permisos")
    void getRol_ok() {
        when(gateway.findRolById(5)).thenReturn(Optional.of(
                new SegAdminGateway.RolRow(5, "SUPERVISOR", "Supervisor", null, true)));
        when(gateway.permisosDe(5)).thenReturn(List.of("V.VENDER"));

        var r = service.getRol(5);

        assertThat(r.clave()).isEqualTo("SUPERVISOR");
        assertThat(r.permisos()).containsExactly("V.VENDER");
    }

    @Test
    @DisplayName("createRol con activo=false respeta el flag")
    void createRol_activoFalse() {
        when(gateway.createRol("CAJERO", "Cajero", "desc", false)).thenReturn(6);
        when(gateway.findRolById(6)).thenReturn(Optional.of(
                new SegAdminGateway.RolRow(6, "CAJERO", "Cajero", "desc", false)));
        when(gateway.permisosDe(6)).thenReturn(List.of());

        var r = service.createRol(new RolRequest("CAJERO", "Cajero", "desc", false));

        assertThat(r.activo()).isFalse();
        verify(gateway).createRol("CAJERO", "Cajero", "desc", false);
    }

    @Test
    @DisplayName("rol inexistente en update/delete -> 404 sin efectos")
    void rolInexistente_updateDelete_404() {
        when(gateway.findRolById(404)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateRol(404, new RolUpdateRequest("x", null, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO));
        assertThatThrownBy(() -> service.deleteRol(404))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO));
        verify(gateway, never()).desactivarRol(anyInt());
        verify(gateway, never()).updateRol(anyInt(), any(), any(), any());
    }

    @Test
    @DisplayName("getPermisosDe: lista permisos; rol inexistente -> 404")
    void getPermisosDe_okY404() {
        when(gateway.findRolById(5)).thenReturn(Optional.of(
                new SegAdminGateway.RolRow(5, "SUPERVISOR", "Supervisor", null, true)));
        when(gateway.permisosDe(5)).thenReturn(List.of("V.VENDER", "V.CANCELAR"));

        assertThat(service.getPermisosDe(5)).containsExactly("V.VENDER", "V.CANCELAR");

        when(gateway.findRolById(404)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getPermisosDe(404))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO));
    }

    @Test
    @DisplayName("setPermisos con rol inexistente -> 404 sin reemplazar")
    void setPermisos_rolInexistente_404() {
        when(gateway.findRolById(404)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setPermisos(404, new PermisosRequest(List.of("V.VENDER"))))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO));
        verify(gateway, never()).reemplazarPermisos(anyInt(), any());
    }

    @Test
    @DisplayName("setPermisos con null limpia; duplicados se colapsan")
    void setPermisos_nullYDedup() {
        when(gateway.findRolById(5)).thenReturn(Optional.of(
                new SegAdminGateway.RolRow(5, "SUPERVISOR", "Supervisor", null, true)));
        when(gateway.permisoClaves()).thenReturn(Set.of("V.VENDER"));
        when(gateway.permisosDe(5)).thenReturn(List.of());

        assertThat(service.setPermisos(5, new PermisosRequest(null))).isEmpty();
        verify(gateway).reemplazarPermisos(5, Set.of());

        when(gateway.permisosDe(5)).thenReturn(List.of("V.VENDER"));
        assertThat(service.setPermisos(5,
                new PermisosRequest(List.of("V.VENDER", "V.VENDER"))))
                .containsExactly("V.VENDER");
        verify(gateway).reemplazarPermisos(5, Set.of("V.VENDER"));
    }

    @Test
    @DisplayName("createPermiso: crea y devuelve; clave duplicada -> 409")
    void createPermiso_okYDuplicado() {
        when(gateway.findPermisoByClave("V.NUEVO")).thenReturn(Optional.empty());
        when(gateway.createPermiso("V.NUEVO", "Desc")).thenReturn(7);
        when(gateway.findPermisoById(7)).thenReturn(Optional.of(
                new SegAdminGateway.PermisoRow(7, "V.NUEVO", "Desc")));

        var r = service.createPermiso(new PermisoRequest("V.NUEVO", "Desc"));
        assertThat(r.permisoId()).isEqualTo(7);
        assertThat(r.clave()).isEqualTo("V.NUEVO");

        when(gateway.findPermisoByClave("V.VENDER")).thenReturn(Optional.of(
                new SegAdminGateway.PermisoRow(1, "V.VENDER", "Registrar ventas")));
        assertThatThrownBy(() -> service.createPermiso(new PermisoRequest("V.VENDER", "x")))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.REGISTRO_DUPLICADO));
        verify(gateway, never()).createPermiso(eq("V.VENDER"), anyString());
    }

    @Test
    @DisplayName("updatePermiso: misma clave del propio permiso no es duplicado")
    void updatePermiso_mismaClave_ok() {
        var p = new SegAdminGateway.PermisoRow(1, "V.VENDER", "Registrar ventas");
        when(gateway.findPermisoById(1)).thenReturn(Optional.of(p));
        when(gateway.findPermisoByClave("V.VENDER")).thenReturn(Optional.of(p));

        var r = service.updatePermiso(1, new PermisoRequest("V.VENDER", "Nueva desc"));

        verify(gateway).updatePermiso(1, "V.VENDER", "Nueva desc");
        assertThat(r.permisoId()).isEqualTo(1);
        assertThat(r.clave()).isEqualTo("V.VENDER");
    }

    @Test
    @DisplayName("updatePermiso inexistente -> 404; clave de otro -> 409")
    void updatePermiso_404YDuplicado() {
        when(gateway.findPermisoById(404)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updatePermiso(404, new PermisoRequest("V.X", "x")))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO));

        when(gateway.findPermisoById(1)).thenReturn(Optional.of(
                new SegAdminGateway.PermisoRow(1, "V.VENDER", "Registrar ventas")));
        when(gateway.findPermisoByClave("V.CANCELAR")).thenReturn(Optional.of(
                new SegAdminGateway.PermisoRow(2, "V.CANCELAR", "Cancelar ventas")));
        assertThatThrownBy(() -> service.updatePermiso(1, new PermisoRequest("V.CANCELAR", "x")))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.REGISTRO_DUPLICADO));
        verify(gateway, never()).updatePermiso(anyInt(), anyString(), anyString());
    }

    @Test
    @DisplayName("deletePermiso inexistente -> 404 sin borrar")
    void deletePermiso_inexistente_404() {
        when(gateway.findPermisoById(404)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deletePermiso(404))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO));
        verify(gateway, never()).deletePermiso(anyInt());
    }

    @Test
    @DisplayName("getPermiso inexistente -> 404 RECURSO_NO_ENCONTRADO")
    void getPermiso_inexistente_codigo() {
        when(gateway.findPermisoById(404)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPermiso(404))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO));
    }
}