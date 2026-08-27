package mx.ferreteria.api.ven.entity;

import java.io.Serializable;
import lombok.*;

@NoArgsConstructor @AllArgsConstructor @Getter @EqualsAndHashCode
public class DevolucionDetalleId implements Serializable {
    private Long devolucionId;
    private Long productoId;
}
