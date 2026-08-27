package mx.ferreteria.api.inv.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import mx.ferreteria.api.inv.entity.ConteoFisico;

public interface ConteoFisicoRepository extends JpaRepository<ConteoFisico, Long> {
    Page<ConteoFisico> findByAlmacenId(Integer almacenId, Pageable pageable);
}
