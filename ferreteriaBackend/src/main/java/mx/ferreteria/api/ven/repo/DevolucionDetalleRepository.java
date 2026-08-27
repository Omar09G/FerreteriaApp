package mx.ferreteria.api.ven.repo;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import mx.ferreteria.api.ven.entity.DevolucionDetalle;
import mx.ferreteria.api.ven.entity.DevolucionDetalleId;

public interface DevolucionDetalleRepository extends JpaRepository<DevolucionDetalle, DevolucionDetalleId> {
    List<DevolucionDetalle> findByDevolucionId(Long devolucionId);
}
