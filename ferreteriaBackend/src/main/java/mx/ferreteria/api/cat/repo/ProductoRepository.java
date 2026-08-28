package mx.ferreteria.api.cat.repo;

import mx.ferreteria.api.cat.entity.Producto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;


public interface ProductoRepository extends JpaRepository<Producto, Long> {
    Page<Producto> findByActivoTrue(Pageable pageable);

    Page<Producto> findByActivoTrueAndNombreContainingIgnoreCase(String nombre, Pageable pageable);

    Page<Producto> findByCategoriaCategoriaIdAndActivoTrue(Integer categoriaId, Pageable pageable);

    Page<Producto> findByMarcaMarcaIdAndActivoTrue(Integer marcaId, Pageable pageable);

    Page<Producto> findByTipoAndActivoTrue(String tipo, Pageable pageable);

    Page<Producto> findByCodigoContainingIgnoreCase(String codigo, Pageable pageable);
}
