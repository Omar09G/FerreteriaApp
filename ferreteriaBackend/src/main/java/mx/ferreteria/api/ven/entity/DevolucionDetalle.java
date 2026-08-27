package mx.ferreteria.api.ven.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.*;

@Entity
@Table(name = "devolucion_detalles", schema = "ven")
@IdClass(DevolucionDetalleId.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DevolucionDetalle {
    @Id @Column(name = "devolucion_id")
    private Long devolucionId;

    @Id @Column(name = "producto_id")
    private Long productoId;

    private Long ventaDetalleId;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal cantidad;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precioUnitario;

    @Column(columnDefinition = "numeric(14,2) generated always as (cantidad * precio_unitario) stored")
    private BigDecimal importeLinea;
}
