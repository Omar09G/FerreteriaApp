package mx.ferreteria.api.inv.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import mx.ferreteria.api.inv.entity.TrasladoDetalle;
import mx.ferreteria.api.inv.entity.TrasladoDetalleId;

public interface TrasladoDetalleRepository extends JpaRepository<TrasladoDetalle, TrasladoDetalleId> {
    List<TrasladoDetalle> findByTrasladoId(Long trasladoId);
}
