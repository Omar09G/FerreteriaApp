package mx.ferreteria.api.ven.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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
                return toResponsePage(cuentaRepo.filtrar(null, estado, desde, hasta, pageable));
        }

        @Transactional(readOnly = true)
        public Page<VenDtos.CuentaCobrarResponse> listCuentasByCliente(Long clienteId,
                        LocalDate desde, LocalDate hasta, String estado, Pageable pageable) {
                return toResponsePage(cuentaRepo.filtrar(clienteId, estado, desde, hasta, pageable));
        }

        private Page<VenDtos.CuentaCobrarResponse> toResponsePage(Page<CuentaCobrar> page) {
                if (page.isEmpty() || page.getContent().size() == 1) {
                        return page.map(this::toCuentaResponse);
                }
                Set<Long> clienteIds = page.getContent().stream().map(CuentaCobrar::getClienteId)
                                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
                Set<Long> ventaIds = page.getContent().stream().map(CuentaCobrar::getVentaId).collect(Collectors.toSet());
                Map<Long, Cliente> clientes = clienteIds.isEmpty() ? Map.of()
                                : clienteRepo.findAllById(clienteIds).stream()
                                                .collect(Collectors.toMap(Cliente::getClienteId, Function.identity()));
                Map<Long, String> ventaFolios = ventaRepo.findAllById(ventaIds).stream()
                                .collect(Collectors.toMap(mx.ferreteria.api.ven.entity.Venta::getVentaId,
                                                mx.ferreteria.api.ven.entity.Venta::getFolio));
                List<Long> cuentaIds = page.getContent().stream().map(CuentaCobrar::getCuentaCobrarId).toList();
                Map<Long, List<mx.ferreteria.api.ven.entity.PagoCliente>> pagosByCuenta = cuentaIds.isEmpty() ? Map.of()
                                : pagoRepo.findByCuentaCobrarIdIn(cuentaIds).stream()
                                                .collect(Collectors.groupingBy(
                                                                mx.ferreteria.api.ven.entity.PagoCliente::getCuentaCobrarId));
                List<VenDtos.CuentaCobrarResponse> content = page.getContent().stream().map(cc -> {
                        String clienteNombre = cc.getClienteId() == null ? null
                                : clientes.containsKey(cc.getClienteId()) ? clientes.get(cc.getClienteId()).getRazonSocial() : null;
                        String ventaFolio = ventaFolios.get(cc.getVentaId());
                        var pagos = pagosByCuenta.getOrDefault(cc.getCuentaCobrarId(), List.of()).stream()
                                .sorted(java.util.Comparator.comparing(
                                        mx.ferreteria.api.ven.entity.PagoCliente::getFecha,
                                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())).reversed())
                                .map(p -> new VenDtos.PagoResponse(
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
                }).toList();
                return new PageImpl<>(content, page.getPageable(), page.getTotalElements());
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
