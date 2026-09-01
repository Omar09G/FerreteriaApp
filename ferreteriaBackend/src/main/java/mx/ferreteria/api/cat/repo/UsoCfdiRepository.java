package mx.ferreteria.api.cat.repo;

import java.util.List;

import mx.ferreteria.api.cat.entity.UsoCfdi;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsoCfdiRepository extends JpaRepository<UsoCfdi, String> {
    List<UsoCfdi> findByActivoTrueOrderByClave();
}
