package mx.ferreteria.api.ven.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.ven.dto.VenDtos;
import mx.ferreteria.api.ven.entity.PagoCliente;
import mx.ferreteria.api.ven.repo.CuentaCobrarRepository;
import mx.ferreteria.api.ven.repo.PagoClienteRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class PagoService {

    private final PagoClienteRepository repo;
    private final CuentaCobrarRepository cuentaRepo;

    public VenDtos.PagoResponse create(VenDtos.PagoClienteRequest req) {
        cuentaRepo.findById(req.cuentaCobrarId())
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        PagoCliente pago = PagoCliente.builder()
                .cuentaCobrarId(req.cuentaCobrarId())
                .formaPagoId(req.formaPagoId())
                .monto(req.monto())
                .referencia(req.referencia())
                .usuarioId(1)
                .turnoCajaId(req.turnoCajaId())
                .build();
        PagoCliente saved = repo.save(pago);
        return new VenDtos.PagoResponse(
                saved.getPagoClienteId(), saved.getFormaPagoId(),
                saved.getReferencia(), saved.getMonto(), saved.getFecha());
    }
}
