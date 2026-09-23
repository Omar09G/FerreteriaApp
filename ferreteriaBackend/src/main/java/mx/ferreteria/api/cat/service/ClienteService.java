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

@Service
@RequiredArgsConstructor
@Transactional
public class ClienteService {

    private final ClienteRepository repo;
    private final CiudadRepository ciudadRepo;
    private final CreditoPort creditoPort;

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
                .esMayorista(Boolean.TRUE.equals(req.esMayorista()))
                .fotoUrl(req.fotoUrl())
                .build();
        Cliente saved = repo.save(entity);
        // Auto-creacion de linea de credito si hay limite>0 (independiente de
        // esMayorista). El adapter en ven implementa la regla (rompe ciclo cat→ven).
        creditoPort.sincronizarLinea(saved.getClienteId(), saved.getLimiteCredito(),
                saved.getDiasCredito());
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
        if (req.fotoUrl() != null) {
            entity.setFotoUrl(req.fotoUrl().isBlank() ? null : req.fotoUrl());
        }
        Cliente saved = repo.save(entity);
        creditoPort.sincronizarLinea(saved.getClienteId(), saved.getLimiteCredito(),
                saved.getDiasCredito());
        return toResponse(saved);
    }

    public void deactivate(Long id) {
        Cliente entity = repo.findById(id).orElseThrow(
                () -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        entity.setActivo(false);
        repo.save(entity);
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
                c.getLimiteCredito(), c.getDiasCredito(), c.getEsMayorista(), c.getActivo(),
                c.getFotoUrl());
    }
}
