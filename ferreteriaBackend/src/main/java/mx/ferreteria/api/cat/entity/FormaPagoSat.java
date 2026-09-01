package mx.ferreteria.api.cat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "formas_pago_sat", schema = "fis")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FormaPagoSat {

    @Id
    @Column(length = 2)
    private String clave;

    @Column(nullable = false, length = 80)
    private String descripcion;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;
}
