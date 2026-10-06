package mx.ferreteria.api.notif.entity;

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

/**
 * Trabajo de notificación pendiente/enviado (ticket PDF o nómina pagada).
 * Polimórfico: (tipo, ref_id) unique. Estado fuera de ven.ventas para no
 * contaminar trg_audit_venta / seg.auditoria.
 */
@Entity
@Table(name = "notificacion_jobs", schema = "notif")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificacionJob {

    public static final String TIPO_VENTA_TICKET = "VENTA_TICKET";
    public static final String TIPO_NOMINA_PAGADA = "NOMINA_PAGADA";
    public static final String TIPO_INFORME_DASHBOARD = "INFORME_DASHBOARD";
    public static final String TIPO_CUENTAS_PAGAR = "CUENTAS_PAGAR";
    public static final String TIPO_COBRANZA = "COBRANZA";
    public static final String TIPO_RENTAS = "RENTAS";
    public static final String TIPO_STOCK_BAJO = "STOCK_BAJO";
    public static final String TIPO_TURNO_ABIERTO = "TURNO_ABIERTO";
    public static final String REF_VENTA = "VENTA";
    public static final String REF_NOMINA = "NOMINA";
    public static final String REF_INFORME = "INFORME";
    public static final String REF_CUENTAS = "CUENTAS";
    public static final String REF_COBRANZA = "COBRANZA";
    public static final String REF_RENTAS = "RENTAS";
    public static final String REF_STOCK = "STOCK";
    public static final String REF_TURNO = "TURNO";

    public static final String ESTADO_PENDIENTE = "PENDIENTE";
    public static final String ESTADO_PROCESANDO = "PROCESANDO";
    public static final String ESTADO_ENVIADA = "ENVIADA";
    public static final String ESTADO_ERROR = "ERROR";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long jobId;

    @Column(nullable = false, length = 32)
    private String tipo;

    @Column(name = "ref_tipo", nullable = false, length = 16)
    private String refTipo;

    @Column(name = "ref_id", nullable = false)
    private Long refId;

    @Column(nullable = false, length = 16)
    @Builder.Default
    private String estado = ESTADO_PENDIENTE;

    @Column(name = "pdf_url")
    private String pdfUrl;

    @Column(nullable = false)
    @Builder.Default
    private Integer intentos = 0;

    @Column(name = "ultimo_error")
    private String ultimoError;

    @Column(name = "creado_en", nullable = false)
    @Builder.Default
    private Instant creadoEn = Instant.now();

    @Column(name = "enviado_en")
    private Instant enviadoEn;
}
