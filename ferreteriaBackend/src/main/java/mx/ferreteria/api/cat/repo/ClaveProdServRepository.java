package mx.ferreteria.api.cat.repo;

import java.util.List;

import mx.ferreteria.api.cat.entity.ClaveProdServ;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaveProdServRepository extends JpaRepository<ClaveProdServ, String> {
    List<ClaveProdServ> findByEjemploFalseOrderByClave();
}
