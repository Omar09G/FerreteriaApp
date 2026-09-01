package mx.ferreteria.api.cat.catalogo;

import java.util.List;

/**
 * Describe una columna de un catálogo para el CRUD genérico. Con esta metadata
 * el engine resuelve tipos, FK (dropdowns), campos obligatorios y la baja lógica.
 */
public record Campo(
        String nombre,
        Tipo tipo,
        boolean requerido,
        boolean unico,
        boolean esActivo,
        boolean clavesEditables,
        String etiqueta,
        // FK: tabla origen de la lista de opciones y columna(s) a mostrar.
        String opcionesTabla,
        List<String> opcionesColumnas,
        List<String> opcionesValores) {

    public enum Tipo {
        TEXT, NUMERO, DECIMAL, BOOLEAN, FECHA
    }

    public static Campo texto(String nombre, String etiqueta, boolean requerido) {
        return new Campo(nombre, Tipo.TEXT, requerido, false, false, true, etiqueta, null, null, null);
    }

    public static Campo textoUnico(String nombre, String etiqueta, boolean requerido) {
        return new Campo(nombre, Tipo.TEXT, requerido, true, false, true, etiqueta, null, null, null);
    }

    public static Campo numero(String nombre, String etiqueta, boolean requerido) {
        return new Campo(nombre, Tipo.NUMERO, requerido, false, false, true, etiqueta, null, null, null);
    }

    public static Campo decimal(String nombre, String etiqueta, boolean requerido) {
        return new Campo(nombre, Tipo.DECIMAL, requerido, false, false, true, etiqueta, null, null, null);
    }

    public static Campo booleano(String nombre, String etiqueta, boolean requerido) {
        return new Campo(nombre, Tipo.BOOLEAN, requerido, false, false, true, etiqueta, null, null, null);
    }

    public static Campo fecha(String nombre, String etiqueta, boolean requerido) {
        return new Campo(nombre, Tipo.FECHA, requerido, false, false, true, etiqueta, null, null, null);
    }

    public static Campo activo() {
        return new Campo("activo", Tipo.BOOLEAN, false, false, true, true, "Activo", null, null, null);
    }

    /** Campo FK: la columna guarda el id de opcionesTabla; el front muestra opcionesColumnas. */
    public static Campo fk(String nombre, String etiqueta, boolean requerido, String opcionesTabla,
                           List<String> opcionesColumnas, List<String> opcionesValores) {
        return new Campo(nombre, Tipo.NUMERO, requerido, false, false, false,
                etiqueta, opcionesTabla, opcionesColumnas, opcionesValores);
    }

    public boolean esPropiedadEditable() {
        return !esActivo && clavesEditables;
    }
}
