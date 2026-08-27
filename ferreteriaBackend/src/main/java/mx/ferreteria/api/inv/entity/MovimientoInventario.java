package mx.ferreteria.api.inv.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.*;

@Entity
@Table(name = "movimientos_inventario", schema = "inv")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovimientoInventario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long movimientoId;

    @Column(nullable = false)
    private Long productoId;

    @Column(nullable = false)
    private Integer almacenId;

    @Column(nullable = false, length = 8)
    private String tipo;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal cantidad;

    @Column(precision = 12, scale = 2)
    private BigDecimal costoUnitario;

    @Column(nullable = false)
    private Integer motivoId;

    @Column(length = 40)
    private String refTabla;

    private Long refId;

    private Long trasladoId;

    private String nota;

    private Integer usuarioId;
}
