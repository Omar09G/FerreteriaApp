package mx.ferreteria.api.fin.repo;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import mx.ferreteria.api.fin.entity.Caja;

public interface CajaRepository extends JpaRepository<Caja, Integer> {
    List<Caja> findByActivaTrue();
    Optional<Caja> findByNombre(String nombre);
}
