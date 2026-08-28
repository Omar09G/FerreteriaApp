package mx.ferreteria.api.fis.repo;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import mx.ferreteria.api.fis.entity.FacturaFis;

public interface FacturaFisRepository extends JpaRepository<FacturaFis, Long> {
    Page<FacturaFis> findAllByOrderByFechaTimbradoDesc(Pageable pageable);

    Page<FacturaFis> findByTipoOrderByFechaTimbradoDesc(String tipo, Pageable pageable);

    Page<FacturaFis> findByFechaTimbradoBetweenOrderByFechaTimbradoDesc(Instant desde, Instant hasta, Pageable pageable);

    Page<FacturaFis> findByTipoAndFechaTimbradoBetweenOrderByFechaTimbradoDesc(String tipo, Instant desde, Instant hasta, Pageable pageable);
}