package mx.ferreteria.api.cat.repo;

import java.util.List;
import java.util.Optional;

import mx.ferreteria.api.cat.entity.TipoGasto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TipoGastoRepository extends JpaRepository<TipoGasto, Integer> {
    Optional<TipoGasto> findByClave(String clave);
    List<TipoGasto> findByActivoTrueOrderByNombre();
    boolean existsByClave(String clave);
}
