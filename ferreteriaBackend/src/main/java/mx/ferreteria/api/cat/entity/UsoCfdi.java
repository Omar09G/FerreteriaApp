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
@Table(name = "usos_cfdi", schema = "fis")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsoCfdi {

    @Id
    @Column(length = 4)
    private String clave;

    @Column(nullable = false, length = 150)
    private String descripcion;

    @Column(nullable = false)
    @Builder.Default
    private Boolean aplicaFisica = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean aplicaMoral = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;
}
