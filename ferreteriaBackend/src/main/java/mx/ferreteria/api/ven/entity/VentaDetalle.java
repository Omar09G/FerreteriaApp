package mx.ferreteria.api.ven.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.*;

@Entity
@Table(name = "venta_detalles", schema = "ven")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VentaDetalle {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long ventaDetalleId;

    @Column(nullable = false)
    private Long ventaId;

    @Column(nullable = false)
    private Long productoId;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal cantidad;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precioUnitario;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default private BigDecimal costoUnitario = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default private BigDecimal descuentoLinea = BigDecimal.ZERO;

    @Column(columnDefinition = "numeric(14,2) generated always as (cantidad * precio_unitario - descuento_linea) stored")
    private BigDecimal totalLinea;

    private Long promocionId;
}
