package mx.ferreteria.api.ven.repo;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import mx.ferreteria.api.ven.entity.Renta;

public interface RentaRepository extends JpaRepository<Renta, Long> {
    Optional<Renta> findByFolio(String folio);

    @Query("""
            SELECT r FROM Renta r
            WHERE r.estado    = COALESCE(:estado, r.estado)
              AND r.fechaLocal >= COALESCE(:desde, r.fechaLocal)
              AND r.fechaLocal <= COALESCE(:hasta, r.fechaLocal)
            ORDER BY r.fechaRenta DESC
            """)
    Page<Renta> filtrar(String estado, LocalDate desde, LocalDate hasta, Pageable pageable);
}
