package mx.ferreteria.api.cat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Códigos de barras alternos de un producto (EAN-13, UPC, internos).
 * {@code factor} = unidades que suma cada escaneo (p. ej. código de
 * paquete ×6). Sin trigger de auditoría propio: {@code seg.fn_auditar}
 * castea la PK a BIGINT y los códigos alfanuméricos lo romperían.
 */
@Entity
@Table(name = "producto_codigos_barras", schema = "inv")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductoCodigoBarras {

    @Id
    @Column(name = "codigo_barras", length = 50)
    private String codigoBarras;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(nullable = false, precision = 12, scale = 3)
    @Builder.Default
    private BigDecimal factor = BigDecimal.ONE;
}
