package mx.ferreteria.api.ven.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.entity.Cliente;
import mx.ferreteria.api.cat.entity.Producto;
import mx.ferreteria.api.cat.repo.ClienteRepository;
import mx.ferreteria.api.cat.repo.FormaPagoRepository;
import mx.ferreteria.api.cat.repo.ProductoRepository;
import mx.ferreteria.api.common.error.RecursoNoEncontradoException;
import mx.ferreteria.api.common.error.ReglaNegocioException;
import mx.ferreteria.api.common.i18n.ErrorCode;
import mx.ferreteria.api.common.security.UserPrincipal;
import mx.ferreteria.api.fin.service.CajaService;
import mx.ferreteria.api.inv.entity.Almacen;
import mx.ferreteria.api.inv.repo.AlmacenRepository;
import mx.ferreteria.api.ven.dto.VenDtos;
import mx.ferreteria.api.ven.entity.Renta;
import mx.ferreteria.api.ven.entity.RentaDetalle;
import mx.ferreteria.api.ven.repo.RentaDetalleRepository;
import mx.ferreteria.api.ven.repo.RentaRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class RentaService {

    private final RentaRepository repo;
    private final RentaDetalleRepository detalleRepo;
    private final AlmacenRepository almacenRepo;
    private final ClienteRepository clienteRepo;
    private final ProductoRepository productoRepo;
    private final FormaPagoRepository formaPagoRepo;
    private final CajaService cajaService;

    @Transactional(readOnly = true)
    public Page<VenDtos.RentaResponse> list(String estado, Pageable pageable) {
        Page<Renta> page = (estado != null)
                ? repo.findByEstadoOrderByFechaRentaDesc(estado, pageable)
                : repo.findAll(pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public VenDtos.RentaResponse getById(Long id) {
        Renta r = repo.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        return toResponse(r);
    }

    public VenDtos.RentaResponse create(VenDtos.RentaRequest req) {
        if (req.fechaDevEsperada().isBefore(LocalDate.now())) {
            throw new ReglaNegocioException(ErrorCode.VALOR_INVALIDO);
        }
        formaPagoRepo.findById(req.formaPagoId())
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        Long turnoCajaId = cajaService.resolverTurnoAbierto(req.cajaId(), req.almacenId());
        Renta entity = Renta.builder()
                .clienteId(req.clienteId())
                .almacenId(req.almacenId())
                .fechaRenta(Instant.now())
                .fechaDevEsperada(req.fechaDevEsperada())
                .deposito(req.deposito())
                .costoTotal(BigDecimal.ZERO)
                .usuarioId(UserPrincipal.actual().usuarioId())
                .turnoCajaId(turnoCajaId)
                .formaPagoId(req.formaPagoId())
                .build();
        Renta saved = repo.save(entity);

        for (VenDtos.RentaDetalleRequest d : req.detalles()) {
            RentaDetalle det = RentaDetalle.builder()
                    .rentaId(saved.getRentaId())
                    .productoId(d.productoId())
                    .cantidad(d.cantidad())
                    .costoDia(d.costoDia())
                    .build();
            detalleRepo.save(det);
        }

        repo.flush();
        Renta refreshed = repo.findById(saved.getRentaId()).orElse(saved);
        return toResponse(refreshed);
    }

    public VenDtos.RentaResponse devolver(Long rentaId, VenDtos.RentaDevolucionRequest req) {
        Renta r = repo.findById(rentaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        if (!"ABIERTA".equals(r.getEstado()) && !"VENCIDA".equals(r.getEstado())) {
            throw new ReglaNegocioException(ErrorCode.VALOR_INVALIDO);
        }
        r.setFechaDevReal(Instant.now());
        r.setEstado("DEVUELTA");
        repo.save(r);
        return toResponse(r);
    }

    public VenDtos.RentaResponse cancelar(Long rentaId) {
        Renta r = repo.findById(rentaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.RECURSO_NO_ENCONTRADO));
        if (!"ABIERTA".equals(r.getEstado()) && !"VENCIDA".equals(r.getEstado())) {
            throw new ReglaNegocioException(ErrorCode.VALOR_INVALIDO);
        }
        r.setEstado("CANCELADA");
        repo.save(r);
        return toResponse(r);
    }

    private VenDtos.RentaResponse toResponse(Renta r) {
        String clienteNombre = clienteRepo.findById(r.getClienteId())
                .map(Cliente::getRazonSocial).orElse(null);
        String almacenNombre = almacenRepo.findById(r.getAlmacenId())
                .map(Almacen::getNombre).orElse(null);
        List<VenDtos.RentaDetalleResponse> detalles = detalleRepo.findByRentaId(r.getRentaId())
                .stream().map(d -> {
                    String nombre = productoRepo.findById(d.getProductoId())
                            .map(Producto::getNombre).orElse(null);
                    return new VenDtos.RentaDetalleResponse(
                            d.getProductoId(), nombre,
                            d.getCantidad(), d.getCostoDia(),
                            d.getDiasCobrados(), d.getSubtotal());
                }).toList();
        return new VenDtos.RentaResponse(
                r.getRentaId(), r.getFolio(),
                r.getClienteId(), clienteNombre,
                r.getAlmacenId(), almacenNombre,
                r.getFechaRenta(), r.getFechaDevEsperada(),
                r.getFechaDevReal(),
                r.getDeposito(), r.getCostoTotal(),
                r.getFormaPagoId(), r.getTurnoCajaId(),
                r.getEstado(), r.getUsuarioId(), detalles);
    }
}
