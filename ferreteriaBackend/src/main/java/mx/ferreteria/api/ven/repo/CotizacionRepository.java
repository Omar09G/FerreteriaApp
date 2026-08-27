package mx.ferreteria.api.ven.repo;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import mx.ferreteria.api.ven.entity.Cotizacion;

public interface CotizacionRepository extends JpaRepository<Cotizacion, Long> {
    Optional<Cotizacion> findByFolio(String folio);
    Page<Cotizacion> findByEstadoOrderByFechaDesc(String estado, Pageable pageable);
    Page<Cotizacion> findByClienteIdAndEstadoOrderByFechaDesc(Long clienteId, String estado, Pageable pageable);
}
