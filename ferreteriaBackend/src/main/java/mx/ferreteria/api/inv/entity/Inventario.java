package mx.ferreteria.api.inv.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.*;

@Entity
@Table(name = "inventario", schema = "inv")
@IdClass(InventarioId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventario {

    @Id
    @Column(name = "producto_id")
    private Long productoId;

    @Id
    @Column(name = "almacen_id")
    private Integer almacenId;

    @Column(nullable = false, precision = 12, scale = 3)
    @Builder.Default
    private BigDecimal stock = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 3)
    @Builder.Default
    private BigDecimal stockMinimo = BigDecimal.ZERO;

    @Column(precision = 12, scale = 3)
    private BigDecimal stockMaximo;

    @Column(nullable = false, precision = 12, scale = 3)
    @Builder.Default
    private BigDecimal reservado = BigDecimal.ZERO;
}
