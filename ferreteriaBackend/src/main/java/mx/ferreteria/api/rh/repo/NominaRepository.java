package mx.ferreteria.api.rh.repo;

import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import mx.ferreteria.api.rh.entity.Nomina;

public interface NominaRepository extends JpaRepository<Nomina, Long> {

    /**
     * Nóminas cuyo periodo [periodo_ini, periodo_fin] intersecta [desde, hasta]
     * (todas las nóminas del periodo seleccionado). Estado opcional.
     */
    @Query("""
            SELECT n FROM Nomina n
            WHERE n.estado     = COALESCE(:estado, n.estado)
              AND n.periodoFin >= COALESCE(:desde, n.periodoFin)
              AND n.periodoIni <= COALESCE(:hasta, n.periodoIni)
            ORDER BY n.periodoFin DESC
            """)
    Page<Nomina> filtrar(String estado, LocalDate desde, LocalDate hasta, Pageable pageable);
}