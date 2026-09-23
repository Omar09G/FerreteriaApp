package mx.ferreteria.api.cat.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Puerto de consulta de stock (lado consumidor). Vive en {@code cat} a
 * propósito: si {@code cat} importara el repositorio de {@code inv}
 * directamente se forma el ciclo cat↔inv que prohíbe
 * {@code modulosSinCiclos}. El adapter en {@code inv} implementa este
 * contrato (la arista inv→cat ya existe y es unidireccional).
 * <p>
 * Stock ausente o nulo se reporta como {@code BigDecimal.ZERO}.
 */
public interface StockPort {

    /**
     * Stock por producto en un almacén (una sola consulta).
     *
     * @return mapa productoId → stock (vacío si almacenId null o sin filas)
     */
    Map<Long, BigDecimal> stockPorProductos(Integer almacenId, List<Long> productoIds);

    /**
     * Stock de un producto en un almacén.
     *
     * @return stock o {@code BigDecimal.ZERO} si no hay registro
     */
    BigDecimal stockDeProducto(Integer almacenId, Long productoId);
}
