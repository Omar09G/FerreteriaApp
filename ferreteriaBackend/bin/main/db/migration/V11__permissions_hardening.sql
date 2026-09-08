-- ============================================================================
-- V11__permissions_hardening.sql
-- DB-SEC-003/004/005/006 — hardening defensivo de permisos, search_path y
-- auditoría. Idempotente: REVOKE / ALTER FUNCTION / CREATE OR REPLACE FUNCTION
-- se pueden aplicar N veces sin efectos colaterales.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. DB-SEC-004: REVOKE DELETE en tablas ledger que aún permitían DELETE físico
--    tras el GRANT total de V1. La cancelación se hace vía UPDATE de estado
--    (no DELETE físico) para preservar auditoría.
-- ---------------------------------------------------------------------------
REVOKE DELETE ON ven.ventas, ven.cuentas_cobrar, com.compras, com.cuentas_pagar,
    fin.cortes_caja
    FROM ferreteria_app;

-- ---------------------------------------------------------------------------
-- 2. DB-SEC-003 (mitigación parcial): el rol ferreteria_app mantiene GRANTs
--    amplios por simplicidad operativa. La segregación en roles
--    (ferreteria_ro / ferreteria_pos / ferreteria_admin) queda como sprint
--    dedicado. Mientras tanto, REVOKE TRUNCATE en TODAS las tablas del
--    esquema de negocio evita el "borrado masivo accidental" desde la app.
-- ---------------------------------------------------------------------------
REVOKE TRUNCATE ON ALL TABLES IN SCHEMA cat, cfg, rh, seg, inv, com, ven, fin, fis
    FROM ferreteria_app;

-- ---------------------------------------------------------------------------
-- 3. DB-SEC-005: SET search_path explícito en cada función PL/pgSQL para
--    impedir "search_path hijack" (función maliciosa en esquema con prioridad
--    que se resuelve antes que public). Se incluye pg_catalog, public y el
--    esquema propietario de la función.
--    Idempotente: ALTER FUNCTION ... SET search_path = ... aplica el mismo
--    valor sin efectos colaterales al re-ejecutarse.
-- ---------------------------------------------------------------------------
ALTER FUNCTION cfg.fn_siguiente_folio(TEXT)
    SET search_path = pg_catalog, public, cfg;

ALTER FUNCTION common_asigna_folio()
    SET search_path = pg_catalog, public, inv, cfg;

ALTER FUNCTION seg.fn_auditar()
    SET search_path = pg_catalog, public, seg;

ALTER FUNCTION common_touch_updated_at()
    SET search_path = pg_catalog, public;

ALTER FUNCTION fin.fn_movimiento_caja(BIGINT, TEXT, TEXT, NUMERIC, INT, TEXT, BIGINT, INT)
    SET search_path = pg_catalog, public, fin, cat;

ALTER FUNCTION inv.fn_registrar_movimiento(BIGINT, INT, TEXT, NUMERIC, INT, NUMERIC, TEXT, BIGINT, INT, TEXT)
    SET search_path = pg_catalog, public, inv, cfg, cat;

ALTER FUNCTION inv.fn_aplica_movimiento_stock()
    SET search_path = pg_catalog, public, inv, cat, cfg;

ALTER FUNCTION inv.fn_kardex_solo_insert()
    SET search_path = pg_catalog, public, inv;

ALTER FUNCTION ven.fn_detalle_valida_stock()
    SET search_path = pg_catalog, public, ven, inv, cat;

ALTER FUNCTION ven.fn_detalle_genera_salida()
    SET search_path = pg_catalog, public, ven, inv, cat;

ALTER FUNCTION ven.fn_valida_credito(BIGINT, NUMERIC)
    SET search_path = pg_catalog, public, ven, cat;

ALTER FUNCTION ven.fn_recalc_totales_venta()
    SET search_path = pg_catalog, public, ven;

ALTER FUNCTION ven.fn_pago_cliente_post()
    SET search_path = pg_catalog, public, ven;

ALTER FUNCTION ven.fn_devolucion_detalle_post()
    SET search_path = pg_catalog, public, ven, inv, cat;

ALTER FUNCTION com.fn_detalle_compra_entrada()
    SET search_path = pg_catalog, public, com, inv, cat;

ALTER FUNCTION com.fn_recalc_totales_compra()
    SET search_path = pg_catalog, public, com, cfg;

ALTER FUNCTION com.fn_pago_proveedor_post()
    SET search_path = pg_catalog, public, com;

ALTER FUNCTION com.fn_devolucion_detalle_post()
    SET search_path = pg_catalog, public, com, inv, cat;

ALTER FUNCTION fin.fn_gasto_post()
    SET search_path = pg_catalog, public, fin;

ALTER FUNCTION fin.fn_ingreso_otro_post()
    SET search_path = pg_catalog, public, fin;

ALTER FUNCTION ven.fn_renta_post()
    SET search_path = pg_catalog, public, ven;

ALTER FUNCTION fin.fn_cerrar_turno(BIGINT, NUMERIC, INT, TEXT)
    SET search_path = pg_catalog, public, fin, ven, inv, cat;

ALTER FUNCTION ven.fn_promo_para_producto(BIGINT, NUMERIC, NUMERIC, BIGINT)
    SET search_path = pg_catalog, public, ven, cat;

ALTER FUNCTION ven.fn_registrar_uso_promo(BIGINT, BIGINT, BIGINT, NUMERIC, INT)
    SET search_path = pg_catalog, public, ven;

ALTER FUNCTION inv.fn_aplicar_traslado(INT, INT, INT, JSONB)
    SET search_path = pg_catalog, public, inv, cfg, cat;

-- ---------------------------------------------------------------------------
-- 4. DB-SEC-006: seg.fn_auditar() ahora excluye password_hash del JSONB
--    capturado en datos_anteriores/datos_nuevos. Un SELECT sobre seg.auditoria
--    ya no expone el bcrypt aunque ferreteria_app mantenga SELECT. La firma
--    y los triggers asociados (trg_audit_*) no cambian.
--    Idempotente: CREATE OR REPLACE FUNCTION reemplaza el cuerpo sin tocar
--    permisos ni dependencias.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION seg.fn_auditar()
RETURNS TRIGGER LANGUAGE plpgsql
SET search_path = pg_catalog, public, seg
AS $$
DECLARE v_uid INTEGER := NULLIF(current_setting('app.usuario_id', true), '')::INTEGER;
        v_pk_col TEXT := COALESCE(NULLIF(TG_ARGV[0], ''), 'id');
        v_row JSONB;
        v_old JSONB;
        v_new JSONB;
BEGIN
    -- DB-SEC-006: enmascarar password_hash antes de persistir en seg.auditoria.
    v_old := CASE WHEN TG_OP IN ('UPDATE','DELETE')
                  THEN to_jsonb(OLD) - 'password_hash' END;
    v_new := CASE WHEN TG_OP IN ('INSERT','UPDATE')
                  THEN to_jsonb(NEW) - 'password_hash' END;
    v_row := CASE WHEN TG_OP = 'DELETE' THEN v_old ELSE v_new END;
    INSERT INTO seg.auditoria (esquema, tabla, registro_id, accion,
                               datos_anteriores, datos_nuevos, usuario_id)
    VALUES (TG_TABLE_SCHEMA, TG_TABLE_NAME,
            COALESCE((v_row ->> v_pk_col)::BIGINT, 0),
            TG_OP, v_old, v_new, v_uid);
RETURN COALESCE(NEW, OLD);
END $$;