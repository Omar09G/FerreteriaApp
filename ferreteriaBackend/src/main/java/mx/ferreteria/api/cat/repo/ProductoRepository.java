package mx.ferreteria.api.cat.repo;

import mx.ferreteria.api.cat.entity.Producto;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductoRepository extends JpaRepository<Producto, Long> {
    Page<Producto> findByActivoTrue(Pageable pageable);

    Page<Producto> findByActivoTrueAndNombreContainingIgnoreCase(String nombre, Pageable pageable);

    Page<Producto> findByCategoriaCategoriaIdAndActivoTrue(Integer categoriaId, Pageable pageable);

    Page<Producto> findByMarcaMarcaIdAndActivoTrue(Integer marcaId, Pageable pageable);

    Page<Producto> findByTipoAndActivoTrue(String tipo, Pageable pageable);

    Page<Producto> findByCodigoContainingIgnoreCase(String codigo, Pageable pageable);

    Page<Producto> findByActivoTrueAndCodigoIgnoreCase(String codigo, Pageable pageable);

    List<Producto> findByCodigoIn(Collection<String> codigos);

    /* -------- Búsqueda difusa del POS (pg_trgm word_similarity) -------- */

    /**
     * Ids ordenados por relevancia para el término ya normalizado
     * (minúsculas, sin comodines LIKE). El ranking vive en el ORDER BY:
     * código de barras exacto &gt; código exacto &gt; prefijo &gt; substring
     * &gt; solo trigram. El WHERE combina ramas indexables (GIN trigram en
     * lower(nombre), PK en barras, btree en codigo).
     */
    @Query(value = """
            SELECT p.producto_id FROM inv.productos p
            WHERE p.activo = true AND (
              EXISTS (SELECT 1 FROM inv.producto_codigos_barras b
                      WHERE b.producto_id = p.producto_id AND b.codigo_barras = :exacto)
              OR lower(p.codigo) = :q
              OR lower(p.codigo) LIKE CONCAT(:q, '%')
              OR lower(p.nombre) LIKE CONCAT(:q, '%')
              OR lower(p.nombre) LIKE CONCAT('%', :likeq, '%')
              OR lower(p.nombre) <% :q
            )
            ORDER BY
              CASE WHEN EXISTS (SELECT 1 FROM inv.producto_codigos_barras b
                                WHERE b.producto_id = p.producto_id AND b.codigo_barras = :exacto)
                       THEN 100
                  WHEN lower(p.codigo) = :q THEN 90
                  WHEN lower(p.codigo) LIKE CONCAT(:q, '%') THEN 80
                  WHEN lower(p.nombre) LIKE CONCAT(:q, '%') THEN 70
                  WHEN lower(p.nombre) LIKE CONCAT('%', :likeq, '%')
                       THEN 50 + 30 * word_similarity(:q, lower(p.nombre))
                  ELSE 30 * word_similarity(:q, lower(p.nombre))
              END DESC,
              p.nombre ASC
            LIMIT :lim
            """, nativeQuery = true)
    List<Long> buscarIds(@Param("q") String q, @Param("exacto") String exacto,
            @Param("likeq") String likeq, @Param("lim") int lim);

    /* -------- Proyecciones BACK-REND-027 (interface-based) -------- */

    Page<ProductoListado> findListadoByCategoriaCategoriaIdAndActivoTrue(Integer categoriaId, Pageable pageable);

    Page<ProductoListado> findListadoByActivoTrue(Pageable pageable);

    Page<ProductoListado> findListadoByActivoTrueAndNombreContainingIgnoreCase(String nombre, Pageable pageable);
}
