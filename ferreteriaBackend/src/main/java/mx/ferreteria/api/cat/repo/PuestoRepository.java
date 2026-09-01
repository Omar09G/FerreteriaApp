package mx.ferreteria.api.cat.repo;

import java.util.List;
import java.util.Optional;

import mx.ferreteria.api.cat.entity.Puesto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PuestoRepository extends JpaRepository<Puesto, Integer> {
    Optional<Puesto> findByNombre(String nombre);
    List<Puesto> findByActivoTrueOrderByNombre();
    boolean existsByNombre(String nombre);
}
