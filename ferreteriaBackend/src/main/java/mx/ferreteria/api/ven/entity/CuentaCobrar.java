package mx.ferreteria.api.ven.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.*;

@Entity
@Table(name = "cuentas_cobrar", schema = "ven")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CuentaCobrar {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long cuentaCobrarId;

    @Column(nullable = false, unique = true)
    private Long ventaId;

    private Long clienteId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal montoTotal;

    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default private BigDecimal montoPagado = BigDecimal.ZERO;

    @Column(nullable = false)
    private LocalDate fechaVencimiento;

    @Column(nullable = false, length = 12)
    @Builder.Default private String estado = "VIGENTE";

    @Column(nullable = false)
    @Builder.Default private Instant creadoEn = Instant.now();
}
