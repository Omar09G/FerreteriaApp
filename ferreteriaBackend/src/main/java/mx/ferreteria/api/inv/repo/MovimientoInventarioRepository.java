package mx.ferreteria.api.inv.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import mx.ferreteria.api.inv.entity.MovimientoInventario;

public interface MovimientoInventarioRepository extends JpaRepository<MovimientoInventario, Long> {
    // Page<MovimientoInventario> findByProductoIdOrderByCreadoEnDesc(Long
    // productoId, Pageable pageable);
    @Query(value = "SELECT * FROM movimiento_inventario WHERE producto_id = :productoId ORDER BY creado_en DESC", nativeQuery = true)
    Page<MovimientoInventario> findByProductoIdOrderByCreadoEnDesc(@Param("productoId") Long productoId,
            Pageable pageable);

    // Page<MovimientoInventario> findByAlmacenIdOrderByCreadoEnDesc(Integer
    // almacenId, Pageable pageable);
    @Query(value = "SELECT * FROM movimiento_inventario WHERE almacen_id = :almacenId ORDER BY creado_en DESC", nativeQuery = true)
    Page<MovimientoInventario> findByAlmacenIdOrderByCreadoEnDesc(@Param("almacenId") Integer almacenId,
            Pageable pageable);

    // Page<MovimientoInventario>
    // findByProductoIdAndAlmacenIdOrderByCreadoEnDesc(Long productoId, Integer
    // almacenId, Pageable pageable);
    @Query(value = "SELECT * FROM movimiento_inventario WHERE producto_id = :productoId AND almacen_id = :almacenId ORDER BY creado_en DESC", nativeQuery = true)
    Page<MovimientoInventario> findByProductoIdAndAlmacenIdOrderByCreadoEnDesc(@Param("productoId") Long productoId,
            @Param("almacenId") Integer almacenId, Pageable pageable);
}
