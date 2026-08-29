package mx.ferreteria.api.ven.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "promocion_categorias", schema = "ven")
@IdClass(PromocionCategoriaId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromocionCategoria {

    @Id
    @Column(name = "promocion_id")
    private Long promocionId;

    @Id
    @Column(name = "categoria_id")
    private Integer categoriaId;
}