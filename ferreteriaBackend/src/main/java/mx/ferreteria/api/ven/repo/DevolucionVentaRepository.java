package mx.ferreteria.api.ven.repo;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import mx.ferreteria.api.ven.entity.DevolucionVenta;

public interface DevolucionVentaRepository extends JpaRepository<DevolucionVenta, Long> {
    Optional<DevolucionVenta> findByFolio(String folio);
    Page<DevolucionVenta> findByVentaIdOrderByFechaDesc(Long ventaId, Pageable pageable);
}
