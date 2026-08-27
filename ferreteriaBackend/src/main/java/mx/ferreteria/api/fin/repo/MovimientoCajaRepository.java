package mx.ferreteria.api.fin.repo;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import mx.ferreteria.api.fin.entity.MovimientoCaja;

public interface MovimientoCajaRepository extends JpaRepository<MovimientoCaja, Long> {
    List<MovimientoCaja> findByTurnoCajaIdOrderByCreadoEnAsc(Long turnoCajaId);
}
