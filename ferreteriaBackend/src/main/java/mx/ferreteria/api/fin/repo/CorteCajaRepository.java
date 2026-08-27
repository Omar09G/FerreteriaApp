package mx.ferreteria.api.fin.repo;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import mx.ferreteria.api.fin.entity.CorteCaja;

public interface CorteCajaRepository extends JpaRepository<CorteCaja, Long> {
    Optional<CorteCaja> findByTurnoCajaId(Long turnoCajaId);
    Page<CorteCaja> findAllByOrderByFechaDescCorteIdDesc(Pageable pageable);
}
