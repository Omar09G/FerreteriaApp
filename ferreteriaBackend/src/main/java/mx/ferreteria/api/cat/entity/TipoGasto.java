package mx.ferreteria.api.cat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tipos_gasto", schema = "cat")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TipoGasto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer tipoGastoId;

    @Column(nullable = false, unique = true, length = 30)
    private String clave;

    @Column(nullable = false, length = 80)
    private String nombre;

    @Column(nullable = false)
    @Builder.Default
    private Boolean esFijo = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;
}
