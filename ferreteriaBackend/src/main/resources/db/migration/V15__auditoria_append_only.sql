-- ============================================================================
-- V15__auditoria_append_only.sql
-- S4 auditoría append-only: seg.auditoria ya tenía REVOKE DELETE (02_tablas/V11);
-- aquí se cierra UPDATE a nivel GRANT y con trigger anti UPDATE/DELETE.
-- Idempotente: REVOKE y CREATE OR REPLACE / DROP TRIGGER IF EXISTS.
-- ============================================================================

REVOKE UPDATE ON seg.auditoria FROM ferreteria_app;

CREATE OR REPLACE FUNCTION seg.fn_auditoria_solo_insert()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'seg.auditoria es append-only' USING ERRCODE = 'P0999'; END $$;

-- SET search_path explícito (DB-SEC-005): evita search_path hijack.
ALTER FUNCTION seg.fn_auditoria_solo_insert() SET search_path = pg_catalog, public, seg;

DROP TRIGGER IF EXISTS trg_auditoria_no_upd_del ON seg.auditoria;
CREATE TRIGGER trg_auditoria_no_upd_del BEFORE UPDATE OR DELETE ON seg.auditoria
FOR EACH ROW EXECUTE FUNCTION seg.fn_auditoria_solo_insert();
