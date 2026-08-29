package mx.ferreteria.api.ven.repo;

import mx.ferreteria.api.ven.entity.Promocion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PromocionRepository extends JpaRepository<Promocion, Long>, JpaSpecificationExecutor<Promocion> {
}