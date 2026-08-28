package mx.ferreteria.api.rh.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.*;

@Entity
@Table(name = "nominas", schema = "rh")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Nomina {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long nominaId;

    @Column(nullable = false)
    private Integer empleadoId;

    @Column(nullable = false)
    private LocalDate periodoIni;

    @Column(nullable = false)
    private LocalDate periodoFin;

    @Column(nullable = false, precision = 4, scale = 1)
    private BigDecimal diasPagados;

    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default private BigDecimal percepciones = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default private BigDecimal deducciones = BigDecimal.ZERO;

    @Column(precision = 14, scale = 2, insertable = false, updatable = false)
    private BigDecimal netoPagar;

    @Column(nullable = false, length = 12)
    @Builder.Default private String estado = "PENDIENTE";

    private Instant fechaPago;

    @Column(nullable = false)
    private Integer usuarioRegistraId;

    private String notas;
}