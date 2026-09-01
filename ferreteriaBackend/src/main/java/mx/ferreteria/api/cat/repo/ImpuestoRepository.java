package mx.ferreteria.api.cat.repo;

import java.util.List;
import java.util.Optional;

import mx.ferreteria.api.cat.entity.Impuesto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImpuestoRepository extends JpaRepository<Impuesto, Integer> {
    Optional<Impuesto> findByClaveSat(String claveSat);
    List<Impuesto> findByActivoTrueOrderByNombre();
    boolean existsByClaveSat(String claveSat);
}
