package mx.ferreteria.api.rh.repo;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Query(value = "SELECT empleado_id FROM rh.nominas WHERE periodo_ini = :ini AND periodo_fin = :fin AND empleado_id IN (:ids)", nativeQuery = true)
    List<Integer> findEmpleadoIdsByPeriodo(@Param("ini") LocalDate ini, @Param("fin") LocalDate fin, @Param("ids") Collection<Integer> ids);
}