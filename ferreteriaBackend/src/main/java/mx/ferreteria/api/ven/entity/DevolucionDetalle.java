package mx.ferreteria.api.ven.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.*;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

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

    /** GENERATED ALWAYS AS (cantidad * precio_unitario) STORED en BD — no escribirla desde JPA. */
    @Generated(event = EventType.INSERT)
    @Column(insertable = false, updatable = false)
    private BigDecimal importeLinea;
}
