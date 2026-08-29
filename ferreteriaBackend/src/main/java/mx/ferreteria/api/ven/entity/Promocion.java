package mx.ferreteria.api.ven.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "promociones", schema = "ven")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Promocion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long promocionId;

    @Column(nullable = false, length = 150)
    private String nombre;

    private String descripcion;

    @Column(nullable = false, length = 25)
    private String tipo;

    @Column(precision = 5, scale = 2)
    private BigDecimal valorPct;

    @Column(precision = 12, scale = 2)
    private BigDecimal valorMonto;

    @Column(precision = 12, scale = 2)
    private BigDecimal precioEspecial;

    @Column(precision = 14, scale = 2)
    private BigDecimal compraMinTotal;

    @Column(precision = 12, scale = 3)
    private BigDecimal compraMinCantidad;

    @Column(precision = 12, scale = 3)
    private BigDecimal lleva;

    @Column(precision = 12, scale = 3)
    private BigDecimal paga;

    private Integer maxUsosTotal;

    private Integer maxUsosCliente;

    @Column(nullable = false)
    @Builder.Default
    private Integer usosActual = 0;

    @Column(nullable = false)
    @Builder.Default
    private Instant vigenciaDesde = Instant.now();

    private Instant vigenciaHasta;

    /** SMALLINT[] en PostgreSQL: 1=Lunes … 7=Domingo. Default 1..7 (todos). */
    @Column(name = "dias_semana", nullable = false)
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Builder.Default
    private List<Short> diasSemana = List.of((short) 1, (short) 2, (short) 3, (short) 4, (short) 5, (short) 6, (short) 7);

    private LocalTime horaDesde;

    private LocalTime horaHasta;

    @Column(nullable = false)
    @Builder.Default
    private Boolean soloMayoristas = false;

    @Column(nullable = false, length = 12)
    @Builder.Default
    private String estado = "ACTIVA";

    /** Quien dio de alta la promoción. Inmutable: siempre se conserva el autor original. */
    @Column(name = "usuario_id", nullable = false)
    private Integer usuarioId;

    @Column(name = "creado_en", nullable = false)
    @Builder.Default
    private Instant creadoEn = Instant.now();
}