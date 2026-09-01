package mx.ferreteria.api.cat.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tasas_impuesto", schema = "fis")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TasaImpuesto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer tasaId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "impuesto_id", nullable = false)
    private Impuesto impuesto;

    @Column(nullable = false, precision = 6, scale = 4)
    private BigDecimal tasa;

    @Column(nullable = false, length = 8)
    @Builder.Default
    private String factor = "TASA";

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String ambito = "VENTA";

    @Column(nullable = false)
    @Builder.Default
    private Boolean zonaFrontera = false;

    @Column(name = "vigente_desde", nullable = false)
    @Builder.Default
    private LocalDate vigenteDesde = LocalDate.now();

    @Column(name = "vigente_hasta")
    private LocalDate vigenteHasta;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;
}
