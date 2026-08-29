package mx.ferreteria.api.inv.repo;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import mx.ferreteria.api.inv.entity.MovimientoInventario;

public interface MovimientoInventarioRepository extends JpaRepository<MovimientoInventario, Long> {

    /**
     * Las queries nativas usan el esquema `inv.` y el nombre PLURAL de la tabla
     * (`movimientos_inventario`, no `movimiento_inventario`) según
     * `ferreteriaDB/scripts/02_tablas.sql` línea 377. La entity está bien mapeada;
     * el problema sólo afecta a las native queries.
     */

    @Query(value = "SELECT * FROM inv.movimientos_inventario "
            + "WHERE creado_en::date BETWEEN :inicio AND :fin ORDER BY creado_en DESC", nativeQuery = true)
    Page<MovimientoInventario> findAllEnRango(@Param("inicio") LocalDate inicio,
            @Param("fin") LocalDate fin, Pageable pageable);

    @Query(value = "SELECT * FROM inv.movimientos_inventario "
            + "WHERE producto_id = :productoId "
            + "AND creado_en::date BETWEEN :inicio AND :fin ORDER BY creado_en DESC", nativeQuery = true)
    Page<MovimientoInventario> findByProductoEnRango(@Param("productoId") Long productoId,
            @Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin, Pageable pageable);

    @Query(value = "SELECT * FROM inv.movimientos_inventario "
            + "WHERE almacen_id = :almacenId "
            + "AND creado_en::date BETWEEN :inicio AND :fin ORDER BY creado_en DESC", nativeQuery = true)
    Page<MovimientoInventario> findByAlmacenEnRango(@Param("almacenId") Integer almacenId,
            @Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin, Pageable pageable);

    @Query(value = "SELECT * FROM inv.movimientos_inventario WHERE producto_id = :productoId ORDER BY creado_en DESC", nativeQuery = true)
    Page<MovimientoInventario> findByProductoIdOrderByCreadoEnDesc(@Param("productoId") Long productoId,
            Pageable pageable);

    @Query(value = "SELECT * FROM inv.movimientos_inventario WHERE almacen_id = :almacenId ORDER BY creado_en DESC", nativeQuery = true)
    Page<MovimientoInventario> findByAlmacenIdOrderByCreadoEnDesc(@Param("almacenId") Integer almacenId,
            Pageable pageable);

    @Query(value = "SELECT * FROM inv.movimientos_inventario WHERE producto_id = :productoId AND almacen_id = :almacenId ORDER BY creado_en DESC", nativeQuery = true)
    Page<MovimientoInventario> findByProductoIdAndAlmacenIdOrderByCreadoEnDesc(@Param("productoId") Long productoId,
            @Param("almacenId") Integer almacenId, Pageable pageable);
}