package mx.ferreteria.api.fin.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "cajas", schema = "fin")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Caja {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer cajaId;

    @Column(nullable = false, unique = true, length = 80)
    private String nombre;

    @Column(nullable = false)
    private Integer almacenId;

    @Column(nullable = false)
    @Builder.Default private Boolean activa = true;
}
