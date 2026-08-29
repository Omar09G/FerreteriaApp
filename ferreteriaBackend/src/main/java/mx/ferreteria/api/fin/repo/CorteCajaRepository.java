package mx.ferreteria.api.fin.repo;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import mx.ferreteria.api.fin.entity.CorteCaja;

public interface CorteCajaRepository extends JpaRepository<CorteCaja, Long> {
    Optional<CorteCaja> findByTurnoCajaId(Long turnoCajaId);

    @Query("""
            SELECT c FROM CorteCaja c
            WHERE (:desde IS NULL OR c.fecha >= :desde)
              AND (:hasta IS NULL OR c.fecha <= :hasta)
            ORDER BY c.fecha DESC, c.corteId DESC
            """)
    Page<CorteCaja> findAllByRangoFecha(LocalDate desde, LocalDate hasta, Pageable pageable);
}
