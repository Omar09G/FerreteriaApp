package mx.ferreteria.api.cfg.service;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cfg.dto.TicketConfigDtos.TicketConfigRequest;
import mx.ferreteria.api.cfg.dto.TicketConfigDtos.TicketConfigResponse;
import mx.ferreteria.api.cfg.entity.TicketConfig;
import mx.ferreteria.api.cfg.repo.TicketConfigRepository;
import mx.ferreteria.api.common.security.UserPrincipal;

@Service
@RequiredArgsConstructor
public class TicketConfigService {

    private final TicketConfigRepository repo;

    @Transactional(readOnly = true)
    public TicketConfigResponse get(Integer almacenId) {
        Optional<TicketConfig> cfg = Optional.empty();
        if (almacenId != null) {
            cfg = repo.findByAlmacenId(almacenId);
        }
        if (cfg.isEmpty()) {
            cfg = repo.findByAlmacenIdIsNull();
        }
        TicketConfig tc = cfg.orElseGet(this::defaultConfig);
        return toResponse(tc);
    }

    @Transactional
    public TicketConfigResponse upsert(TicketConfigRequest req) {
        Integer almacenId = req.almacenId();
        TicketConfig existing;
        if (almacenId == null) {
            existing = repo.findByAlmacenIdIsNull().orElse(null);
        } else {
            existing = repo.findByAlmacenId(almacenId).orElse(null);
        }
        if (existing == null) {
            existing = defaultConfig();
            existing.setAlmacenId(almacenId);
        }
        apply(existing, req);
        existing.setActualizadoEn(Instant.now());
        existing.setActualizadoPor(UserPrincipal.actual().usuarioId());
        TicketConfig saved = repo.save(existing);
        return toResponse(saved);
    }

    private void apply(TicketConfig e, TicketConfigRequest r) {
        if (r.logotipoUrl() != null) e.setLogotipoUrl(blankToNull(r.logotipoUrl()));
        if (r.mostrarLogotipo() != null) e.setMostrarLogotipo(r.mostrarLogotipo());
        if (r.nombreNegocio() != null && !r.nombreNegocio().isBlank()) e.setNombreNegocio(r.nombreNegocio().trim());
        if (r.direccion() != null) e.setDireccion(blankToNull(r.direccion()));
        if (r.cp() != null) e.setCp(blankToNull(r.cp()));
        if (r.rfc() != null) e.setRfc(blankToNull(r.rfc() != null ? r.rfc().toUpperCase() : null));
        if (r.telefono() != null) e.setTelefono(blankToNull(r.telefono()));
        if (r.email() != null) e.setEmail(blankToNull(r.email()));
        if (r.sitioWeb() != null) e.setSitioWeb(blankToNull(r.sitioWeb()));
        if (r.tituloDocumento() != null && !r.tituloDocumento().isBlank()) e.setTituloDocumento(r.tituloDocumento().trim());
        if (r.mostrarDatosCliente() != null) e.setMostrarDatosCliente(r.mostrarDatosCliente());
        if (r.mostrarNumeroFactura() != null) e.setMostrarNumeroFactura(r.mostrarNumeroFactura());
        if (r.mostrarCaja() != null) e.setMostrarCaja(r.mostrarCaja());
        if (r.mostrarFechaHora() != null) e.setMostrarFechaHora(r.mostrarFechaHora());
        if (r.mostrarVendedor() != null) e.setMostrarVendedor(r.mostrarVendedor());
        if (r.mostrarDesgloseIva() != null) e.setMostrarDesgloseIva(r.mostrarDesgloseIva());
        if (r.mostrarDescuento() != null) e.setMostrarDescuento(r.mostrarDescuento());
        if (r.mostrarCambio() != null) e.setMostrarCambio(r.mostrarCambio());
        if (r.mensajePie() != null) e.setMensajePie(blankToNull(r.mensajePie()));
        if (r.pieSecundario() != null) e.setPieSecundario(blankToNull(r.pieSecundario()));
        if (r.anchoPapelMm() != null && (r.anchoPapelMm() == 58 || r.anchoPapelMm() == 80)) e.setAnchoPapelMm(r.anchoPapelMm());
        if (r.fontSizePt() != null && r.fontSizePt() >= 7 && r.fontSizePt() <= 12) e.setFontSizePt(r.fontSizePt());
    }

    private String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private TicketConfig defaultConfig() {
        return TicketConfig.builder()
                .mostrarLogotipo(false)
                .nombreNegocio("Ferretería El Tornillo Feliz")
                .tituloDocumento("Factura simplificada")
                .mostrarDatosCliente(true)
                .mostrarNumeroFactura(true)
                .mostrarCaja(true)
                .mostrarFechaHora(true)
                .mostrarVendedor(true)
                .mostrarDesgloseIva(true)
                .mostrarDescuento(true)
                .mostrarCambio(true)
                .mensajePie("30 DÍAS PARA DEVOLUCIONES O CAMBIOS")
                .anchoPapelMm((short) 80)
                .fontSizePt((short) 9)
                .actualizadoEn(Instant.now())
                .build();
    }

    private TicketConfigResponse toResponse(TicketConfig e) {
        return new TicketConfigResponse(
                e.getTicketConfigId(),
                e.getAlmacenId(),
                e.getLogotipoUrl(),
                e.getMostrarLogotipo(),
                e.getNombreNegocio(),
                e.getDireccion(),
                e.getCp(),
                e.getRfc(),
                e.getTelefono(),
                e.getEmail(),
                e.getSitioWeb(),
                e.getTituloDocumento(),
                e.getMostrarDatosCliente(),
                e.getMostrarNumeroFactura(),
                e.getMostrarCaja(),
                e.getMostrarFechaHora(),
                e.getMostrarVendedor(),
                e.getMostrarDesgloseIva(),
                e.getMostrarDescuento(),
                e.getMostrarCambio(),
                e.getMensajePie(),
                e.getPieSecundario(),
                e.getAnchoPapelMm(),
                e.getFontSizePt(),
                e.getActualizadoEn() != null ? e.getActualizadoEn().toString() : null,
                e.getActualizadoPor()
        );
    }
}
