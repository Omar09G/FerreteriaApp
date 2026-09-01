package mx.ferreteria.api.cat.repo;

import java.util.List;

import mx.ferreteria.api.cat.entity.Ciudad;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CiudadRepository extends JpaRepository<Ciudad, Integer> {
    List<Ciudad> findByEstadoEstadoIdOrderByNombre(Integer estadoId);
    boolean existsByEstadoEstadoIdAndNombre(Integer estadoId, String nombre);
}
