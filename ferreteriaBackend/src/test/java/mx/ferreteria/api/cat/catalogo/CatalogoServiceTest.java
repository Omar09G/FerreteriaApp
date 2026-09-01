package mx.ferreteria.api.cat.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import mx.ferreteria.api.cat.repo.EstadoRepository;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.error.ValidacionException;

@ExtendWith(MockitoExtension.class)
class CatalogoServiceTest {

    @Mock EstadoRepository estadoRepo;
    @Mock mx.ferreteria.api.cat.repo.ImpuestoRepository impuestoRepo;
    @Mock mx.ferreteria.api.cat.repo.FormaPagoSatRepository formaPagoSatRepo;

    CatalogoService service;
    OpcionesCatalogoService opcionesService;

    @BeforeEach
    void setup() {
        opcionesService = new OpcionesCatalogoService(estadoRepo, impuestoRepo, formaPagoSatRepo);
        service = new CatalogoService(new Catalogos(), opcionesService);
    }

    @Test
    @DisplayName("porClave: catálogo desconocido lanza 404")
    void claveDesconocida() {
        assertThatThrownBy(() -> service.porClave("no-existe"))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("porClave: devuelve el descriptor registrado")
    void porClaveOk() {
        Catalogo c = service.porClave("estados");
        assertThat(c.tabla()).isEqualTo("cat.estados");
    }

    @Test
    @DisplayName("paneles: expone todos los catálogos registrados")
    void paneles() {
        assertThat(service.paneles()).isNotEmpty();
    }

    @Test
    @DisplayName("opciones: campo no-FK lanza VALIDACION")
    void opciones_noFk() {
        assertThatThrownBy(() -> service.opciones("estados", "nombre"))
                .isInstanceOf(ValidacionException.class);
    }

    @Test
    @DisplayName("opciones: resuelve dropdown de estados (FK de ciudades)")
    void opcionesEstados() {
        when(estadoRepo.findAllByOrderByNombre()).thenReturn(List.of(
                new mx.ferreteria.api.cat.entity.Estado(1, "01", "Aguascalientes")));
        var opciones = service.opciones("ciudades", "estado_id");
        assertThat(opciones).hasSize(1);
        assertThat(opciones.get(0).clave()).isEqualTo(1);
        assertThat(opciones.get(0).texto()).containsExactly("Aguascalientes");
    }
}
