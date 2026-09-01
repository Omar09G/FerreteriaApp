package mx.ferreteria.api.cat.catalogo;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import mx.ferreteria.api.cat.catalogo.Campo.Tipo;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.common.error.ValidacionException;
import mx.ferreteria.api.common.i18n.ErrorCode;

/**
 * Lógica del CRUD genérico de catálogos. Valida campos requeridos, unicidad,
 * valores de catálogos cerrados (listasValidas) y referencias FK; luego delega
 * SQL exacto al repository. La baja es lógica (activo=false) cuando la tabla lo
 * soporta; en tablas sin `activo` el borrado se bloquea (REGISTRO_NO_MODIFICABLE).
 */
@Service
@RequiredArgsConstructor
public class CatalogoService {

    private final Catalogos catalogos;
    private final CatalogoRepository repo;

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

    public Page<Map<String, Object>> datos(String clave, String q, int page, int size, String sort) {
        Catalogo c = porClave(clave);
        long total = repo.count(c, q);
        List<Map<String, Object>> filas = repo.list(c, size, page * size, sort, q);
        return new PageImpl<>(filas, PageRequest.of(page, size), total);
    }

    public List<Map<String, Object>> opciones(String clave, String campo, String q) {
        Catalogo c = porClave(clave);
        Campo fkCampo = c.campos().stream().filter(x -> x.opcionesTabla() != null
                && x.nombre().equals(campo)).findFirst().orElse(null);
        if (fkCampo == null) {
            throw new ValidacionException(ErrorCode.VALOR_INVALIDO, campo);
        }
        Catalogo fkCat = Catalogos.TODOS.stream()
                .filter(x -> x.clave().equals(fkCampo.opcionesTabla())).findFirst().orElse(null);
        if (fkCat == null) {
            throw new ValidacionException(ErrorCode.REFERENCIA_INVALIDA, fkCampo.opcionesTabla());
        }
        String campoClave = fkCat.pk();
        return repo.opciones(fkCat, campoClave, fkCampo.opcionesColumnas(), q);
    }

    @Transactional
    public void crear(String clave, Map<String, Object> cuerpo) {
        Catalogo c = porClave(clave);
        Map<String, Object> valores = separarValores(c, cuerpo);
        validar(c, valores, null);
        repo.insert(c, valores);
    }

    @Transactional
    public void actualizar(String clave, String id, Map<String, Object> cuerpo) {
        Catalogo c = porClave(clave);
        Object pk = coercePk(c, id);
        if (!repo.existeRegistro(c, pk)) {
            throw new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO, id);
        }
        Map<String, Object> valores = separarValores(c, cuerpo);
        validar(c, valores, pk);
        repo.update(c, pk, valores);
    }

    @Transactional
    public void eliminar(String clave, String id) {
        Catalogo c = porClave(clave);
        if (!c.tieneBajaLogica()) {
            throw new ReglaNegocioException(ErrorCode.REGISTRO_NO_MODIFICABLE, c.nombre());
        }
        Object pk = coercePk(c, id);
        if (!repo.existeRegistro(c, pk)) {
            throw new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO, id);
        }
        repo.desactivar(c, pk);
    }

    /** Convierte el id de ruta al tipo correcto (numérico o string). */
    private Object coercePk(Catalogo c, String id) {
        boolean pkString = repo.pkCampoEnCampos(c) != null;
        if (pkString) {
            return id;
        }
        try {
            return Integer.valueOf(id);
        } catch (NumberFormatException e) {
            throw new ValidacionException(ErrorCode.VALOR_INVALIDO, c.pk(), id);
        }
    }

    /** Filtra el cuerpo dejando solo columnas declaradas y editables. */
    private Map<String, Object> separarValores(Catalogo c, Map<String, Object> cuerpo) {
        Map<String, Object> valores = new LinkedHashMap<>();
        for (Campo cnt : c.campos()) {
            if (!cnt.esPropiedadEditable()) {
                continue;
            }
            valores.put(cnt.nombre(), convertir(cnt, cuerpo.get(cnt.nombre())));
        }
        return valores;
    }

    private Object convertir(Campo cnt, Object v) {
        if (v == null) {
            return null;
        }
        return switch (cnt.tipo()) {
            case DECIMAL -> v instanceof BigDecimal ? v : new BigDecimal(String.valueOf(v));
            case NUMERO -> v instanceof Integer ? v : Integer.valueOf(String.valueOf(v));
            case BOOLEAN -> v instanceof Boolean b ? b : Boolean.valueOf(String.valueOf(v));
            case FECHA -> v;
            default -> String.valueOf(v);
        };
    }

    private void validar(Catalogo c, Map<String, Object> valores, Object pkOmitido) {
        for (Campo cnt : c.campos()) {
            if (!cnt.esPropiedadEditable()) {
                continue;
            }
            Object v = valores.get(cnt.nombre());
            if (cnt.requerido() && (v == null || "".equals(v))) {
                throw new ValidacionException(ErrorCode.CAMPO_REQUERIDO, cnt.etiqueta());
            }
            if (v == null) {
                continue;
            }
            if (cnt.unico()) {
                boolean duplicado = pkOmitido != null
                        ? repo.existeValorExcepto(c, v, cnt, pkOmitido)
                        : repo.existeValor(c, v, cnt);
                if (duplicado) {
                    throw new ReglaNegocioException(ErrorCode.REGISTRO_DUPLICADO, cnt.etiqueta());
                }
            }
            if (c.listasValidas() != null && c.listasValidas().containsKey(cnt.nombre())
                    && !c.listasValidas().get(cnt.nombre()).contains(String.valueOf(v))) {
                throw new ValidacionException(ErrorCode.VALOR_INVALIDO, cnt.etiqueta());
            }
            if (cnt.tipo() == Tipo.NUMERO && ((Number) v).intValue() < 0) {
                throw new ValidacionException(ErrorCode.VALOR_INVALIDO, cnt.etiqueta());
            }
            if (cnt.tipo() == Tipo.DECIMAL && ((BigDecimal) v).signum() < 0) {
                throw new ValidacionException(ErrorCode.VALOR_INVALIDO, cnt.etiqueta());
            }
            if (cnt.opcionesTabla() != null) {
                Catalogo fkCat = Catalogos.TODOS.stream()
                        .filter(x -> x.clave().equals(cnt.opcionesTabla())).findFirst().orElse(null);
                if (fkCat == null || !repo.referenciaValida(fkCat.tabla(), v, fkCat.pk())) {
                    throw new ValidacionException(ErrorCode.REFERENCIA_INVALIDA, cnt.etiqueta());
                }
            }
        }
    }
}
