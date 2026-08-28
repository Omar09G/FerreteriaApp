package mx.ferreteria.api.cat.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "formas_pago", schema = "cat")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FormaPago {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer formaPagoId;

    @Column(nullable = false, length = 20)
    private String clave;

    @Column(nullable = false, unique = true, length = 40)
    private String nombre;

    @Column(name = "es_efectivo", nullable = false)
    @Builder.Default
    private Boolean esEfectivo = false;

    @Column(name = "afecta_caja", nullable = false)
    @Builder.Default
    private Boolean afectaCaja = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;

    @Column(name = "requiere_referencia", nullable = false)
    @Builder.Default
    private Boolean requiereReferencia = false;
    @Column(name = "forma_pago_sat", nullable = true, length = 20)
    private String formaPagoSat;
    @Column(name = "comision_pct", nullable = true, length = 20)
    private Double comisionPct;
}
