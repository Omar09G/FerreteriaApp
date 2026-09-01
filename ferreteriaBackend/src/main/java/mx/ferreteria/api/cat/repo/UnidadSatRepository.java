package mx.ferreteria.api.cat.repo;

import java.util.List;

import mx.ferreteria.api.cat.entity.UnidadSat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnidadSatRepository extends JpaRepository<UnidadSat, String> {
    List<UnidadSat> findByActivoTrueOrderByClave();
}
