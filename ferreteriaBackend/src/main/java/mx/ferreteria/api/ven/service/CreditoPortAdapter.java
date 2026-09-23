package mx.ferreteria.api.ven.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.service.CreditoPort;
import mx.ferreteria.api.common.security.UserPrincipal;
import mx.ferreteria.api.ven.entity.LineaCredito;
import mx.ferreteria.api.ven.repo.LineaCreditoRepository;

/**
 * Adapter de {@link CreditoPort} (contrato de {@code cat}) sobre el
 * repositorio de {@code ven}. Lógica movida verbatim desde
 * {@code ClienteService}: auto-creación de línea ACTIVA al dar de alta o
 * editar un cliente con límite&gt;0. La arista ven→cat ya existe y es
 * unidireccional, así que no forma ciclo. Corre en la tx del llamador.
 * {@code @Component} (no {@code @Service}) por la convención de naming.
 */
@Component
@RequiredArgsConstructor
public class CreditoPortAdapter implements CreditoPort {

    private final LineaCreditoRepository lineaRepo;

    @Override
    @Transactional
    public void sincronizarLinea(Long clienteId, BigDecimal limiteCredito, Integer diasCredito) {
        if (limiteCredito == null || limiteCredito.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        int dias = diasCredito != null && diasCredito > 0 ? diasCredito : 15;
        // Coerce a rango del trigger (1..365)
        dias = Math.clamp(dias, 1, 365);
        LineaCredito existente = lineaRepo.findByClienteIdAndEstado(clienteId, "ACTIVA").orElse(null);
        int actor = 1;
        try {
            actor = UserPrincipal.actual().usuarioId();
            if (actor == 0) {
                actor = 1;
            }
        } catch (Exception ignored) {
            actor = 1;
        }
        if (existente != null) {
            existente.setMontoAutorizado(limiteCredito);
            existente.setDiasCredito((short) dias);
            lineaRepo.save(existente);
        } else {
            LineaCredito nueva = LineaCredito.builder()
                    .clienteId(clienteId)
                    .montoAutorizado(limiteCredito)
                    .diasCredito((short) dias)
                    .tasaMoratorio(BigDecimal.ZERO)
                    .usuarioAutorizoId(actor)
                    .estado("ACTIVA")
                    .observaciones("Auto-creada desde alta/edicion de cliente")
                    .build();
            lineaRepo.save(nueva);
        }
    }
}
