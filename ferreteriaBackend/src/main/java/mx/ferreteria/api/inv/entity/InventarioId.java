package mx.ferreteria.api.inv.entity;

import java.io.Serializable;
import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@EqualsAndHashCode
public class InventarioId implements Serializable {

    private Long productoId;
    private Integer almacenId;
}
