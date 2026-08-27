package mx.ferreteria.api.inv.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "almacenes", schema = "inv")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Almacen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer almacenId;

    @Column(nullable = false, unique = true, length = 100)
    private String nombre;

    private String direccion;

    @Column(length = 20)
    private String telefono;

    @Column(nullable = false)
    @Builder.Default
    private Boolean esPuntoVenta = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;
}
