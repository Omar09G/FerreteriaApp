package mx.ferreteria.api.cat.entity;

import java.math.BigDecimal;

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
@Table(name = "puestos", schema = "cat")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Puesto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer puestoId;

    @Column(nullable = false, unique = true, length = 80)
    private String nombre;

    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal sueldoBase = BigDecimal.ZERO;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;
}
