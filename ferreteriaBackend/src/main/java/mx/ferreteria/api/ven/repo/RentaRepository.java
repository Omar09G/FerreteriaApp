package mx.ferreteria.api.ven.repo;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import mx.ferreteria.api.ven.entity.Renta;

public interface RentaRepository extends JpaRepository<Renta, Long> {
    Optional<Renta> findByFolio(String folio);
    Page<Renta> findByEstadoOrderByFechaRentaDesc(String estado, Pageable pageable);
    Page<Renta> findByClienteIdAndEstadoOrderByFechaRentaDesc(Long clienteId, String estado, Pageable pageable);
}
