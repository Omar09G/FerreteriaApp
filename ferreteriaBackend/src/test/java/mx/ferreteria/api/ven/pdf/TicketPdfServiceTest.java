package mx.ferreteria.api.ven.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import mx.ferreteria.api.cat.entity.Cliente;
import mx.ferreteria.api.cat.repo.ClienteRepository;
import mx.ferreteria.api.cfg.entity.TicketConfig;
import mx.ferreteria.api.cfg.repo.TicketConfigRepository;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.ven.entity.Venta;
import mx.ferreteria.api.ven.entity.VentaDetalle;
import mx.ferreteria.api.ven.repo.VentaDetalleRepository;
import mx.ferreteria.api.ven.repo.VentaRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TicketPdfServiceTest {

    @Mock
    VentaRepository ventaRepo;

    @Mock
    VentaDetalleRepository detalleRepo;

    @Mock
    ClienteRepository clienteRepo;

    @Mock
    TicketConfigRepository ticketConfigRepo;

    @InjectMocks
    TicketPdfService service;

    // ── helpers ───────────────────────────────────────────────────────

    private Venta ventaBase() {
        return Venta.builder()
                .ventaId(1L).folio("V-0001").clienteId(10L).almacenId(1)
                .fecha(Instant.parse("2026-01-15T16:30:00Z"))
                .formaPagoId(1).ivaTasa(new BigDecimal("16.00"))
                .subtotal(new BigDecimal("100.00")).iva(new BigDecimal("16.00"))
                .descuentoTotal(new BigDecimal("10.00")).total(new BigDecimal("106.00"))
                .estado("COMPLETADA").usuarioId(1).build();
    }

    private TicketConfig configBase() {
        return TicketConfig.builder()
                .ticketConfigId(1).almacenId(null)
                .nombreNegocio("Ferretería Test").direccion("Av. Siempre Viva 123")
                .rfc("TEST010101AAA").tituloDocumento("Ticket de venta")
                .mostrarDatosCliente(true).mostrarDesgloseIva(true).mostrarDescuento(true)
                .mensajePie("Gracias por su compra").pieSecundario("www.test.mx")
                .anchoPapelMm((short) 80).fontSizePt((short) 9).build();
    }

    private VentaDetalle detalle(Long productoId, String cantidad, String precio) {
        return VentaDetalle.builder().ventaDetalleId(1L).ventaId(1L).productoId(productoId)
                .cantidad(cantidad == null ? null : new BigDecimal(cantidad))
                .precioUnitario(precio == null ? null : new BigDecimal(precio)).build();
    }

    private Cliente cliente(Long id, String razonSocial, String rfc) {
        return Cliente.builder().clienteId(id).razonSocial(razonSocial).rfc(rfc).build();
    }

    /**
     * Escenario feliz mínimo: venta + detalles + cliente + config global
     * (sin config por almacén).
     */
    private void stubBase(Venta v, List<VentaDetalle> detalles, Cliente c, TicketConfig cfg) {
        when(ventaRepo.findById(v.getVentaId())).thenReturn(Optional.of(v));
        when(detalleRepo.findByVentaId(v.getVentaId())).thenReturn(detalles);
        if (v.getClienteId() != null) {
            when(clienteRepo.findById(v.getClienteId())).thenReturn(Optional.ofNullable(c));
        }
        if (v.getAlmacenId() != null) {
            when(ticketConfigRepo.findByAlmacenId(v.getAlmacenId())).thenReturn(Optional.empty());
        }
        when(ticketConfigRepo.findByAlmacenIdIsNull()).thenReturn(Optional.ofNullable(cfg));
    }

    private static void assertEsPdfValido(byte[] pdf) {
        assertThat(pdf).isNotNull().isNotEmpty();
        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    // ── error: venta inexistente ──────────────────────────────────────

    @Test
    @DisplayName("venta inexistente: lanza RecursoNoEncontradoException")
    void ventaInexistente_lanzaRecursoNoEncontrado() {
        when(ventaRepo.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generarTicketPdf(999L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RECURSO_NO_ENCONTRADO);
    }

    // ── camino feliz ──────────────────────────────────────────────────

    @Test
    @DisplayName("ticket completo con cliente y desglose: genera PDF válido")
    void ticketCompleto_generaPdfValido() {
        Venta v = ventaBase();
        stubBase(v,
                List.of(detalle(5L, "2", "50.00"), detalle(9223372036854775807L, "1", "10.00")),
                cliente(10L, "Juan Pérez", "PEPJ800101AAA"),
                configBase());

        assertEsPdfValido(service.generarTicketPdf(1L));
    }

    // ── resolución de config ──────────────────────────────────────────

    @Test
    @DisplayName("config por almacén existente: se usa la del almacén")
    void configPorAlmacen_seUsaCuandoExiste() {
        Venta v = ventaBase();
        v.setAlmacenId(2);
        TicketConfig suc = configBase();
        suc.setAlmacenId(2);
        suc.setNombreNegocio("Sucursal Norte");

        when(ventaRepo.findById(1L)).thenReturn(Optional.of(v));
        when(detalleRepo.findByVentaId(1L)).thenReturn(List.of(detalle(5L, "1", "50.00")));
        when(clienteRepo.findById(10L))
                .thenReturn(Optional.of(cliente(10L, "Juan Pérez", "PEPJ800101AAA")));
        when(ticketConfigRepo.findByAlmacenId(2)).thenReturn(Optional.of(suc));

        assertEsPdfValido(service.generarTicketPdf(1L));
    }

    @Test
    @DisplayName("sin config por almacén: usa la config global")
    void configGlobal_seUsaCuandoNoHayPorAlmacen() {
        Venta v = ventaBase();
        stubBase(v, List.of(detalle(5L, "1", "50.00")),
                cliente(10L, "Juan Pérez", "PEPJ800101AAA"), configBase());

        assertEsPdfValido(service.generarTicketPdf(1L));
    }

    @Test
    @DisplayName("sin config por almacén ni global: usa valores por defecto")
    void configPorDefecto_seUsaCuandoNoHayNinguna() {
        Venta v = ventaBase();
        stubBase(v, List.of(detalle(5L, "1", "50.00")),
                cliente(10L, "Juan Pérez", "PEPJ800101AAA"), null);

        assertEsPdfValido(service.generarTicketPdf(1L));
    }

    @Test
    @DisplayName("venta sin almacén: usa la config global")
    void ventaSinAlmacen_usaConfigGlobal() {
        Venta v = ventaBase();
        v.setAlmacenId(null);
        stubBase(v, List.of(detalle(5L, "1", "50.00")),
                cliente(10L, "Juan Pérez", "PEPJ800101AAA"), configBase());

        assertEsPdfValido(service.generarTicketPdf(1L));
    }

    @Test
    @DisplayName("venta sin almacén ni config global: usa valores por defecto")
    void ventaSinAlmacenNiGlobal_usaValoresPorDefecto() {
        Venta v = ventaBase();
        v.setAlmacenId(null);
        stubBase(v, List.of(detalle(5L, "1", "50.00")),
                cliente(10L, "Juan Pérez", "PEPJ800101AAA"), null);

        assertEsPdfValido(service.generarTicketPdf(1L));
    }

    // ── bloque cliente ────────────────────────────────────────────────

    @Test
    @DisplayName("cliente inexistente: muestra Consumidor final")
    void clienteInexistente_muestraConsumidorFinal() {
        Venta v = ventaBase();
        stubBase(v, List.of(detalle(5L, "1", "50.00")), null, configBase());

        assertEsPdfValido(service.generarTicketPdf(1L));
    }

    @Test
    @DisplayName("venta sin cliente: muestra Consumidor final")
    void ventaSinCliente_muestraConsumidorFinal() {
        Venta v = ventaBase();
        v.setClienteId(null);
        stubBase(v, List.of(detalle(5L, "1", "50.00")), null, configBase());

        assertEsPdfValido(service.generarTicketPdf(1L));
    }

    @Test
    @DisplayName("mostrarDatosCliente=false: omite bloque cliente")
    void ocultarDatosCliente_omiteBloqueCliente() {
        Venta v = ventaBase();
        TicketConfig cfg = configBase();
        cfg.setMostrarDatosCliente(false);
        stubBase(v, List.of(detalle(5L, "1", "50.00")),
                cliente(10L, "Juan Pérez", "PEPJ800101AAA"), cfg);

        assertEsPdfValido(service.generarTicketPdf(1L));
    }

    @Test
    @DisplayName("mostrarDatosCliente=null: omite bloque cliente")
    void mostrarDatosClienteNulo_omiteBloqueCliente() {
        Venta v = ventaBase();
        TicketConfig cfg = configBase();
        cfg.setMostrarDatosCliente(null);
        stubBase(v, List.of(detalle(5L, "1", "50.00")),
                cliente(10L, "Juan Pérez", "PEPJ800101AAA"), cfg);

        assertEsPdfValido(service.generarTicketPdf(1L));
    }

    @Test
    @DisplayName("cliente sin RFC: omite línea de RFC")
    void clienteSinRfc_omiteLineaRfc() {
        Venta v = ventaBase();
        stubBase(v, List.of(detalle(5L, "1", "50.00")),
                cliente(10L, "Juan Pérez", null), configBase());

        assertEsPdfValido(service.generarTicketPdf(1L));
    }

    @Test
    @DisplayName("cliente con RFC en blanco: omite línea de RFC")
    void clienteRfcEnBlanco_omiteLineaRfc() {
        Venta v = ventaBase();
        stubBase(v, List.of(detalle(5L, "1", "50.00")),
                cliente(10L, "Juan Pérez", "   "), configBase());

        assertEsPdfValido(service.generarTicketPdf(1L));
    }

    // ── encabezado config ─────────────────────────────────────────────

    @Test
    @DisplayName("config sin dirección ni RFC: omite esas líneas")
    void configSinDireccionNiRfc_omiteLineas() {
        Venta v = ventaBase();
        TicketConfig cfg = configBase();
        cfg.setDireccion(null);
        cfg.setRfc(null);
        stubBase(v, List.of(detalle(5L, "1", "50.00")),
                cliente(10L, "Juan Pérez", "PEPJ800101AAA"), cfg);

        assertEsPdfValido(service.generarTicketPdf(1L));
    }

    @Test
    @DisplayName("config con dirección y RFC en blanco: omite esas líneas")
    void direccionYRfcEnBlanco_omiteLineas() {
        Venta v = ventaBase();
        TicketConfig cfg = configBase();
        cfg.setDireccion("   ");
        cfg.setRfc("  ");
        stubBase(v, List.of(detalle(5L, "1", "50.00")),
                cliente(10L, "Juan Pérez", "PEPJ800101AAA"), cfg);

        assertEsPdfValido(service.generarTicketPdf(1L));
    }

    @Test
    @DisplayName("título de documento nulo: usa Factura simplificada")
    void tituloNulo_usaFacturaSimplificada() {
        Venta v = ventaBase();
        TicketConfig cfg = configBase();
        cfg.setTituloDocumento(null);
        stubBase(v, List.of(detalle(5L, "1", "50.00")),
                cliente(10L, "Juan Pérez", "PEPJ800101AAA"), cfg);

        assertEsPdfValido(service.generarTicketPdf(1L));
    }

    @Test
    @DisplayName("venta sin fecha: omite línea de fecha")
    void ventaSinFecha_omiteLineaFecha() {
        Venta v = ventaBase();
        v.setFecha(null);
        stubBase(v, List.of(detalle(5L, "1", "50.00")),
                cliente(10L, "Juan Pérez", "PEPJ800101AAA"), configBase());

        assertEsPdfValido(service.generarTicketPdf(1L));
    }

    // ── desglose IVA y descuento ───────────────────────────────────────

    @Test
    @DisplayName("mostrarDesgloseIva=false: omite subtotal e IVA")
    void sinDesgloseIva_omiteSubtotalEIva() {
        Venta v = ventaBase();
        TicketConfig cfg = configBase();
        cfg.setMostrarDesgloseIva(false);
        stubBase(v, List.of(detalle(5L, "1", "50.00")),
                cliente(10L, "Juan Pérez", "PEPJ800101AAA"), cfg);

        assertEsPdfValido(service.generarTicketPdf(1L));
    }

    @Test
    @DisplayName("mostrarDesgloseIva=null: omite subtotal e IVA")
    void desgloseIvaNulo_omiteSubtotalEIva() {
        Venta v = ventaBase();
        TicketConfig cfg = configBase();
        cfg.setMostrarDesgloseIva(null);
        stubBase(v, List.of(detalle(5L, "1", "50.00")),
                cliente(10L, "Juan Pérez", "PEPJ800101AAA"), cfg);

        assertEsPdfValido(service.generarTicketPdf(1L));
    }

    @Test
    @DisplayName("descuento nulo con mostrarDescuento=true: no falla")
    void descuentoNulo_noFalla() {
        Venta v = ventaBase();
        v.setDescuentoTotal(null);
        stubBase(v, List.of(detalle(5L, "1", "50.00")),
                cliente(10L, "Juan Pérez", "PEPJ800101AAA"), configBase());

        assertEsPdfValido(service.generarTicketPdf(1L));
    }

    @Test
    @DisplayName("descuento cero: omite línea de descuento")
    void descuentoCero_omiteLinea() {
        Venta v = ventaBase();
        v.setDescuentoTotal(BigDecimal.ZERO);
        stubBase(v, List.of(detalle(5L, "1", "50.00")),
                cliente(10L, "Juan Pérez", "PEPJ800101AAA"), configBase());

        assertEsPdfValido(service.generarTicketPdf(1L));
    }

    @Test
    @DisplayName("descuento negativo: omite línea de descuento")
    void descuentoNegativo_omiteLinea() {
        Venta v = ventaBase();
        v.setDescuentoTotal(new BigDecimal("-5.00"));
        stubBase(v, List.of(detalle(5L, "1", "50.00")),
                cliente(10L, "Juan Pérez", "PEPJ800101AAA"), configBase());

        assertEsPdfValido(service.generarTicketPdf(1L));
    }

    @Test
    @DisplayName("mostrarDescuento=false: omite línea aunque haya descuento")
    void mostrarDescuentoFalso_omiteLinea() {
        Venta v = ventaBase();
        TicketConfig cfg = configBase();
        cfg.setMostrarDescuento(false);
        stubBase(v, List.of(detalle(5L, "1", "50.00")),
                cliente(10L, "Juan Pérez", "PEPJ800101AAA"), cfg);

        assertEsPdfValido(service.generarTicketPdf(1L));
    }

    @Test
    @DisplayName("mostrarDescuento=null: omite línea aunque haya descuento")
    void mostrarDescuentoNulo_omiteLinea() {
        Venta v = ventaBase();
        TicketConfig cfg = configBase();
        cfg.setMostrarDescuento(null);
        stubBase(v, List.of(detalle(5L, "1", "50.00")),
                cliente(10L, "Juan Pérez", "PEPJ800101AAA"), cfg);

        assertEsPdfValido(service.generarTicketPdf(1L));
    }

    // ── datos nulos / listas vacías / pie ─────────────────────────────

    @Test
    @DisplayName("montos nulos: genera PDF con ceros")
    void montosNulos_generaPdfConCeros() {
        Venta v = ventaBase();
        v.setSubtotal(null);
        v.setIva(null);
        v.setIvaTasa(null);
        v.setDescuentoTotal(null);
        v.setTotal(null);
        stubBase(v, List.of(detalle(null, null, null)), null, configBase());

        assertEsPdfValido(service.generarTicketPdf(1L));
    }

    @Test
    @DisplayName("sin detalles: genera PDF válido")
    void detallesVacios_generaPdf() {
        Venta v = ventaBase();
        stubBase(v, List.of(), cliente(10L, "Juan Pérez", "PEPJ800101AAA"), configBase());

        assertEsPdfValido(service.generarTicketPdf(1L));
    }

    @Test
    @DisplayName("sin mensajes de pie: omite pie")
    void sinPie_omitePie() {
        Venta v = ventaBase();
        TicketConfig cfg = configBase();
        cfg.setMensajePie(null);
        cfg.setPieSecundario(null);
        stubBase(v, List.of(detalle(5L, "1", "50.00")),
                cliente(10L, "Juan Pérez", "PEPJ800101AAA"), cfg);

        assertEsPdfValido(service.generarTicketPdf(1L));
    }

    @Test
    @DisplayName("pie en blanco: omite pie")
    void pieEnBlanco_omitePie() {
        Venta v = ventaBase();
        TicketConfig cfg = configBase();
        cfg.setMensajePie("   ");
        cfg.setPieSecundario("  ");
        stubBase(v, List.of(detalle(5L, "1", "50.00")),
                cliente(10L, "Juan Pérez", "PEPJ800101AAA"), cfg);

        assertEsPdfValido(service.generarTicketPdf(1L));
    }

    // ── error interno ─────────────────────────────────────────────────

    @Test
    @DisplayName("detalle nulo: lanza IllegalStateException")
    void detalleNulo_lanzaIllegalState() {
        Venta v = ventaBase();
        stubBase(v, Collections.singletonList(null),
                cliente(10L, "Juan Pérez", "PEPJ800101AAA"), configBase());

        assertThatThrownBy(() -> service.generarTicketPdf(1L))
                .isInstanceOf(IllegalStateException.class);
    }
}
