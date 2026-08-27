package mx.ferreteria.api.cat.repo;

import mx.ferreteria.api.cat.entity.UnidadMedida;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UnidadMedidaRepository extends JpaRepository<UnidadMedida, Integer> {
    Page<UnidadMedida> findByNombreContainingIgnoreCase(String nombre, Pageable pageable);

    Page<UnidadMedida> findByActivoTrue(Pageable pageable);

    Page<UnidadMedida> findByActivoTrueAndNombreContainingIgnoreCase(String nombre, Pageable pageable);
}
