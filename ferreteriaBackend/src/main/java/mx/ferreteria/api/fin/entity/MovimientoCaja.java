package mx.ferreteria.api.fin.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "movimientos_caja", schema = "fin")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MovimientoCaja {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long movimientoId;

    @Column(nullable = false)
    private Long turnoCajaId;

    @Column(nullable = false, length = 8)
    private String tipo;

    @Column(nullable = false, length = 30)
    private String concepto;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal monto;

    private Integer formaPagoId;

    @Column(length = 40)
    private String refTabla;

    private Long refId;

    private Integer usuarioId;

    @Column(nullable = false)
    @Builder.Default private Instant creadoEn = Instant.now();
}
