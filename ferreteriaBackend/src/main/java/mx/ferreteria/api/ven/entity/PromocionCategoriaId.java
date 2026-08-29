package mx.ferreteria.api.ven.entity;

import java.io.Serializable;
import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
public class PromocionCategoriaId implements Serializable {

    private Long promocionId;
    private Integer categoriaId;
}