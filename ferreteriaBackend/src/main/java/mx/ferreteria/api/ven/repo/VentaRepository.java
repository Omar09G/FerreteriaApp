package mx.ferreteria.api.ven.repo;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import mx.ferreteria.api.ven.entity.Venta;

public interface VentaRepository extends JpaRepository<Venta, Long> {
    Optional<Venta> findByFolio(String folio);
    Page<Venta> findByFechaBetweenOrderByFechaDesc(Instant desde, Instant hasta, Pageable pageable);
    Page<Venta> findByAlmacenIdAndFechaBetweenOrderByFechaDesc(Integer almacenId, Instant desde, Instant hasta, Pageable pageable);
    Page<Venta> findByClienteIdOrderByFechaDesc(Long clienteId, Pageable pageable);
    Page<Venta> findByEstadoOrderByFechaDesc(String estado, Pageable pageable);
    Page<Venta> findByTurnoCajaIdOrderByFechaDesc(Long turnoCajaId, Pageable pageable);
}
