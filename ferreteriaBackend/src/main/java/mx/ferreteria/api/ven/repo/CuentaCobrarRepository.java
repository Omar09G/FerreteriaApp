package mx.ferreteria.api.ven.repo;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import mx.ferreteria.api.ven.entity.CuentaCobrar;

public interface CuentaCobrarRepository extends JpaRepository<CuentaCobrar, Long> {
    Optional<CuentaCobrar> findByVentaId(Long ventaId);

    /**
     * Cobranza con filtros opcionales combinados (velocidad + rango de fechas).
     * El rango aplica sobre fecha_vencimiento (DATE), que es la fecha visible en
     * pantalla: "cuentas que vencen entre desde y hasta".
     */
    @Query("""
            SELECT c FROM CuentaCobrar c
            WHERE c.clienteId  = COALESCE(:clienteId, c.clienteId)
              AND c.estado     = COALESCE(:estado, c.estado)
              AND c.fechaVencimiento >= COALESCE(:desde, c.fechaVencimiento)
              AND c.fechaVencimiento <= COALESCE(:hasta, c.fechaVencimiento)
            ORDER BY c.creadoEn DESC
            """)
    Page<CuentaCobrar> filtrar(Long clienteId, String estado,
            LocalDate desde, LocalDate hasta, Pageable pageable);
}
