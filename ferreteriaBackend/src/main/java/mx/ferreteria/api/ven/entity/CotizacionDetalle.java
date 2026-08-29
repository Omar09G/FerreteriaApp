package mx.ferreteria.api.ven.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.*;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

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

    /**
     * Columna GENERATED ALWAYS AS (cantidad * precio_unitario) STORED en BD.
     * Hibernate no debe escribirla; se marca insertable=false + updatable=false
     * y se usa @Generated para que el dialect PG la lea tras el INSERT.
     */
    @Generated(event = EventType.INSERT)
    @Column(insertable = false, updatable = false)
    private BigDecimal importeLinea;
}
