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
@Table(name = "claves_prod_serv", schema = "fis")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClaveProdServ {

    @Id
    @Column(length = 8)
    private String clave;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String descripcion;

    private Boolean incluyeIva;

    @Column(nullable = false)
    @Builder.Default
    private Boolean ejemplo = false;
}
