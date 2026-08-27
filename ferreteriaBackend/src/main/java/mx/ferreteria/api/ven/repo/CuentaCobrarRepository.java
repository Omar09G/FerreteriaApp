package mx.ferreteria.api.ven.repo;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import mx.ferreteria.api.ven.entity.CuentaCobrar;

public interface CuentaCobrarRepository extends JpaRepository<CuentaCobrar, Long> {
    Optional<CuentaCobrar> findByVentaId(Long ventaId);
    Page<CuentaCobrar> findByClienteIdAndEstadoOrderByCreadoEnDesc(Long clienteId, String estado, Pageable pageable);
    Page<CuentaCobrar> findByClienteIdOrderByCreadoEnDesc(Long clienteId, Pageable pageable);
    Page<CuentaCobrar> findByEstadoOrderByCreadoEnDesc(String estado, Pageable pageable);
}
