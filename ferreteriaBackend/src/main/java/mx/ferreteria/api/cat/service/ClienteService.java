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
import mx.ferreteria.api.cat.entity.Cliente;
import mx.ferreteria.api.cat.repo.ClienteRepository;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.i18n.ErrorCode;

@Service
@RequiredArgsConstructor
@Transactional
public class ClienteService {

    private final ClienteRepository repo;

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
                .telefono(req.telefono())
                .email(req.email())
                .limiteCredito(req.limiteCredito() != null ? req.limiteCredito() : BigDecimal.ZERO)
                .diasCredito(req.diasCredito() != null ? req.diasCredito() : 0)
                .esMayorista(req.esMayorista() != null ? req.esMayorista() : false)
                .build();
        return toResponse(repo.save(entity));
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
        entity.setTelefono(req.telefono());
        entity.setEmail(req.email());
        if (req.limiteCredito() != null) {
            entity.setLimiteCredito(req.limiteCredito());
        }
        if (req.diasCredito() != null) {
            entity.setDiasCredito(req.diasCredito());
        }
        if (req.esMayorista() != null) {
            entity.setEsMayorista(req.esMayorista());
        }
        return toResponse(repo.save(entity));
    }

    public void deactivate(Long id) {
        Cliente entity = repo.findById(id).orElseThrow(
                () -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        entity.setActivo(false);
        repo.save(entity);
    }

    private ClienteResponse toResponse(Cliente c) {
        return new ClienteResponse(
                c.getClienteId(), c.getTipoPersona(), c.getRazonSocial(),
                c.getNombreComercial(), c.getRfc(), c.getTelefono(), c.getEmail(),
                c.getLimiteCredito(), c.getDiasCredito(), c.getEsMayorista());
    }
}
