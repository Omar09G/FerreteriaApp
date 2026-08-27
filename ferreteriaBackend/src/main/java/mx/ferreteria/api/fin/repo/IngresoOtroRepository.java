package mx.ferreteria.api.fin.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import mx.ferreteria.api.fin.entity.IngresoOtro;

public interface IngresoOtroRepository extends JpaRepository<IngresoOtro, Long> {
    Page<IngresoOtro> findAllByOrderByCreadoEnDesc(Pageable pageable);
    Page<IngresoOtro> findByTurnoCajaIdOrderByCreadoEnDesc(Long turnoCajaId, Pageable pageable);
}
