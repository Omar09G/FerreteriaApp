package mx.ferreteria.api.com.repo;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import mx.ferreteria.api.com.entity.Compra;

public interface CompraRepository extends JpaRepository<Compra, Long> {
    Page<Compra> findByAlmacenIdAndFechaBetweenOrderByFechaDesc(Integer almacenId, Instant desde, Instant hasta, Pageable pageable);

    Page<Compra> findByFechaBetweenOrderByFechaDesc(Instant desde, Instant hasta, Pageable pageable);

    Page<Compra> findByAlmacenIdOrderByFechaDesc(Integer almacenId, Pageable pageable);

    Page<Compra> findByProveedorIdOrderByFechaDesc(Integer proveedorId, Pageable pageable);
}