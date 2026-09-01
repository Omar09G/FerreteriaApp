package mx.ferreteria.api.cat.repo;

import java.util.List;
import java.util.Optional;

import mx.ferreteria.api.cat.entity.FormaPago;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FormaPagoRepository extends JpaRepository<FormaPago, Integer> {
    Optional<FormaPago> findByClave(String clave);
    List<FormaPago> findByActivoTrueOrderByNombre();
    boolean existsByClave(String clave);
}
