package mx.ferreteria.api.cat.repo;

import java.util.List;

import mx.ferreteria.api.cat.entity.MetodoPagoSat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetodoPagoSatRepository extends JpaRepository<MetodoPagoSat, String> {
    List<MetodoPagoSat> findByActivoTrueOrderByClave();
}
