package mx.ferreteria.api.fis.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "facturas", schema = "fis")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FacturaFis {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long facturaId;

    @Column(nullable = false, length = 10)
    private String tipo;

    @Column(length = 20)
    private String serie;

    @Column(nullable = false, length = 40)
    private String folio;

    @Column(length = 64)
    private String uuid;

    @Column(nullable = false, length = 13)
    private String emisorRfc;

    @Column(nullable = false, length = 13)
    private String receptorRfc;

    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default private BigDecimal iva = BigDecimal.ZERO;

    @Column(precision = 14, scale = 2, insertable = false, updatable = false)
    private BigDecimal total;

    @Column(nullable = false)
    @Builder.Default private Instant fechaTimbrado = Instant.now();

    @Column(columnDefinition = "text")
    private String cfdiXml;

    @Column(nullable = false, length = 12)
    @Builder.Default private String estado = "ACTIVA";

    private Long ventaId;

    @Column(nullable = false)
    private Integer usuarioId;

    @Column(nullable = false)
    @Builder.Default private Instant creadoEn = Instant.now();
}