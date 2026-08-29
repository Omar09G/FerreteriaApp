package mx.ferreteria.api.seg.service;

import java.time.Instant;
import java.util.List;

/**
 * Puerta de persistencia para el listado de auditoría. Implementación JDBC
 * en {@code seg.repo.AuditoriaRepository}. Filtros opcionales: todos null =
 * últimas inserciones, ordenadas por id descendente.
 */
public interface AuditoriaGateway {

    /** Filtro de búsqueda. null en cualquier campo = sin restricción. */
    record Filtro(
            String esquema,
            String tabla,
            String accion,
            String usuario,
            Long registroId,
            Instant desde,
            Instant hasta,
            String texto,
            int limit,
            int offset) { }

    record AuditoriaRow(
            Long auditoriaId,
            String esquema,
            String tabla,
            Long registroId,
            String accion,
            String datosAnteriores,
            String datosNuevos,
            Integer usuarioId,
            String usuario,
            Instant creadoEn) { }

    record TablaRow(String esquema, String tabla) { }

    List<AuditoriaRow> buscar(Filtro filtro);

    long contar(Filtro filtro);

    List<TablaRow> tablas();
}