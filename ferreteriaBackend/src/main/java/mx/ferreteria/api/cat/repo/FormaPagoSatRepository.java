package mx.ferreteria.api.cat.repo;

import java.util.List;

import mx.ferreteria.api.cat.entity.FormaPagoSat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FormaPagoSatRepository extends JpaRepository<FormaPagoSat, String> {
    List<FormaPagoSat> findByActivoTrueOrderByClave();
}
