package mx.ferreteria.api.com.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "compras", schema = "com")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Compra {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long compraId;

    @Column(unique = true)
    private String folio;

    @Column(name = "factura_proveedor", length = 50)
    private String facturaProveedor;

    @Column(nullable = false)
    private Integer proveedorId;

    private Long ordenCompraId;

    @Column(nullable = false)
    private Integer almacenId;

    @Column(nullable = false)
    @Builder.Default private Instant fecha = Instant.now();

    @Column(nullable = false)
    private Integer formaPagoId;

    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default private BigDecimal iva = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default private BigDecimal descuentoTotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    @Builder.Default private BigDecimal total = BigDecimal.ZERO;

    @Column(nullable = false, length = 12)
    @Builder.Default private String estado = "RECIBIDA";

    @Column(nullable = false)
    private Integer usuarioId;

    private Long turnoCajaId;

    private String notas;
}