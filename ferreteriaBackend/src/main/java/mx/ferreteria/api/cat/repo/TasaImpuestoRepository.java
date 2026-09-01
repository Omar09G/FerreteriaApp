package mx.ferreteria.api.cat.repo;

import java.util.List;

import mx.ferreteria.api.cat.entity.TasaImpuesto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TasaImpuestoRepository extends JpaRepository<TasaImpuesto, Integer> {
    List<TasaImpuesto> findByActivoTrueOrderByTasa();
    List<TasaImpuesto> findByImpuestoImpuestoIdAndActivoTrue(Integer impuestoId);
}
