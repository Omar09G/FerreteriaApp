package mx.ferreteria.api.inv.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "traslados", schema = "inv")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Traslado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long trasladoId;

    @Column(nullable = false, unique = true)
    private String folio;

    @Column(nullable = false)
    private Integer almacenOrigen;

    @Column(nullable = false)
    private Integer almacenDestino;

    @Column(nullable = false, length = 12)
    @Builder.Default
    private String estado = "APLICADO";

    @Column(nullable = false)
    private Integer usuarioId;
}
