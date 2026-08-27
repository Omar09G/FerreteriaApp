package mx.ferreteria.api.cat.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "formas_pago", schema = "cat")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FormaPago {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer formaPagoId;

    @Column(nullable = false, unique = true, length = 40)
    private String nombre;

    @Column(nullable = false, length = 20)
    @Builder.Default private String tipo = "EFECTIVO";

    @Column(nullable = false)
    @Builder.Default private Boolean activa = true;

    @Column(nullable = false)
    @Builder.Default private Boolean esCredito = false;
}
