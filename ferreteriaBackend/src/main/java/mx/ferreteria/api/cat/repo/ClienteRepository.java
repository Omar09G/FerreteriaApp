package mx.ferreteria.api.cat.repo;

import mx.ferreteria.api.cat.entity.Cliente;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    Page<Cliente> findByRazonSocialContainingIgnoreCase(String razonSocial, Pageable pageable);

    Page<Cliente> findByActivoTrue(Pageable pageable);

    Page<Cliente> findByActivoTrueAndRazonSocialContainingIgnoreCase(String razonSocial, Pageable pageable);
}
