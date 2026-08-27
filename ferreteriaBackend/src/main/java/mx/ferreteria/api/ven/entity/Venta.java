package mx.ferreteria.api.ven.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "ventas", schema = "ven")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Venta {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long ventaId;

    @Column(nullable = false, unique = true)
    private String folio;

    private Long clienteId;

    @Column(nullable = false)
    private Integer almacenId;

    private Long cotizacionId;

    @Column(nullable = false)
    private Instant fecha;

    @Column(columnDefinition = "date")
    private LocalDate fechaLocal;

    @Column(nullable = false)
    private Integer formaPagoId;

    @Column(nullable = false, precision = 5, scale = 2)
    @Builder.Default private BigDecimal ivaTasa = new BigDecimal("16.00");

    @Column(nullable = false)
    @Builder.Default private Boolean ivaIncluido = true;

    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default private BigDecimal iva = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default private BigDecimal descuentoTotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default private BigDecimal total = BigDecimal.ZERO;

    @Column(nullable = false, length = 12)
    @Builder.Default private String estado = "COMPLETADA";

    @Column(nullable = false)
    private Integer usuarioId;

    private Long turnoCajaId;

    @Column(length = 3)
    private String metodoPagoSat;

    private UUID folioFiscalUuid;

    private String notas;
}
