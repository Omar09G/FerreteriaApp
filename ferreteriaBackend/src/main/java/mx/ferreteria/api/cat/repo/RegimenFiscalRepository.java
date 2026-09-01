package mx.ferreteria.api.cat.repo;

import java.util.List;

import mx.ferreteria.api.cat.entity.RegimenFiscal;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegimenFiscalRepository extends JpaRepository<RegimenFiscal, String> {
    List<RegimenFiscal> findByActivoTrueOrderByClaveSat();
}
