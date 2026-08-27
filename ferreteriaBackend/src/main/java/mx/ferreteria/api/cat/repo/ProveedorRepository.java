package mx.ferreteria.api.cat.repo;

import mx.ferreteria.api.cat.entity.Proveedor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProveedorRepository extends JpaRepository<Proveedor, Integer> {
    Page<Proveedor> findByRazonSocialContainingIgnoreCase(String razonSocial, Pageable pageable);

    Page<Proveedor> findByActivoTrue(Pageable pageable);

    Page<Proveedor> findByActivoTrueAndRazonSocialContainingIgnoreCase(String razonSocial, Pageable pageable);
}
