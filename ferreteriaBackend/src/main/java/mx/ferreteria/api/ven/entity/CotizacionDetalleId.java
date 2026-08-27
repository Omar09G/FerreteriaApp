package mx.ferreteria.api.ven.entity;

import java.io.Serializable;
import lombok.*;

@NoArgsConstructor @AllArgsConstructor @Getter @EqualsAndHashCode
public class CotizacionDetalleId implements Serializable {
    private Long cotizacionId;
    private Long productoId;
}
