package mx.ferreteria.api.ven.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.*;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Entity
@Table(name = "renta_detalles", schema = "ven")
@IdClass(RentaDetalleId.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RentaDetalle {
    @Id @Column(name = "renta_id")
    private Long rentaId;

    @Id @Column(name = "producto_id")
    private Long productoId;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal cantidad;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal costoDia;

    @Column(nullable = false, precision = 6, scale = 1)
    @Builder.Default private BigDecimal diasCobrados = BigDecimal.ZERO;

    /** GENERATED ALWAYS AS (costo_dia * dias_cobrados) STORED en BD — no escribirla desde JPA. */
    @Generated(event = EventType.INSERT)
    @Column(insertable = false, updatable = false)
    private BigDecimal subtotal;
}
