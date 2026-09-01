package mx.ferreteria.api.cat.repo;

import java.util.Optional;

import mx.ferreteria.api.cat.entity.Folio;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FolioRepository extends JpaRepository<Folio, String> {
    Optional<Folio> findByTipo(String tipo);
}
