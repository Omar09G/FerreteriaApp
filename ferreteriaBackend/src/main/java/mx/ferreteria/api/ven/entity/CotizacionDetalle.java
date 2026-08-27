package mx.ferreteria.api.ven.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.*;

@Entity
@Table(name = "cotizacion_detalles", schema = "ven")
@IdClass(CotizacionDetalleId.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CotizacionDetalle {
    @Id @Column(name = "cotizacion_id")
    private Long cotizacionId;

    @Id @Column(name = "producto_id")
    private Long productoId;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal cantidad;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precioUnitario;

    @Column(columnDefinition = "numeric(14,2) generated always as (cantidad * precio_unitario) stored")
    private BigDecimal importeLinea;
}
