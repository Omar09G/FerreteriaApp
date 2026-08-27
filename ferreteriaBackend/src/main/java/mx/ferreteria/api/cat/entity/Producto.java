package mx.ferreteria.api.cat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "productos", schema = "inv")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long productoId;

    @Column(unique = true, length = 40)
    private String codigo;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String tipo = "PRODUCTO";

    @Column(nullable = false, length = 180)
    private String nombre;

    private String descripcion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marca_id")
    private Marca marca;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unidad_medida_id", nullable = false)
    private UnidadMedida unidadMedida;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal costoActual = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal precioMenudeo = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal precioMayoreo;

    @Column(nullable = false)
    @Builder.Default
    private Boolean aplicaIva = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;
}
