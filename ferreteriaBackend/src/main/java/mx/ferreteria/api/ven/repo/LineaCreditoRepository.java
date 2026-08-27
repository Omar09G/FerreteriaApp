package mx.ferreteria.api.ven.repo;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import mx.ferreteria.api.ven.entity.LineaCredito;

public interface LineaCreditoRepository extends JpaRepository<LineaCredito, Long> {
    Optional<LineaCredito> findByClienteIdAndEstado(Long clienteId, String estado);
}
