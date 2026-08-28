package mx.ferreteria.api.com.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.*;

@Entity
@Table(name = "compra_detalles", schema = "com")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CompraDetalle {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long compraDetalleId;

    @Column(nullable = false)
    private Long compraId;

    @Column(nullable = false)
    private Long productoId;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal cantidad;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal costoUnitario;

    @Column(name = "importe_linea", precision = 14, scale = 2, insertable = false, updatable = false)
    private BigDecimal importeLinea;
}