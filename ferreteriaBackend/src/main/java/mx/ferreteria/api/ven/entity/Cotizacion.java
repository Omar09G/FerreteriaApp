package mx.ferreteria.api.ven.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.*;

@Entity
@Table(name = "cotizaciones", schema = "ven")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Cotizacion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long cotizacionId;

    @Column(nullable = false, unique = true)
    private String folio;

    private Long clienteId;

    @Column(nullable = false)
    private Instant fecha;

    private LocalDate vigenciaHasta;

    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default private BigDecimal iva = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default private BigDecimal total = BigDecimal.ZERO;

    @Column(nullable = false, length = 12)
    @Builder.Default private String estado = "VIGENTE";

    private Long ventaGeneradaId;

    @Column(nullable = false)
    private Integer usuarioId;
}
