package mx.ferreteria.api.fin.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.*;

@Entity
@Table(name = "ingresos_otros", schema = "fin")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class IngresoOtro {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long ingresoOtroId;

    @Column(nullable = false, length = 150)
    private String concepto;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false)
    @Builder.Default private LocalDate fecha = LocalDate.now();

    @Column(nullable = false)
    private Integer formaPagoId;

    private Long turnoCajaId;

    @Column(nullable = false)
    private Integer usuarioId;

    @Column(nullable = false)
    @Builder.Default private Instant creadoEn = Instant.now();
}
