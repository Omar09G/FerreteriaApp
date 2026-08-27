package mx.ferreteria.api.inv.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.*;

@Entity
@Table(name = "conteos_fisicos_detalle", schema = "inv")
@IdClass(ConteoFisicoDetalleId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConteoFisicoDetalle {

    @Id
    @Column(name = "conteo_id")
    private Long conteoId;

    @Id
    @Column(name = "producto_id")
    private Long productoId;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal cantidadSistema;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal cantidadFisica;
}
