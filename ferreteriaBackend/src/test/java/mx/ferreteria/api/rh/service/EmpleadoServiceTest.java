package mx.ferreteria.api.rh.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageRequest;

import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.common.error.ValidacionException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.rh.dto.EmpleadoDtos.EmpleadoCreateRequest;
import mx.ferreteria.api.rh.dto.EmpleadoDtos.EmpleadoUpdateRequest;
import mx.ferreteria.api.rh.service.EmpleadoGateway.EmpleadoRow;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmpleadoServiceTest {

    @Mock
    EmpleadoGateway gateway;

    @Mock
    UsuarioAltaGateway usuarioAlta;

    EmpleadoService service;

    private static final EmpleadoRow ROW = new EmpleadoRow(
            1, 3, "Vendedor", "Juan", "Pérez", "López", "CURP123", "NSS123",
            "555", "juan@x.mx", "Av 1", "Colonia", 1, "97000",
            LocalDate.of(2026, 1, 15), null, new BigDecimal("100.00"), true);

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new EmpleadoService(gateway, usuarioAlta);
    }

    @Test
    @DisplayName("list: pagina de respuestas con puesto nombre enriquecido")
    void list_paginates() {
        when(gateway.findEmpleados(20, 0)).thenReturn(List.of(ROW));
        when(gateway.countEmpleados()).thenReturn(1L);

        var page = service.list(PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).puestoNombre()).isEqualTo("Vendedor");
        assertThat(page.getContent().get(0).nombre()).isEqualTo("Juan");
        verify(gateway).findEmpleados(20, 0);
    }

    @Test
    @DisplayName("create: fecha y sueldo por default y re-carga el registro creado")
    void create_defaultsAndRefetches() {
        when(gateway.create(eq(3), eq("Juan"), eq("Pérez"), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(gateway.findById(1)).thenReturn(Optional.of(ROW));

        var r = service.create(new EmpleadoCreateRequest(3, "Juan", "Pérez", null, null, null,
                "555", "juan@x.mx", null, null, null, null, null, null,
                null, null, null));

        assertThat(r.empleadoId()).isEqualTo(1);
        // fecha/sueldo default viven del lado del servicio (no en NULL explícito)
        verify(gateway).create(eq(3), eq("Juan"), eq("Pérez"), any(), any(), any(), eq("555"),
                eq("juan@x.mx"), any(), any(), any(), any(), eq(LocalDate.now()), eq(BigDecimal.ZERO));
        verify(usuarioAlta, never()).crearUsuarioConRoles(any(), any(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("create con username: en la MISMA funcion crea usuario con roles (email coherente)")
    void create_conUsuario_creaUsuarioYRoles() {
        when(gateway.create(anyInt(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any())).thenReturn(1);
        when(gateway.findById(1)).thenReturn(Optional.of(ROW));

        var r = service.create(new EmpleadoCreateRequest(3, "Juan", "Pérez", null, null, null,
                "555", "juan@x.mx", null, null, null, null, null, null,
                "juan.perez", "Secreta123", List.of("VENDEDOR")));

        verify(usuarioAlta).crearUsuarioConRoles("juan.perez", "juan@x.mx", "Secreta123", 1,
                List.of("VENDEDOR"));
        assertThat(r.empleadoId()).isEqualTo(1);
    }

    @Test
    @DisplayName("create con username sin password -> 400 CAMPO_REQUERIDO y no crea nada")
    void create_conUsuarioSinPassword_rejected() {
        assertThatThrownBy(() -> service.create(new EmpleadoCreateRequest(
                3, "Juan", "Pérez", null, null, null, "555", "juan@x.mx",
                null, null, null, null, null, null, "juan", null, List.of())))
                .isInstanceOfSatisfying(ValidacionException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CAMPO_REQUERIDO));
        verify(usuarioAlta, never()).crearUsuarioConRoles(any(), any(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("update: exige existente, delega parche y devuelve registro refrescado")
    void update_delegatesAndRefreshes() {
        when(gateway.findById(1)).thenReturn(Optional.of(ROW));

        var r = service.update(1, new EmpleadoUpdateRequest(null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, false));

        verify(gateway).update(eq(1), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), eq(false));
        assertThat(r.activo()).isTrue(); // row stub sin cambio; el update ya quedó verificado
    }

    @Test
    @DisplayName("create/get/update/baja de empleado inexistente -> 404 RECURSO_NO_ENCONTRADO")
    void missing_throws404() {
        when(gateway.findById(99)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(99))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.errorCode().name())
                                .isEqualTo("RECURSO_NO_ENCONTRADO"));
        assertThatThrownBy(() -> service.baja(99))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("baja: soft-delete via gateway.baja (nunca DELETE fisico)")
    void baja_delegates() {
        when(gateway.findById(1)).thenReturn(Optional.of(ROW));
        service.baja(1);
        verify(gateway).baja(1);
    }
}