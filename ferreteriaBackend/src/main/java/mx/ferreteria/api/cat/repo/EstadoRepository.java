package mx.ferreteria.api.cat.repo;

import java.util.List;
import java.util.Optional;

import mx.ferreteria.api.cat.entity.Estado;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EstadoRepository extends JpaRepository<Estado, Integer> {
    Optional<Estado> findByClaveInegi(String claveInegi);
    boolean existsByClaveInegi(String claveInegi);
    boolean existsByNombre(String nombre);
    List<Estado> findAllByOrderByNombre();
}
