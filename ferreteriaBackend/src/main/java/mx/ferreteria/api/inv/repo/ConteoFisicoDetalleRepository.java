package mx.ferreteria.api.inv.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import mx.ferreteria.api.inv.entity.ConteoFisicoDetalle;
import mx.ferreteria.api.inv.entity.ConteoFisicoDetalleId;

public interface ConteoFisicoDetalleRepository extends JpaRepository<ConteoFisicoDetalle, ConteoFisicoDetalleId> {
    List<ConteoFisicoDetalle> findByConteoId(Long conteoId);
}
