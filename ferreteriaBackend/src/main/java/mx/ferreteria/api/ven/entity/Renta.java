package mx.ferreteria.api.ven.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.*;

@Entity
@Table(name = "rentas", schema = "ven")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Renta {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long rentaId;

    @Column(nullable = false, unique = true)
    private String folio;

    @Column(nullable = false)
    private Long clienteId;

    @Column(nullable = false)
    private Integer almacenId;

    @Column(nullable = false)
    @Builder.Default private Instant fechaRenta = Instant.now();

    @Column(nullable = false)
    private LocalDate fechaDevEsperada;

    private Instant fechaDevReal;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default private BigDecimal deposito = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default private BigDecimal costoTotal = BigDecimal.ZERO;

    @Column(nullable = false, length = 12)
    @Builder.Default private String estado = "ABIERTA";

    @Column(nullable = false)
    private Integer usuarioId;

    private Long turnoCajaId;

    private Integer formaPagoId;
}
