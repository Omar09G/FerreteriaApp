package mx.ferreteria.api.inv.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "conteos_fisicos", schema = "inv")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConteoFisico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long conteoId;

    @Column(nullable = false)
    private Integer almacenId;

    @Column(nullable = false, length = 12)
    @Builder.Default
    private String estado = "EN_PROCESO";

    @Column(nullable = false)
    private Integer usuarioId;

    private String observaciones;
}
