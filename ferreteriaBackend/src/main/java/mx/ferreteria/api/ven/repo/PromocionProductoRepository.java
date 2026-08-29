package mx.ferreteria.api.ven.repo;

import java.util.List;

import mx.ferreteria.api.ven.entity.PromocionProducto;
import mx.ferreteria.api.ven.entity.PromocionProductoId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PromocionProductoRepository extends JpaRepository<PromocionProducto, PromocionProductoId> {

    @Query("SELECT pp FROM PromocionProducto pp WHERE pp.promocionId = :promocionId")
    List<PromocionProducto> findByPromocionId(Long promocionId);

    @Modifying
    @Query("DELETE FROM PromocionProducto pp WHERE pp.promocionId = :promocionId")
    void deleteByPromocionId(Long promocionId);
}