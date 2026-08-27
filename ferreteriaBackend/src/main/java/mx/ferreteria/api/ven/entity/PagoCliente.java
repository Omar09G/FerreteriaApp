package mx.ferreteria.api.ven.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "pagos_cliente", schema = "ven")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PagoCliente {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long pagoClienteId;

    @Column(nullable = false)
    private Long cuentaCobrarId;

    @Column(nullable = false)
    private Integer formaPagoId;

    private String referencia;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false)
    @Builder.Default private Instant fecha = Instant.now();

    @Column(nullable = false)
    private Integer usuarioId;

    private Long turnoCajaId;
}
