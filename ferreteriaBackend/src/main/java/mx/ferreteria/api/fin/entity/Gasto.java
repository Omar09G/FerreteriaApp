package mx.ferreteria.api.fin.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.*;

@Entity
@Table(name = "gastos", schema = "fin")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Gasto {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long gastoId;

    @Column(nullable = false, unique = true)
    private String folio;

    @Column(nullable = false)
    private Integer tipoGastoId;

    @Column(nullable = false, length = 250)
    private String descripcion;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false)
    @Builder.Default private LocalDate fechaGasto = LocalDate.now();

    @Column(nullable = false)
    private Integer formaPagoId;

    private Integer proveedorId;

    private Long turnoCajaId;

    @Column(length = 64)
    private String facturaUuid;

    @Column(nullable = false)
    private Integer usuarioId;

    @Column(nullable = false)
    @Builder.Default private Instant creadoEn = Instant.now();
}
