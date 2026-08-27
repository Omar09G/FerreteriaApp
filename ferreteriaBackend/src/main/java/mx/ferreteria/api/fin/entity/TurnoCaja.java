package mx.ferreteria.api.fin.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "turnos_caja", schema = "fin")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TurnoCaja {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long turnoCajaId;

    @Column(nullable = false)
    private Integer cajaId;

    @Column(nullable = false)
    private Integer usuarioId;

    @Column(nullable = false)
    @Builder.Default private Instant aperturaEn = Instant.now();

    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default private BigDecimal montoApertura = BigDecimal.ZERO;

    private Instant cierreEn;

    private BigDecimal montoEsperado;

    private BigDecimal montoContado;

    private BigDecimal diferencia;

    @Column(nullable = false, length = 10)
    @Builder.Default private String estado = "ABIERTO";

    private String observaciones;
}
