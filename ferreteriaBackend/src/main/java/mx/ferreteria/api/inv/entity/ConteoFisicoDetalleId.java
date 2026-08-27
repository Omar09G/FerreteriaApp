package mx.ferreteria.api.inv.entity;

import java.io.Serializable;
import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@EqualsAndHashCode
public class ConteoFisicoDetalleId implements Serializable {

    private Long conteoId;
    private Long productoId;
}
