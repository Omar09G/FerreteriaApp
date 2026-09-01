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
        String etiqueta,
        // FK: clave del catálogo de origen (en Catalogos) y columna(s) a mostrar.
        String opcionesTabla,
        List<String> opcionesColumnas) {

    public enum Tipo {
        TEXT, NUMERO, DECIMAL, BOOLEAN, FECHA
    }

    /** Indica si el valor que guarda es una FK (drop-down) en vez de texto libre. */
    public boolean esFk() {
        return opcionesTabla != null;
    }

    public static Campo texto(String nombre, String etiqueta, boolean requerido) {
        return new Campo(nombre, Tipo.TEXT, requerido, false, false, etiqueta, null, null);
    }

    public static Campo textoUnico(String nombre, String etiqueta, boolean requerido) {
        return new Campo(nombre, Tipo.TEXT, requerido, true, false, etiqueta, null, null);
    }

    public static Campo numero(String nombre, String etiqueta, boolean requerido) {
        return new Campo(nombre, Tipo.NUMERO, requerido, false, false, etiqueta, null, null);
    }

    public static Campo decimal(String nombre, String etiqueta, boolean requerido) {
        return new Campo(nombre, Tipo.DECIMAL, requerido, false, false, etiqueta, null, null);
    }

    public static Campo booleano(String nombre, String etiqueta, boolean requerido) {
        return new Campo(nombre, Tipo.BOOLEAN, requerido, false, false, etiqueta, null, null);
    }

    public static Campo fecha(String nombre, String etiqueta, boolean requerido) {
        return new Campo(nombre, Tipo.FECHA, requerido, false, false, etiqueta, null, null);
    }

    public static Campo activo() {
        return new Campo("activo", Tipo.BOOLEAN, false, false, true, "Activo", null, null);
    }

    /** Campo FK numérico: guarda el id de la fila en el catálogo de origen. */
    public static Campo fk(String nombre, String etiqueta, boolean requerido, String opcionesTabla,
                           List<String> opcionesColumnas) {
        return new Campo(nombre, Tipo.NUMERO, requerido, false, false, etiqueta, opcionesTabla, opcionesColumnas);
    }

    /** Campo FK con clave de texto como valor (p. ej. formas_pago.forma_pago_sat). */
    public static Campo fkTexto(String nombre, String etiqueta, boolean requerido, String opcionesTabla,
                                List<String> opcionesColumnas) {
        return new Campo(nombre, Tipo.TEXT, requerido, false, false, etiqueta, opcionesTabla, opcionesColumnas);
    }

    /** Campo persistible en formularios: todo excepto el marcador de baja lógica. */
    public boolean esPropiedadEditable() {
        return !esActivo;
    }
}
