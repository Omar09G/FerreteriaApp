package mx.ferreteria.api.ven.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "promocion_productos", schema = "ven")
@IdClass(PromocionProductoId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromocionProducto {

    @Id
    @Column(name = "promocion_id")
    private Long promocionId;

    @Id
    @Column(name = "producto_id")
    private Long productoId;
}