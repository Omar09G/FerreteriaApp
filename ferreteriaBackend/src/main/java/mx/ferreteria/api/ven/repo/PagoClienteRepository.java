package mx.ferreteria.api.ven.repo;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import mx.ferreteria.api.ven.entity.PagoCliente;

public interface PagoClienteRepository extends JpaRepository<PagoCliente, Long> {
    List<PagoCliente> findByCuentaCobrarIdOrderByFechaDesc(Long cuentaCobrarId);
}
