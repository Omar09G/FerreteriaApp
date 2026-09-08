package mx.ferreteria.api.cfg.entity;

import java.time.Instant;

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

@Entity
@Table(name = "ticket_config", schema = "cfg")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ticket_config_id")
    private Integer ticketConfigId;

    @Column(name = "almacen_id", unique = true)
    private Integer almacenId;

    @Column(name = "logotipo_url", columnDefinition = "TEXT")
    private String logotipoUrl;

    @Column(name = "mostrar_logotipo", nullable = false)
    private Boolean mostrarLogotipo;

    @Column(name = "nombre_negocio", nullable = false, length = 180)
    private String nombreNegocio;

    @Column(name = "direccion", length = 250)
    private String direccion;

    @Column(name = "cp", length = 10)
    private String cp;

    @Column(name = "rfc", length = 13)
    private String rfc;

    @Column(name = "telefono", length = 20)
    private String telefono;

    @Column(name = "email", length = 120)
    private String email;

    @Column(name = "sitio_web", length = 120)
    private String sitioWeb;

    @Column(name = "titulo_documento", nullable = false, length = 40)
    private String tituloDocumento;

    @Column(name = "mostrar_datos_cliente", nullable = false)
    private Boolean mostrarDatosCliente;

    @Column(name = "mostrar_numero_factura", nullable = false)
    private Boolean mostrarNumeroFactura;

    @Column(name = "mostrar_caja", nullable = false)
    private Boolean mostrarCaja;

    @Column(name = "mostrar_fecha_hora", nullable = false)
    private Boolean mostrarFechaHora;

    @Column(name = "mostrar_vendedor", nullable = false)
    private Boolean mostrarVendedor;

    @Column(name = "mostrar_desglose_iva", nullable = false)
    private Boolean mostrarDesgloseIva;

    @Column(name = "mostrar_descuento", nullable = false)
    private Boolean mostrarDescuento;

    @Column(name = "mostrar_cambio", nullable = false)
    private Boolean mostrarCambio;

    @Column(name = "mensaje_pie", columnDefinition = "TEXT")
    private String mensajePie;

    @Column(name = "pie_secundario", columnDefinition = "TEXT")
    private String pieSecundario;

    @Column(name = "ancho_papel_mm", nullable = false)
    private Short anchoPapelMm;

    @Column(name = "font_size_pt", nullable = false)
    private Short fontSizePt;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    @Column(name = "actualizado_por")
    private Integer actualizadoPor;
}
