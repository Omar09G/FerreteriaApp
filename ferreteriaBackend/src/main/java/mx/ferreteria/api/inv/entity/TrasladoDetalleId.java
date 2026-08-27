package mx.ferreteria.api.inv.entity;

import java.io.Serializable;
import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@EqualsAndHashCode
public class TrasladoDetalleId implements Serializable {

    private Long trasladoId;
    private Long productoId;
}
