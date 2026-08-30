package mx.ferreteria.api.fin.repo;

import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import mx.ferreteria.api.fin.entity.Gasto;

public interface GastoRepository extends JpaRepository<Gasto, Long> {
    Page<Gasto> findAllByOrderByCreadoEnDesc(Pageable pageable);
    Page<Gasto> findByTurnoCajaIdOrderByCreadoEnDesc(Long turnoCajaId, Pageable pageable);
    Page<Gasto> findByFechaGastoBetweenOrderByCreadoEnDesc(LocalDate desde, LocalDate hasta, Pageable pageable);
}
