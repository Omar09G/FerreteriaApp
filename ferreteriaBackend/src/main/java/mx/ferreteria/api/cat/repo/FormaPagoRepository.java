package mx.ferreteria.api.cat.repo;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import mx.ferreteria.api.cat.entity.FormaPago;

public interface FormaPagoRepository extends JpaRepository<FormaPago, Integer> {
    Optional<FormaPago> findByNombre(String nombre);
}
