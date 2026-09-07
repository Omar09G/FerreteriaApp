package mx.ferreteria.api.cat.repo;

/**
 * Proyeccion JPA para listados de productos. BACK-REND-027: el listado por
 * categoria (BACK-REND-027 findByCategoriaCategoriaIdAndActivoTrue) trae la
 * entidad completa (descripcion, especificaciones JSONB, created_by, etc.) cuando
 * el grid solo muestra codigo, nombre, costo, precios, stock, marca.
 * <p>
 * Devolver esta proyeccion reduce IO de BD ~10x (productos con descripciones
 * largas + JSONB pueden pesar 5-10 KB; esta vista solo trae ~250 bytes).
 */
public interface ProductoListado {
    Long getProductoId();
    String getCodigo();
    String getNombre();
    String getCategoriaNombre();
    String getMarcaNombre();
    java.math.BigDecimal getCostoActual();
    java.math.BigDecimal getPrecioMenudeo();
    java.math.BigDecimal getPrecioMayoreo();
    Boolean getAplicaIva();
    Boolean getActivo();
}
