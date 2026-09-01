package mx.ferreteria.api.cat.catalogo;

import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import mx.ferreteria.api.cat.repo.EstadoRepository;
import mx.ferreteria.api.cat.repo.FormaPagoSatRepository;
import mx.ferreteria.api.cat.repo.ImpuestoRepository;
import mx.ferreteria.api.common.error.ValidacionException;
import mx.ferreteria.api.common.i18n.ErrorCode;

/**
 * Resuelve las opciones (dropdown) de campos FK del CRUD de catálogos usando
 * exclusivamente repositorios JPA (sin SQL). El campo FK declara la clave del
 * catálogo de origen en el descriptor; aquí se traduce a su repositorio tipado.
 */
@Service
@RequiredArgsConstructor
public class OpcionesCatalogoService {

    private final EstadoRepository estadoRepo;
    private final ImpuestoRepository impuestoRepo;
    private final FormaPagoSatRepository formaPagoSatRepo;

    /**
     * @return lista de mapas [{clave: .., ...columnas}] para el catálogo de origen.
     */
    public List<OpcionFk> opciones(String claveCatalogoOrigen, List<String> columnas) {
        return switch (claveCatalogoOrigen) {
            case "estados" -> estadoRepo.findAllByOrderByNombre().stream()
                    .map(e -> new OpcionFk(e.getEstadoId(), List.of(e.getNombre())))
                    .toList();
            case "impuestos" -> impuestoRepo.findByActivoTrueOrderByNombre().stream()
                    .map(i -> new OpcionFk(i.getImpuestoId(), List.of(i.getNombre())))
                    .toList();
            case "formas_pago_sat" -> formaPagoSatRepo.findByActivoTrueOrderByClave().stream()
                    .map(f -> new OpcionFk(f.getClave(), List.of(f.getDescripcion())))
                    .toList();
            default -> throw new ValidacionException(ErrorCode.REFERENCIA_INVALIDA, claveCatalogoOrigen);
        };
    }

    public record OpcionFk(Object clave, List<String> texto) { }
}
