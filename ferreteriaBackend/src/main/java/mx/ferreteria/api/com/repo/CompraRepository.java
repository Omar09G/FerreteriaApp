package mx.ferreteria.api.com.repo;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import mx.ferreteria.api.com.entity.Compra;

public interface CompraRepository extends JpaRepository<Compra, Long> {
    // Filtros por fecha_local (DATE generada en BD) — sin desfase de zona horaria.
    Page<Compra> findByAlmacenIdAndFechaLocalBetweenOrderByFechaDesc(Integer almacenId, LocalDate desde, LocalDate hasta, Pageable pageable);

    Page<Compra> findByFechaLocalBetweenOrderByFechaDesc(LocalDate desde, LocalDate hasta, Pageable pageable);

    Page<Compra> findByAlmacenIdOrderByFechaDesc(Integer almacenId, Pageable pageable);

    Page<Compra> findByProveedorIdOrderByFechaDesc(Integer proveedorId, Pageable pageable);
}