package mx.ferreteria.api.rh.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import mx.ferreteria.api.rh.entity.Nomina;

public interface NominaRepository extends JpaRepository<Nomina, Long> {
    Page<Nomina> findAllByOrderByPeriodoFinDesc(Pageable pageable);

    Page<Nomina> findByEstadoOrderByPeriodoFinDesc(String estado, Pageable pageable);
}