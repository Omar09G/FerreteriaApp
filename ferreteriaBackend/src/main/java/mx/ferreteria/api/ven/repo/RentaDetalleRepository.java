package mx.ferreteria.api.ven.repo;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import mx.ferreteria.api.ven.entity.RentaDetalle;
import mx.ferreteria.api.ven.entity.RentaDetalleId;

public interface RentaDetalleRepository extends JpaRepository<RentaDetalle, RentaDetalleId> {
    List<RentaDetalle> findByRentaId(Long rentaId);
}
