package mx.ferreteria.api.cat.catalogo;

import java.util.List;
import java.util.Map;

/**
 * Descriptor declarativo de un catálogo. Cada entrada define la tabla, su PK,
 * las columnas (tipos/FK/requeridos) y reglas de validación. Toda la lógica
 * CRUD vive en el engine; por catálogo solo se declara metadata.
 */
public record Catalogo(
        String clave,
        String tabla,
        String nombre,
        String pk,
        List<Campo> campos,
        Map<String, List<String>> listasValidas,
        boolean soportaBajaLogica) {

    public Campo campoActivo() {
        return campos.stream().filter(Campo::esActivo).findFirst().orElse(null);
    }

    public boolean tieneBajaLogica() {
        return campoActivo() != null;
    }
}
