package mx.ferreteria.api.cat.repo;

import java.util.List;
import java.util.Optional;

import mx.ferreteria.api.cat.entity.MotivoMovimiento;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MotivoMovimientoRepository extends JpaRepository<MotivoMovimiento, Integer> {
    Optional<MotivoMovimiento> findByClave(String clave);
    List<MotivoMovimiento> findByActivoTrueOrderByNombre();
    boolean existsByClave(String clave);
}
