package mx.ferreteria.api.ven.repo;

import java.util.List;

import mx.ferreteria.api.ven.entity.PromocionCategoria;
import mx.ferreteria.api.ven.entity.PromocionCategoriaId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PromocionCategoriaRepository extends JpaRepository<PromocionCategoria, PromocionCategoriaId> {

    @Query("SELECT pc FROM PromocionCategoria pc WHERE pc.promocionId = :promocionId")
    List<PromocionCategoria> findByPromocionId(Long promocionId);

    @Modifying
    @Query("DELETE FROM PromocionCategoria pc WHERE pc.promocionId = :promocionId")
    void deleteByPromocionId(Long promocionId);
}