package mx.ferreteria.api.ven.repo;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import mx.ferreteria.api.ven.entity.VentaDetalle;

public interface VentaDetalleRepository extends JpaRepository<VentaDetalle, Long> {
    List<VentaDetalle> findByVentaId(Long ventaId);
}
