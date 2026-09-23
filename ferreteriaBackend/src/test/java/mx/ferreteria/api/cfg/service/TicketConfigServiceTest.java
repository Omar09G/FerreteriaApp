package mx.ferreteria.api.cfg.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

import mx.ferreteria.api.cfg.dto.TicketConfigDtos.TicketConfigRequest;
import mx.ferreteria.api.cfg.dto.TicketConfigDtos.TicketConfigResponse;
import mx.ferreteria.api.cfg.entity.TicketConfig;
import mx.ferreteria.api.cfg.repo.TicketConfigRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TicketConfigServiceTest {

    @Mock
    TicketConfigRepository repo;

    @InjectMocks
    TicketConfigService service;

    private TicketConfig persisted(Integer almacenId) {
        return TicketConfig.builder()
                .ticketConfigId(1).almacenId(almacenId)
                .logotipoUrl("https://x/logo.png").mostrarLogotipo(true)
                .nombreNegocio("Negocio").direccion("Calle 1").cp("06600")
                .rfc("MELA830504H17").telefono("5551234").email("a@b.com")
                .sitioWeb("https://negocio.mx").tituloDocumento("Ticket")
                .mostrarDatosCliente(true).mostrarNumeroFactura(true)
                .mostrarCaja(true).mostrarFechaHora(true).mostrarVendedor(true)
                .mostrarDesgloseIva(true).mostrarDescuento(true).mostrarCambio(true)
                .mensajePie("Gracias").pieSecundario("Vuelva pronto")
                .anchoPapelMm((short) 80).fontSizePt((short) 9)
                .actualizadoEn(Instant.parse("2026-01-01T00:00:00Z")).actualizadoPor(7)
                .build();
    }

    private TicketConfigRequest fullRequest(Integer almacenId) {
        return new TicketConfigRequest(
                "https://y/nuevo.png", false, "  Mi Negocio  ", "Av. 2", "44100",
                "mela830504h17", "3339876", "nuevo@negocio.mx", "https://nuevo.mx",
                "  Factura  ",
                false, false, false, false, false, false, false, false,
                "Nuevo pie", "Nuevo pie 2",
                (short) 58, (short) 12, almacenId);
    }

    private TicketConfigRequest emptyRequest(Integer almacenId) {
        return new TicketConfigRequest(
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, almacenId);
    }

    private void echoSave() {
        when(repo.save(any(TicketConfig.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ── get ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("get con config de almacen: retorna la especifica")
    void get_conConfigDeAlmacen() {
        when(repo.findByAlmacenId(5)).thenReturn(Optional.of(persisted(5)));

        TicketConfigResponse resp = service.get(5);

        assertThat(resp.almacenId()).isEqualTo(5);
        assertThat(resp.nombreNegocio()).isEqualTo("Negocio");
        assertThat(resp.actualizadoEn()).isEqualTo("2026-01-01T00:00:00Z");
    }

    @Test
    @DisplayName("get sin config de almacen: fallback a global")
    void get_fallbackGlobal() {
        when(repo.findByAlmacenId(5)).thenReturn(Optional.empty());
        when(repo.findByAlmacenIdIsNull()).thenReturn(Optional.of(persisted(null)));

        TicketConfigResponse resp = service.get(5);

        assertThat(resp.almacenId()).isNull();
        assertThat(resp.nombreNegocio()).isEqualTo("Negocio");
    }

    @Test
    @DisplayName("get sin ninguna config: retorna default")
    void get_default() {
        when(repo.findByAlmacenId(5)).thenReturn(Optional.empty());
        when(repo.findByAlmacenIdIsNull()).thenReturn(Optional.empty());

        TicketConfigResponse resp = service.get(5);

        assertThat(resp.nombreNegocio()).isEqualTo("Ferretería El Tornillo Feliz");
        assertThat(resp.tituloDocumento()).isEqualTo("Factura simplificada");
        assertThat(resp.anchoPapelMm()).isEqualTo((short) 80);
        assertThat(resp.fontSizePt()).isEqualTo((short) 9);
        assertThat(resp.mensajePie()).isEqualTo("30 DÍAS PARA DEVOLUCIONES O CAMBIOS");
        assertThat(resp.actualizadoEn()).isNotNull();
    }

    @Test
    @DisplayName("get con almacen null: usa global sin buscar por almacen")
    void get_almacenNullConGlobal() {
        when(repo.findByAlmacenIdIsNull()).thenReturn(Optional.of(persisted(null)));

        TicketConfigResponse resp = service.get(null);

        assertThat(resp.almacenId()).isNull();
        verify(repo, never()).findByAlmacenId(any());
    }

    @Test
    @DisplayName("get con almacen null y sin global: retorna default")
    void get_almacenNullSinGlobal() {
        when(repo.findByAlmacenIdIsNull()).thenReturn(Optional.empty());

        TicketConfigResponse resp = service.get(null);

        assertThat(resp.nombreNegocio()).isEqualTo("Ferretería El Tornillo Feliz");
    }

    @Test
    @DisplayName("get mapea actualizadoEn null a null")
    void get_actualizadoEnNull() {
        TicketConfig sinFecha = persisted(5);
        sinFecha.setActualizadoEn(null);
        when(repo.findByAlmacenId(5)).thenReturn(Optional.of(sinFecha));

        assertThat(service.get(5).actualizadoEn()).isNull();
    }

    // ── upsert ────────────────────────────────────────────────────────

    @Test
    @DisplayName("upsert global existente: aplica todos los campos, trim y RFC en mayusculas")
    void upsert_globalExistente_aplicaTodo() {
        when(repo.findByAlmacenIdIsNull()).thenReturn(Optional.of(persisted(null)));
        echoSave();

        TicketConfigResponse resp = service.upsert(fullRequest(null));

        assertThat(resp.nombreNegocio()).isEqualTo("Mi Negocio");
        assertThat(resp.tituloDocumento()).isEqualTo("Factura");
        assertThat(resp.rfc()).isEqualTo("MELA830504H17");
        assertThat(resp.logotipoUrl()).isEqualTo("https://y/nuevo.png");
        assertThat(resp.direccion()).isEqualTo("Av. 2");
        assertThat(resp.cp()).isEqualTo("44100");
        assertThat(resp.telefono()).isEqualTo("3339876");
        assertThat(resp.email()).isEqualTo("nuevo@negocio.mx");
        assertThat(resp.sitioWeb()).isEqualTo("https://nuevo.mx");
        assertThat(resp.mensajePie()).isEqualTo("Nuevo pie");
        assertThat(resp.pieSecundario()).isEqualTo("Nuevo pie 2");
        assertThat(resp.mostrarLogotipo()).isFalse();
        assertThat(resp.mostrarDatosCliente()).isFalse();
        assertThat(resp.mostrarNumeroFactura()).isFalse();
        assertThat(resp.mostrarCaja()).isFalse();
        assertThat(resp.mostrarFechaHora()).isFalse();
        assertThat(resp.mostrarVendedor()).isFalse();
        assertThat(resp.mostrarDesgloseIva()).isFalse();
        assertThat(resp.mostrarDescuento()).isFalse();
        assertThat(resp.mostrarCambio()).isFalse();
        assertThat(resp.anchoPapelMm()).isEqualTo((short) 58);
        assertThat(resp.fontSizePt()).isEqualTo((short) 12);
        // Sin autenticacion UserPrincipal.actual() es SYSTEM (usuarioId 0)
        assertThat(resp.actualizadoPor()).isZero();
        verify(repo).save(any(TicketConfig.class));
    }

    @Test
    @DisplayName("upsert global nuevo: crea desde default cuando no existe")
    void upsert_globalNuevo_creaDefault() {
        when(repo.findByAlmacenIdIsNull()).thenReturn(Optional.empty());
        echoSave();

        TicketConfigResponse resp = service.upsert(emptyRequest(null));

        assertThat(resp.almacenId()).isNull();
        assertThat(resp.nombreNegocio()).isEqualTo("Ferretería El Tornillo Feliz");
        assertThat(resp.tituloDocumento()).isEqualTo("Factura simplificada");
        assertThat(resp.anchoPapelMm()).isEqualTo((short) 80);
        verify(repo).save(any(TicketConfig.class));
    }

    @Test
    @DisplayName("upsert almacen existente: actualiza")
    void upsert_almacenExistente() {
        when(repo.findByAlmacenId(5)).thenReturn(Optional.of(persisted(5)));
        echoSave();

        TicketConfigResponse resp = service.upsert(fullRequest(5));

        assertThat(resp.almacenId()).isEqualTo(5);
        assertThat(resp.nombreNegocio()).isEqualTo("Mi Negocio");
        verify(repo).save(any(TicketConfig.class));
    }

    @Test
    @DisplayName("upsert almacen nuevo: crea con almacenId")
    void upsert_almacenNuevo() {
        when(repo.findByAlmacenId(5)).thenReturn(Optional.empty());
        echoSave();

        TicketConfigResponse resp = service.upsert(emptyRequest(5));

        assertThat(resp.almacenId()).isEqualTo(5);
        assertThat(resp.nombreNegocio()).isEqualTo("Ferretería El Tornillo Feliz");
        verify(repo).save(any(TicketConfig.class));
    }

    @Test
    @DisplayName("upsert con strings en blanco: se convierten a null")
    void upsert_blanksToNull() {
        when(repo.findByAlmacenIdIsNull()).thenReturn(Optional.of(persisted(null)));
        echoSave();
        TicketConfigRequest req = new TicketConfigRequest(
                "   ", null, null, "  ", " ", null, "\t", " ", "  ", null,
                null, null, null, null, null, null, null, null, " ", "",
                null, null, null);

        TicketConfigResponse resp = service.upsert(req);

        assertThat(resp.logotipoUrl()).isNull();
        assertThat(resp.direccion()).isNull();
        assertThat(resp.cp()).isNull();
        assertThat(resp.telefono()).isNull();
        assertThat(resp.email()).isNull();
        assertThat(resp.sitioWeb()).isNull();
        assertThat(resp.mensajePie()).isNull();
        assertThat(resp.pieSecundario()).isNull();
        // Los nulos no tocan lo existente
        assertThat(resp.nombreNegocio()).isEqualTo("Negocio");
        assertThat(resp.anchoPapelMm()).isEqualTo((short) 80);
    }

    @Test
    @DisplayName("upsert ignora nombre/titulo en blanco y medidas invalidas (ancho 70, font 5)")
    void upsert_ignoraBlancosEInvalidos() {
        when(repo.findByAlmacenIdIsNull()).thenReturn(Optional.of(persisted(null)));
        echoSave();
        TicketConfigRequest req = new TicketConfigRequest(
                null, null, "   ", null, null, null, null, null, null, "",
                null, null, null, null, null, null, null, null, null, null,
                (short) 70, (short) 5, null);

        TicketConfigResponse resp = service.upsert(req);

        assertThat(resp.nombreNegocio()).isEqualTo("Negocio");
        assertThat(resp.tituloDocumento()).isEqualTo("Ticket");
        assertThat(resp.anchoPapelMm()).isEqualTo((short) 80);
        assertThat(resp.fontSizePt()).isEqualTo((short) 9);
    }

    @Test
    @DisplayName("upsert ignora fontSize mayor a 12")
    void upsert_ignoraFontMayor() {
        when(repo.findByAlmacenIdIsNull()).thenReturn(Optional.of(persisted(null)));
        echoSave();
        TicketConfigRequest req = emptyRequest(null);

        TicketConfigResponse resp = service.upsert(
                new TicketConfigRequest(
                        req.logotipoUrl(), req.mostrarLogotipo(), req.nombreNegocio(),
                        req.direccion(), req.cp(), req.rfc(), req.telefono(), req.email(),
                        req.sitioWeb(), req.tituloDocumento(), req.mostrarDatosCliente(),
                        req.mostrarNumeroFactura(), req.mostrarCaja(), req.mostrarFechaHora(),
                        req.mostrarVendedor(), req.mostrarDesgloseIva(), req.mostrarDescuento(),
                        req.mostrarCambio(), req.mensajePie(), req.pieSecundario(),
                        null, (short) 20, null));

        assertThat(resp.fontSizePt()).isEqualTo((short) 9);
    }

    @Test
    @DisplayName("upsert acepta limites de fontSize (7 y 12) y ancho 80")
    void upsert_aceptaLimites() {
        when(repo.findByAlmacenIdIsNull()).thenReturn(Optional.of(persisted(null)));
        echoSave();
        TicketConfigRequest req = new TicketConfigRequest(
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                (short) 80, (short) 7, null);

        TicketConfigResponse resp = service.upsert(req);

        assertThat(resp.anchoPapelMm()).isEqualTo((short) 80);
        assertThat(resp.fontSizePt()).isEqualTo((short) 7);
    }
}
