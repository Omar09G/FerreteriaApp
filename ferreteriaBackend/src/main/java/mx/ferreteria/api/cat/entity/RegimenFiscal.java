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
@Table(name = "regimenes_fiscales", schema = "fis")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegimenFiscal {

    @Id
    @Column(name = "clave_sat", length = 3)
    private String claveSat;

    @Column(nullable = false, length = 120)
    private String descripcion;

    @Column(nullable = false)
    @Builder.Default
    private Boolean personaFisica = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean personaMoral = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;
}
