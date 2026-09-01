package mx.ferreteria.api.cat.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;

import mx.ferreteria.api.common.error.ValidacionException;
import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;

@ExtendWith(MockitoExtension.class)
class CatalogoServiceTest {

    @Mock CatalogoRepository repo;

    CatalogoService service;

    @BeforeEach
    void setup() {
        service = new CatalogoService(new Catalogos(), repo);
    }

    private Catalogo estados() {
        return new Catalogos().porClave("estados");
    }

    private Catalogo puestos() {
        return new Catalogos().porClave("puestos");
    }

    @Test
    @DisplayName("registro desconocido lanza 404")
    void claveDesconocida() {
        assertThatThrownBy(() -> service.porClave("no-existe"))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("crear: campo requerido vacío lanza VALIDACION")
    void crear_campoRequerido() {
        assertThatThrownBy(() -> service.crear("estados", Map.of("clave_inegi", "01")))
                .isInstanceOf(ValidacionException.class);
        verify(repo, never()).insert(any(Catalogo.class), any());
    }

    @Test
    @DisplayName("crear: valor único duplicado lanza REGISTRO_DUPLICADO")
    void crear_duplicado() {
        when(repo.existeValor(any(Catalogo.class), any(), any(Campo.class))).thenReturn(true);
        assertThatThrownBy(() -> service.crear("estados",
                Map.of("clave_inegi", "05", "nombre", "Coahuila")))
                .isInstanceOf(ReglaNegocioException.class);
        verify(repo, never()).insert(any(Catalogo.class), any());
    }

    @Test
    @DisplayName("crear: valor fuera de lista cerrada lanza VALIDACION")
    void crear_listaCerrada() {
        // motivos_movimiento.tipo_default solo acepta ENTRADA/SALIDA
        when(repo.existeValor(any(Catalogo.class), any(), any(Campo.class))).thenReturn(false);
        assertThatThrownBy(() -> service.crear("motivos_movimiento",
                Map.of("clave", "X", "nombre", "Otro", "tipo_default", "BOGUS")))
                .isInstanceOf(ValidacionException.class);
    }

    @Test
    @DisplayName("crear: FK a catálogo inexistente lanza REFERENCIA_INVALIDA")
    void crear_fkInvalida() {
        when(repo.referenciaValida(anyString(), any(), anyString())).thenReturn(false);
        assertThatThrownBy(() -> service.crear("ciudades",
                Map.of("estado_id", 999, "nombre", "Puebla")))
                .isInstanceOf(ValidacionException.class);
        verify(repo, never()).insert(any(Catalogo.class), any());
    }

    @Test
    @DisplayName("crear: válido hace insert")
    void crear_ok() {
        when(repo.existeValor(any(Catalogo.class), any(), any(Campo.class))).thenReturn(false);
        service.crear("estados", Map.of("clave_inegi", "21", "nombre", "Puebla"));
        verify(repo).insert(any(Catalogo.class), any());
    }

    @Test
    @DisplayName("actualizar: registro inexistente lanza 404")
    void actualizar_noEncontrado() {
        when(repo.existeRegistro(any(Catalogo.class), any())).thenReturn(false);
        assertThatThrownBy(() -> service.actualizar("puestos", "1", Map.of("nombre", "X")))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("actualizar: válido hace update")
    void actualizar_ok() {
        when(repo.existeRegistro(any(Catalogo.class), any())).thenReturn(true);
        when(repo.existeValorExcepto(any(Catalogo.class), any(), any(Campo.class), any()))
                .thenReturn(false);
        service.actualizar("puestos", "1", Map.of("nombre", "Puesto A", "sueldo_base", 100));
        verify(repo).update(any(Catalogo.class), any(), any());
    }

    @Test
    @DisplayName("eliminar: tabla sin activo se bloquea (REGISTRO_NO_MODIFICABLE)")
    void eliminar_sinBajaLogica() {
        assertThatThrownBy(() -> service.eliminar("estados", "1"))
                .isInstanceOf(ReglaNegocioException.class);
        verify(repo, never()).desactivar(any(Catalogo.class), any());
    }

    @Test
    @DisplayName("eliminar: registro inexistente lanza 404")
    void eliminar_noEncontrado() {
        when(repo.existeRegistro(any(Catalogo.class), any())).thenReturn(false);
        assertThatThrownBy(() -> service.eliminar("puestos", "99"))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("eliminar: válido desactiva (baja lógica)")
    void eliminar_ok() {
        when(repo.existeRegistro(any(Catalogo.class), any())).thenReturn(true);
        service.eliminar("puestos", "1");
        verify(repo).desactivar(any(Catalogo.class), any());
    }

    @Test
    @DisplayName("datos: página numérica para tablas con baja lógica excluye borrados")
    void datos_paginado() {
        when(repo.count(any(Catalogo.class), any())).thenReturn(1L);
        when(repo.list(any(Catalogo.class), anyInt(), anyInt(), any(), any())).thenReturn(List.of(Map.of()));
        Page<Map<String, Object>> page = service.datos("estados", "", 0, 20, "");
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("opciones: campo no-FK lanza VALIDACION")
    void opciones_noFk() {
        assertThatThrownBy(() -> service.opciones("estados", "nombre", ""))
                .isInstanceOf(ValidacionException.class);
    }
}
