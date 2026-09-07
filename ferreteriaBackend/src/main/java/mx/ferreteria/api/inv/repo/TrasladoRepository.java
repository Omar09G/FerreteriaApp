package mx.ferreteria.api.inv.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import mx.ferreteria.api.inv.entity.Traslado;

public interface TrasladoRepository extends JpaRepository<Traslado, Long> {
    @Query(value = "SELECT * FROM inv.traslados ORDER BY creado_en DESC", nativeQuery = true)
    Page<Traslado> findAllByOrderByCreadoEnDesc(Pageable pageable);

    @Query(value = "SELECT folio FROM inv.traslados WHERE traslado_id = :id", nativeQuery = true)
    String findFolioById(@org.springframework.data.repository.query.Param("id") Long id);
}

