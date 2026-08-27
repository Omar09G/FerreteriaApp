package mx.ferreteria.api.cat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "clientes", schema = "ven")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long clienteId;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String tipoPersona = "FISICA";

    @Column(nullable = false, length = 180)
    private String razonSocial;

    @Column(length = 180)
    private String nombreComercial;

    @Column(length = 13)
    private String rfc;

    @Column(length = 20)
    private String telefono;

    @Column(length = 120)
    private String email;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal limiteCredito = BigDecimal.ZERO;

    @Column(nullable = false)
    @Builder.Default
    private Integer diasCredito = 0;

    @Column(nullable = false)
    @Builder.Default
    private Boolean esMayorista = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;
}
