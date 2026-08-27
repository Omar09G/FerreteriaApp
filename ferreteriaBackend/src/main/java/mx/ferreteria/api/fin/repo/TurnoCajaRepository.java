package mx.ferreteria.api.fin.repo;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import mx.ferreteria.api.fin.entity.TurnoCaja;

public interface TurnoCajaRepository extends JpaRepository<TurnoCaja, Long> {
    Optional<TurnoCaja> findByCajaIdAndEstado(Integer cajaId, String estado);
    Page<TurnoCaja> findByCajaIdOrderByAperturaEnDesc(Integer cajaId, Pageable pageable);
}
