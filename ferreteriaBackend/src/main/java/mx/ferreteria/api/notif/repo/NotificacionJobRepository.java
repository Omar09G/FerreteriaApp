package mx.ferreteria.api.notif.repo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import mx.ferreteria.api.notif.entity.NotificacionJob;

public interface NotificacionJobRepository extends JpaRepository<NotificacionJob, Long> {

    Optional<NotificacionJob> findByTipoAndRefId(String tipo, Long refId);

    List<NotificacionJob> findTop20ByEstadoOrderByCreadoEnAsc(String estado);

    /**
     * Jobs reintentables: PENDIENTE/ERROR más los PROCESANDO rancios (proceso
     * caído entre marcarProcesando y el fin). Sin updated_at se usa creado_en
     * como cota: un PROCESANDO con creado_en viejo es un huérfano seguro.
     * Solo tipos del pipeline broker (ticket/nómina): INFORME_DASHBOARD es
     * síncrono (botón/JOB diario) y su reintento es manual.
     */
    @Query("""
            select j from NotificacionJob j
            where (j.estado in ('PENDIENTE','ERROR')
                   or (j.estado = 'PROCESANDO' and j.creadoEn < :stale))
              and j.intentos < :maxIntentos
              and j.tipo in ('VENTA_TICKET','NOMINA_PAGADA')
            order by j.creadoEn asc
            """)
    List<NotificacionJob> pendientesParaReconciliar(
            @Param("maxIntentos") int maxIntentos, @Param("stale") Instant stale);
}
