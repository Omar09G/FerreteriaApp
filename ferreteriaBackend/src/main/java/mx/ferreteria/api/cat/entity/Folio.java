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
@Table(name = "folios", schema = "cfg")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Folio {

    @Id
    @Column(length = 25)
    private String tipo;

    @Column(nullable = false, length = 6)
    private String prefijo;

    @Column(nullable = false)
    @Builder.Default
    private Long consecutivo = 0L;
}
