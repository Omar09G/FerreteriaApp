package mx.ferreteria.api.ven.repo;

import java.util.Optional;

import mx.ferreteria.api.ven.entity.Venta;

/**
 * Operaciones JPA que requieren {@code EntityManager} directo (no expresables
 * como derived query). Implementacion en {@link VentaRepositoryImpl}.
 * <p>
 * BACK-DIS-002: este contrato evita que {@code VentaService} (capa de aplicacion)
 * importe {@code jakarta.persistence.EntityManager}. La inyeccion del EM vive en
 * el adapter {@code VentaRepositoryImpl}; el service solo conoce la abstraccion.
 */
public interface VentaRepositoryCustom {

    /**
     * Recarga una Venta descartando cualquier copia cacheada en el
     * PersistenceContext, y devuelve la instancia recien cargada con todos los
     * campos recalculados por triggers/columnas GENERATED de la BD.
     * <p>
     * Se usa tras {@code ventaRepo.flush()} en el flujo de checkout, donde los
     * triggers de folio y totales ya dispararon pero Hibernate aun ve los
     * ceros de la entidad inicial.
     *
     * @param ventaId id de la venta a recargar
     * @return Venta con totales actualizados
     */
    Optional<Venta> reloadAfterTriggers(Long ventaId);
}
