package mx.ferreteria.api.ven.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "devoluciones_venta", schema = "ven")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DevolucionVenta {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long devolucionId;

    @Column(nullable = false, unique = true)
    private String folio;

    @Column(nullable = false)
    private Long ventaId;

    @Column(nullable = false)
    @Builder.Default private Instant fecha = Instant.now();

    @Column(nullable = false)
    private String motivo;

    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default private BigDecimal total = BigDecimal.ZERO;

    @Column(nullable = false)
    private Integer formaDevolucionId;

    @Column(nullable = false)
    private Integer usuarioId;

    private Long turnoCajaId;
}
