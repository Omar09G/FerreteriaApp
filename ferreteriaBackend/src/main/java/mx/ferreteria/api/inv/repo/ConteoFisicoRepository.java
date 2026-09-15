package mx.ferreteria.api.inv.repo;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import mx.ferreteria.api.inv.entity.ConteoFisico;

public interface ConteoFisicoRepository extends JpaRepository<ConteoFisico, Long> {

    /**
     * Filtro combinado para el listado de conteos. {@code almacenId} y
     * {@code estado} son opcionales (null = sin filtro) vía COALESCE contra la
     * propia columna — mismo patrón que {@code NominaRepository.filtrar}.
     * {@code desde}/{@code hasta} son SIEMPRE no-nulos (el servicio suple
     * sentinelas): un {@code :param IS NULL} con Instant nulo rompe en
     * PostgreSQL ("could not determine data type of parameter").
     * Rango: [desde, hasta).
     */
    @Query("""
            SELECT c FROM ConteoFisico c
            WHERE c.almacenId = COALESCE(:almacenId, c.almacenId)
              AND c.estado = COALESCE(:estado, c.estado)
              AND c.fecha >= :desde AND c.fecha < :hasta
            ORDER BY c.fecha DESC
            """)
    Page<ConteoFisico> filtrar(
            @Param("almacenId") Integer almacenId,
            @Param("estado") String estado,
            @Param("desde") Instant desde,
            @Param("hasta") Instant hasta,
            Pageable pageable);

    /**
     * Variante con filtro por producto contado (EXISTS sobre el detalle).
     * Query separada para no condicionar el EXISTS a un parámetro nulo.
     */
    @Query("""
            SELECT c FROM ConteoFisico c
            WHERE c.almacenId = COALESCE(:almacenId, c.almacenId)
              AND c.estado = COALESCE(:estado, c.estado)
              AND c.fecha >= :desde AND c.fecha < :hasta
              AND EXISTS (
                    SELECT 1 FROM ConteoFisicoDetalle d
                    WHERE d.conteoId = c.conteoId AND d.productoId = :productoId)
            ORDER BY c.fecha DESC
            """)
    Page<ConteoFisico> filtrarPorProducto(
            @Param("almacenId") Integer almacenId,
            @Param("estado") String estado,
            @Param("productoId") Long productoId,
            @Param("desde") Instant desde,
            @Param("hasta") Instant hasta,
            Pageable pageable);
}
