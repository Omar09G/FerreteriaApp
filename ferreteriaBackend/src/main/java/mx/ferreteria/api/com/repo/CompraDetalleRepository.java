package mx.ferreteria.api.com.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import mx.ferreteria.api.com.entity.CompraDetalle;

public interface CompraDetalleRepository extends JpaRepository<CompraDetalle, Long> {
    List<CompraDetalle> findByCompraIdOrderByCompraDetalleId(Long compraId);
}