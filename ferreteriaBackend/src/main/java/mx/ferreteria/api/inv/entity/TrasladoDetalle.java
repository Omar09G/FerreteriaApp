package mx.ferreteria.api.inv.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.*;

@Entity
@Table(name = "traslado_detalles", schema = "inv")
@IdClass(TrasladoDetalleId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrasladoDetalle {

    @Id
    @Column(name = "traslado_id")
    private Long trasladoId;

    @Id
    @Column(name = "producto_id")
    private Long productoId;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal cantidad;
}
