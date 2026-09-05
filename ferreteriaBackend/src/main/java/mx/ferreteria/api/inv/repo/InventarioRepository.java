package mx.ferreteria.api.inv.repo;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import mx.ferreteria.api.inv.entity.Inventario;
import mx.ferreteria.api.inv.entity.InventarioId;

public interface InventarioRepository extends JpaRepository<Inventario, InventarioId> {
    Page<Inventario> findByAlmacenId(Integer almacenId, Pageable pageable);

    Inventario findByAlmacenIdAndProductoId(Integer almacenId, Long productoId);

    // Page<Inventario> findByAlmacenIdAndStockLessThanStockMinimo(Integer
    // almacenId, Pageable pageable);
    @Query("SELECT i FROM Inventario i WHERE i.almacenId = :almacenId AND i.stock < i.stockMinimo")
    Page<Inventario> findStockBajo(@Param("almacenId") Integer almacenId, Pageable pageable);

    List<Inventario> findByProductoId(Long productoId);

    @Query("SELECT i FROM Inventario i WHERE i.stock <= i.stockMinimo")
    Page<Inventario> findBajoStock(Pageable pageable);
}
