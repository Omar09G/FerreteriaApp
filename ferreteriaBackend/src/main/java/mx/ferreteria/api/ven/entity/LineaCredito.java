package mx.ferreteria.api.ven.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.*;

@Entity
@Table(name = "lineas_credito", schema = "ven")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LineaCredito {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long lineaCreditoId;

    @Column(nullable = false)
    private Long clienteId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal montoAutorizado;

    @Column(nullable = false)
    @Builder.Default private Short diasCredito = 15;

    @Column(nullable = false, precision = 5, scale = 2)
    @Builder.Default private BigDecimal tasaMoratorio = BigDecimal.ZERO;

    @Column(nullable = false)
    @Builder.Default private Instant fechaAutorizacion = Instant.now();

    private LocalDate vigenteHasta;

    @Column(nullable = false)
    private Integer usuarioAutorizoId;

    @Column(nullable = false, length = 12)
    @Builder.Default private String estado = "ACTIVA";

    private String observaciones;
}
