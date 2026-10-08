-- Espejo Flyway de ferreteriaDB/migrations/delta_auditoria_promociones.sql (idempotente).
-- En installs nuevos el objeto ya viene en V1__base/V2__parametria regenerados;
-- este archivo cubre BDs existentes que migran por Flyway.

-- ============================================================================
-- DELTA: Disparador de auditoría para ven.promociones (alta/cambio/baja).
-- Aplica a BD existente. El bloque equivalente ya está integrado en
-- scripts/02_tablas.sql para instalaciones nuevas.
-- ============================================================================

DROP TRIGGER IF EXISTS trg_audit_promocion ON ven.promociones;

CREATE TRIGGER trg_audit_promocion
    AFTER INSERT OR UPDATE OR DELETE ON ven.promociones
    FOR EACH ROW
    EXECUTE FUNCTION seg.fn_auditar('promocion_id');