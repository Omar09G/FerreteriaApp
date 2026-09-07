package mx.ferreteria.api.ven.repo;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import mx.ferreteria.api.ven.entity.Venta;

/**
 * Implementacion JPA de {@link VentaRepositoryCustom}. Es el UNICO lugar del
 * paquete donde se inyecta {@link EntityManager} (hexagonal: la inyeccion vive
 * en el adapter, no en el service).
 */
@Repository
public class VentaRepositoryImpl implements VentaRepositoryCustom {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Optional<Venta> reloadAfterTriggers(Long ventaId) {
        // em.clear() evicta todas las entidades del PersistenceContext (incluidos
        // los VentaDetalle recien guardados con total_linea=0) para que el
        // findById posterior ejecute SQL fresco y refleje los valores calculados
        // por los triggers de la BD (folio, totales de cabecera, total_linea por
        // detalle). Sin esto Hibernate devolveria los totales en cero porque las
        // entidades en memoria prevalecen sobre la fila persistida.
        em.clear();
        return Optional.ofNullable(em.find(Venta.class, ventaId));
    }
}
