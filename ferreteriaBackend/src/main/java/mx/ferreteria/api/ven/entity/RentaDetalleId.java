package mx.ferreteria.api.ven.entity;

import java.io.Serializable;
import lombok.*;

@NoArgsConstructor @AllArgsConstructor @Getter @EqualsAndHashCode
public class RentaDetalleId implements Serializable {
    private Long rentaId;
    private Long productoId;
}
