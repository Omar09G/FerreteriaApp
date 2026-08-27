package mx.ferreteria.api.fin.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.*;

@Entity
@Table(name = "cortes_caja", schema = "fin")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CorteCaja {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long corteId;

    @Column(nullable = false, unique = true)
    private Long turnoCajaId;

    @Column(nullable = false)
    private Integer cajaId;

    @Column(nullable = false)
    private Integer almacenId;

    @Column(nullable = false)
    private Integer usuarioId;

    @Column(nullable = false)
    private Integer usuarioCierreId;

    @Column(nullable = false)
    @Builder.Default private LocalDate fecha = LocalDate.now();

    @Column(nullable = false)
    private Instant aperturaEn;

    @Column(nullable = false)
    @Builder.Default private Instant cierreEn = Instant.now();

    @Column(nullable = false)
    @Builder.Default private Integer numVentas = 0;

    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default private BigDecimal iva = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default private BigDecimal descuentos = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default private BigDecimal totalVendido = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default private BigDecimal costoVentas = BigDecimal.ZERO;

    @Column(columnDefinition = "numeric(14,2) generated always as (subtotal - costo_ventas) stored")
    private BigDecimal utilidadBruta;

    @Column(columnDefinition = "numeric(6,2) generated always as ((subtotal - costo_ventas) / nullif(subtotal,0) * 100) stored")
    private BigDecimal margenPct;

    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default private BigDecimal fondoApertura = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default private BigDecimal entradasEfectivo = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default private BigDecimal salidasEfectivo = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default private BigDecimal dineroEsperado = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default private BigDecimal dineroContado = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default private BigDecimal diferencia = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default private BigDecimal ingresosNoEfectivo = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default private BigDecimal egresosNoEfectivo = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default private BigDecimal perdidasInventario = BigDecimal.ZERO;

    @Column(columnDefinition = "jsonb")
    @Builder.Default private String desgloseEntradas = "{}";

    @Column(columnDefinition = "jsonb")
    @Builder.Default private String desgloseSalidas = "{}";

    @Column(columnDefinition = "jsonb")
    @Builder.Default private String desgloseFormasPago = "{}";

    private String observaciones;

    @Column(nullable = false)
    @Builder.Default private Instant creadoEn = Instant.now();
}
