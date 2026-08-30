package mx.ferreteria.api.ven.service;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.entity.Cliente;
import mx.ferreteria.api.cat.repo.ClienteRepository;
import mx.ferreteria.api.ven.dto.VenDtos;
import mx.ferreteria.api.ven.entity.CuentaCobrar;
import mx.ferreteria.api.ven.repo.CuentaCobrarRepository;
import mx.ferreteria.api.ven.repo.PagoClienteRepository;
import mx.ferreteria.api.ven.repo.VentaRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class CreditoService {

        private final CuentaCobrarRepository cuentaRepo;
        private final PagoClienteRepository pagoRepo;
        private final VentaRepository ventaRepo;
        private final ClienteRepository clienteRepo;

        @Transactional(readOnly = true)
        public Page<VenDtos.CuentaCobrarResponse> listCuentas(
                        LocalDate desde, LocalDate hasta, String estado, Pageable pageable) {
                return cuentaRepo.filtrar(null, estado, desde, hasta, pageable)
                                .map(this::toCuentaResponse);
        }

        @Transactional(readOnly = true)
        public Page<VenDtos.CuentaCobrarResponse> listCuentasByCliente(Long clienteId,
                        LocalDate desde, LocalDate hasta, String estado, Pageable pageable) {
                return cuentaRepo.filtrar(clienteId, estado, desde, hasta, pageable)
                                .map(this::toCuentaResponse);
        }

        private VenDtos.CuentaCobrarResponse toCuentaResponse(CuentaCobrar cc) {
                String clienteNombre = null;
                if (cc.getClienteId() != null) {
                        clienteNombre = clienteRepo.findById(cc.getClienteId())
                                        .map(Cliente::getRazonSocial).orElse(null);
                }
                String ventaFolio = ventaRepo.findById(cc.getVentaId())
                                .map(v -> v.getFolio()).orElse(null);
                var pagos = pagoRepo.findByCuentaCobrarIdOrderByFechaDesc(cc.getCuentaCobrarId())
                                .stream().map(p -> new VenDtos.PagoResponse(
                                                p.getPagoClienteId(), p.getFormaPagoId(),
                                                p.getReferencia(), p.getMonto(), p.getFecha()))
                                .toList();
                return new VenDtos.CuentaCobrarResponse(
                                cc.getCuentaCobrarId(), cc.getVentaId(), ventaFolio,
                                cc.getClienteId(), clienteNombre,
                                cc.getMontoTotal(), cc.getMontoPagado(),
                                cc.getMontoTotal().subtract(cc.getMontoPagado()),
                                cc.getFechaVencimiento(), cc.getEstado(), cc.getCreadoEn(),
                                pagos);
        }
}
