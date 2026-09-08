package mx.ferreteria.api.cat.service;

import java.math.BigDecimal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.dto.CatDtos.ClienteRequest;
import mx.ferreteria.api.cat.dto.CatDtos.ClienteResponse;
import mx.ferreteria.api.cat.entity.Ciudad;
import mx.ferreteria.api.cat.entity.Cliente;
import mx.ferreteria.api.cat.repo.CiudadRepository;
import mx.ferreteria.api.cat.repo.ClienteRepository;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.common.security.UserPrincipal;
import mx.ferreteria.api.ven.entity.LineaCredito;
import mx.ferreteria.api.ven.repo.LineaCreditoRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class ClienteService {

    private final ClienteRepository repo;
    private final CiudadRepository ciudadRepo;
    private final LineaCreditoRepository lineaRepo;

    @Transactional(readOnly = true)
    public Page<ClienteResponse> list(String q, Pageable pageable) {
        Page<Cliente> page = StringUtils.hasText(q)
                ? repo.findByActivoTrueAndRazonSocialContainingIgnoreCase(q, pageable)
                : repo.findByActivoTrue(pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ClienteResponse getById(Long id) {
        Cliente entity = repo.findById(id).orElseThrow(
                () -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        return toResponse(entity);
    }

    public ClienteResponse create(ClienteRequest req) {
        Cliente entity = Cliente.builder()
                .tipoPersona(req.tipoPersona() != null ? req.tipoPersona() : "FISICA")
                .razonSocial(req.razonSocial())
                .nombreComercial(req.nombreComercial())
                .rfc(req.rfc())
                .curp(req.curp())
                .telefono(req.telefono())
                .whatsapp(req.whatsapp())
                .email(req.email())
                .calle(req.calle())
                .colonia(req.colonia())
                .ciudadId(req.ciudadId())
                .cp(req.cp())
                .limiteCredito(req.limiteCredito() != null ? req.limiteCredito() : BigDecimal.ZERO)
                .diasCredito(req.diasCredito() != null ? req.diasCredito() : 0)
                .esMayorista(req.esMayorista() != null ? req.esMayorista() : false)
                .build();
        Cliente saved = repo.save(entity);
        // Auto-creacion de linea de credito si hay limite>0 (independiente de esMayorista)
        autoCrearOActualizarLinea(saved);
        return toResponse(saved);
    }

    public ClienteResponse update(Long id, ClienteRequest req) {
        Cliente entity = repo.findById(id).orElseThrow(
                () -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        if (req.tipoPersona() != null) {
            entity.setTipoPersona(req.tipoPersona());
        }
        entity.setRazonSocial(req.razonSocial());
        entity.setNombreComercial(req.nombreComercial());
        entity.setRfc(req.rfc());
        entity.setCurp(req.curp());
        entity.setTelefono(req.telefono());
        entity.setWhatsapp(req.whatsapp());
        entity.setEmail(req.email());
        entity.setCalle(req.calle());
        entity.setColonia(req.colonia());
        entity.setCiudadId(req.ciudadId());
        entity.setCp(req.cp());
        if (req.limiteCredito() != null) {
            entity.setLimiteCredito(req.limiteCredito());
        }
        if (req.diasCredito() != null) {
            entity.setDiasCredito(req.diasCredito());
        }
        if (req.esMayorista() != null) {
            entity.setEsMayorista(req.esMayorista());
        }
        Cliente saved = repo.save(entity);
        autoCrearOActualizarLinea(saved);
        return toResponse(saved);
    }

    public void deactivate(Long id) {
        Cliente entity = repo.findById(id).orElseThrow(
                () -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        entity.setActivo(false);
        repo.save(entity);
    }

    private void autoCrearOActualizarLinea(Cliente c) {
        BigDecimal limite = c.getLimiteCredito();
        if (limite == null || limite.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        int dias = c.getDiasCredito() != null && c.getDiasCredito() > 0 ? c.getDiasCredito() : 15;
        // Coerce a rango del trigger (1..365)
        dias = Math.max(1, Math.min(dias, 365));
        var existente = lineaRepo.findByClienteIdAndEstado(c.getClienteId(), "ACTIVA").orElse(null);
        int actor = 1;
        try {
            actor = UserPrincipal.actual().usuarioId();
            if (actor == 0) actor = 1;
        } catch (Exception ignored) { actor = 1; }
        if (existente != null) {
            existente.setMontoAutorizado(limite);
            existente.setDiasCredito((short) dias);
            lineaRepo.save(existente);
        } else {
            LineaCredito nueva = LineaCredito.builder()
                    .clienteId(c.getClienteId())
                    .montoAutorizado(limite)
                    .diasCredito((short) dias)
                    .tasaMoratorio(BigDecimal.ZERO)
                    .usuarioAutorizoId(actor)
                    .estado("ACTIVA")
                    .observaciones("Auto-creada desde alta/edicion de cliente")
                    .build();
            lineaRepo.save(nueva);
        }
    }

    private ClienteResponse toResponse(Cliente c) {
        String ciudadNombre = null;
        if (c.getCiudadId() != null) {
            ciudadNombre = ciudadRepo.findById(c.getCiudadId()).map(Ciudad::getNombre).orElse(null);
        }
        return new ClienteResponse(
                c.getClienteId(), c.getTipoPersona(), c.getRazonSocial(),
                c.getNombreComercial(), c.getRfc(), c.getCurp(), c.getRegimenFiscal(),
                c.getTelefono(), c.getWhatsapp(), c.getEmail(),
                c.getCalle(), c.getColonia(), c.getCiudadId(), ciudadNombre, c.getCp(),
                c.getLimiteCredito(), c.getDiasCredito(), c.getEsMayorista(), c.getActivo());
    }
}
