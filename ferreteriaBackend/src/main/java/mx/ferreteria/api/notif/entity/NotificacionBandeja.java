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
 * Fila de la bandeja de notificaciones en tiempo real (SSE): un registro por
 * destinatario y evento. El usuario conectado la recibe al instante por el
 * stream; el desconectado la ve como contador + historial al entrar.
 * Idempotente por (usuario, tipo, ref): republicar el mismo evento no duplica.
 */
@Entity
@Table(name = "notificacion_bandeja", schema = "notif")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificacionBandeja {

    public static final String TIPO_VENTA_TICKET = "VENTA_TICKET";
    public static final String TIPO_NOMINA_PAGADA = "NOMINA_PAGADA";
    public static final String TIPO_INFORME_DASHBOARD = "INFORME_DASHBOARD";
    public static final String TIPO_CUENTAS_PAGAR = "CUENTAS_PAGAR";
    public static final String TIPO_COBRANZA = "COBRANZA";
    public static final String TIPO_RENTAS = "RENTAS";
    public static final String TIPO_STOCK_BAJO = "STOCK_BAJO";
    public static final String TIPO_TURNO_ABIERTO = "TURNO_ABIERTO";
    public static final String TIPO_VENTA_CANCELADA = "VENTA_CANCELADA";
    public static final String TIPO_COMPRA_CREADA = "COMPRA_CREADA";
    public static final String TIPO_TURNO_APERTURA = "TURNO_APERTURA";
    public static final String TIPO_CORTE_CAJA = "CORTE_CAJA";
    public static final String TIPO_NOMINA_CREADA = "NOMINA_CREADA";
    public static final String TIPO_CHAT_MENSAJE = "CHAT_MENSAJE";

    public static final String REF_VENTA = "VENTA";
    public static final String REF_NOMINA = "NOMINA";
    public static final String REF_INFORME = "INFORME";
    public static final String REF_CUENTAS = "CUENTAS";
    public static final String REF_COBRANZA = "COBRANZA";
    public static final String REF_RENTAS = "RENTAS";
    public static final String REF_STOCK = "STOCK";
    public static final String REF_TURNO = "TURNO";
    public static final String REF_COMPRA = "COMPRA";
    public static final String REF_CORTE = "CORTE";
    public static final String REF_CHAT = "CHAT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long bandejaId;

    @Column(name = "usuario_id", nullable = false)
    private Integer usuarioId;

    @Column(nullable = false, length = 32)
    private String tipo;

    @Column(nullable = false, length = 140)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String detalle;

    @Column(name = "ref_tipo", nullable = false, length = 16)
    private String refTipo;

    @Column(name = "ref_id", nullable = false)
    private Long refId;

    @Column(name = "leida_en")
    private Instant leidaEn;

    @Column(name = "creada_en", nullable = false)
    @Builder.Default
    private Instant creadaEn = Instant.now();
}
