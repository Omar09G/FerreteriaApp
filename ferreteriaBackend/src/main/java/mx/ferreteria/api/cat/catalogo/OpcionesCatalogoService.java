package mx.ferreteria.api.cat.catalogo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
                    .map(e -> proyectar(e.getEstadoId(), Map.of("nombre", e.getNombre()), columnas))
                    .toList();
            case "impuestos" -> impuestoRepo.findByActivoTrueOrderByNombre().stream()
                    .map(i -> proyectar(i.getImpuestoId(), Map.of("nombre", i.getNombre()), columnas))
                    .toList();
            case "formas_pago_sat" -> formaPagoSatRepo.findByActivoTrueOrderByClave().stream()
                    .map(f -> proyectar(f.getClave(), Map.of("descripcion", f.getDescripcion()), columnas))
                    .toList();
            default -> throw new ValidacionException(ErrorCode.REFERENCIA_INVALIDA, claveCatalogoOrigen);
        };
    }

    /**
     * Selecciona los textos a mostrar según las columnas pedidas por el
     * descriptor FK ({@code opcionesColumnas}). Sin columnas (null/vacío) se
     * devuelven todos los campos disponibles (comportamiento anterior).
     * Columna desconocida -> 400 REFERENCIA_INVALIDA.
     */
    private static OpcionFk proyectar(Object clave, Map<String, String> campos, List<String> columnas) {
        if (columnas == null || columnas.isEmpty()) {
            return new OpcionFk(clave, List.copyOf(campos.values()));
        }
        List<String> texto = new ArrayList<>(columnas.size());
        for (String col : columnas) {
            if (!campos.containsKey(col)) {
                throw new ValidacionException(ErrorCode.REFERENCIA_INVALIDA, col);
            }
            texto.add(campos.get(col));
        }
        return new OpcionFk(clave, texto);
    }

    public record OpcionFk(Object clave, List<String> texto) { }
}
