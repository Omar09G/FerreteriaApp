package mx.ferreteria.api.cat.repo;

import mx.ferreteria.api.cat.entity.ProductoCodigoBarras;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CodigoBarrasRepository extends JpaRepository<ProductoCodigoBarras, String> {

    Optional<ProductoCodigoBarras> findByCodigoBarras(String codigoBarras);

    List<ProductoCodigoBarras> findByProductoProductoId(Long productoId);

    List<ProductoCodigoBarras> findByProductoProductoIdIn(Collection<Long> productoIds);

    void deleteByProductoProductoId(Long productoId);
}
