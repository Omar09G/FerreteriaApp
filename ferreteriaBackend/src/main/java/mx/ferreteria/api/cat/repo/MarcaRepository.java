package mx.ferreteria.api.cat.repo;

import mx.ferreteria.api.cat.entity.Marca;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MarcaRepository extends JpaRepository<Marca, Integer> {
    Page<Marca> findByNombreContainingIgnoreCase(String nombre, Pageable pageable);

    Page<Marca> findByActivoTrue(Pageable pageable);

    Page<Marca> findByActivoTrueAndNombreContainingIgnoreCase(String nombre, Pageable pageable);
}
