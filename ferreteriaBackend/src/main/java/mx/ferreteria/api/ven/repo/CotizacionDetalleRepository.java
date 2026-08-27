package mx.ferreteria.api.ven.repo;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import mx.ferreteria.api.ven.entity.CotizacionDetalle;
import mx.ferreteria.api.ven.entity.CotizacionDetalleId;

public interface CotizacionDetalleRepository extends JpaRepository<CotizacionDetalle, CotizacionDetalleId> {
    List<CotizacionDetalle> findByCotizacionId(Long cotizacionId);
}
