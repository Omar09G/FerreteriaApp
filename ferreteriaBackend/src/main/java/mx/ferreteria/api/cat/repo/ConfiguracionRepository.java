package mx.ferreteria.api.cat.repo;

import java.util.Optional;

import mx.ferreteria.api.cat.entity.Configuracion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfiguracionRepository extends JpaRepository<Configuracion, String> {
    Optional<Configuracion> findByClave(String clave);
}
