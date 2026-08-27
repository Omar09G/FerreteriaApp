package mx.ferreteria.api.inv.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import mx.ferreteria.api.inv.entity.Almacen;

public interface AlmacenRepository extends JpaRepository<Almacen, Integer> {
    Page<Almacen> findByActivoTrue(Pageable pageable);
    Page<Almacen> findByNombreContainingIgnoreCase(String nombre, Pageable pageable);
}
