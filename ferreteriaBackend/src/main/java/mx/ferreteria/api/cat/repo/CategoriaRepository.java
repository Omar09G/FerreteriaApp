package mx.ferreteria.api.cat.repo;

import java.util.List;

import mx.ferreteria.api.cat.entity.Categoria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;


public interface CategoriaRepository extends JpaRepository<Categoria, Integer> {
    List<Categoria> findByActivoTrueAndCategoriaPadreIsNullOrderByNombre();

    List<Categoria> findByCategoriaPadreCategoriaIdAndActivoTrueOrderByNombre(Integer padreId);

    Page<Categoria> findByActivoTrue(Pageable pageable);
}
