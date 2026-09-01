package mx.ferreteria.api.cat.catalogo;

import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.error.ValidacionException;
import mx.ferreteria.api.common.i18n.ErrorCode;

/**
 * Metadata del CRUD de catálogos (PLAN catalogo). Este servicio expone los
 * descriptores (paneles) que el front usa para renderizar cada tabla y resuelve
 * las opciones de los campos FK con repositorios JPA. El CRUD por tabla se hace
 * en los endpoints individuales (véase service/ y api/), no aquí.
 */
@Service
@RequiredArgsConstructor
public class CatalogoService {

    private final Catalogos catalogos;
    private final OpcionesCatalogoService opcionesService;

    public List<Catalogo> paneles() {
        return Catalogos.TODOS;
    }

    public Catalogo porClave(String clave) {
        Catalogo c = catalogos.porClave(clave);
        if (c == null) {
            throw new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO, clave);
        }
        return c;
    }

    public List<OpcionesCatalogoService.OpcionFk> opciones(String clave, String campo) {
        Catalogo c = porClave(clave);
        Campo fkCampo = c.campos().stream().filter(x -> x.opcionesTabla() != null
                && x.nombre().equals(campo)).findFirst().orElse(null);
        if (fkCampo == null) {
            throw new ValidacionException(ErrorCode.VALOR_INVALIDO, campo);
        }
        return opcionesService.opciones(fkCampo.opcionesTabla(), fkCampo.opcionesColumnas());
    }
}
