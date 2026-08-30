package mx.ferreteria.api.ven.repo;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import mx.ferreteria.api.ven.entity.Cotizacion;

public interface CotizacionRepository extends JpaRepository<Cotizacion, Long> {
    Optional<Cotizacion> findByFolio(String folio);

    @Query("""
            SELECT c FROM Cotizacion c
            WHERE c.estado    = COALESCE(:estado, c.estado)
              AND c.fechaLocal >= COALESCE(:desde, c.fechaLocal)
              AND c.fechaLocal <= COALESCE(:hasta, c.fechaLocal)
            ORDER BY c.fecha DESC
            """)
    Page<Cotizacion> filtrar(String estado, LocalDate desde, LocalDate hasta, Pageable pageable);
}
