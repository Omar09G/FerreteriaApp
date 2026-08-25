-- ============================================================================
-- 04_admin.sql
-- Sistema Integral de Ferretería · Paso 4/5
-- Usuario ADMINISTRADOR (encargado de crear todo lo demás desde la aplicación).
--
--   psql -U postgres -d ferreteria -f 04_admin.sql
--
-- CREDENCIALES INICIALES:
--   usuario: admin
--   password: Admin123*        <-- OBLIGATORIO cambiar en el primer login.
--   El hash bcrypt se genera en la base con pgcrypto (nunca va en texto plano).
-- ============================================================================

SET timezone TO 'America/Mexico_City';

DO $$
DECLARE
    v_puesto_id  INTEGER;
    v_empleado_id INTEGER;
    v_usuario_id INTEGER;
    v_rol_id     INTEGER;
BEGIN
    -- ------------------------------------------------------------------
    -- 1. Puesto de administrador (creado en parametría)
    -- ------------------------------------------------------------------
    SELECT puesto_id INTO v_puesto_id FROM cat.puestos WHERE nombre = 'Administrador General';
    IF v_puesto_id IS NULL THEN
        RAISE EXCEPTION 'No existe el puesto Administrador General. Ejecuta 03_parametria.sql primero.';
    END IF;

    -- ------------------------------------------------------------------
    -- 2. Empleado administrador
    -- ------------------------------------------------------------------
    INSERT INTO rh.empleados
        (puesto_id, nombre, apellido_p, apellido_m, email, telefono,
         fecha_ingreso, sueldo_diario, activo)
    VALUES
        (v_puesto_id, 'ADMIN', 'SISTEMAS', NULL, 'admin@ferreteria.local',
         '81-0000-0000', CURRENT_DATE, 900, true)
    ON CONFLICT (email) DO NOTHING;

    SELECT empleado_id INTO v_empleado_id
    FROM rh.empleados WHERE email = 'admin@ferreteria.local';

    -- ------------------------------------------------------------------
    -- 3. Usuario admin con hash bcrypt generado en BD
    -- ------------------------------------------------------------------
    INSERT INTO seg.usuarios (empleado_id, username, email, password_hash, activo)
    VALUES
        (v_empleado_id, 'admin', 'admin@ferreteria.local',
         crypt('Admin123*', gen_salt('bf', 12)), true)
    ON CONFLICT (username) DO UPDATE
        SET password_hash = EXCLUDED.password_hash,
            empleado_id   = EXCLUDED.empleado_id,
            activo        = true;

    SELECT usuario_id INTO v_usuario_id FROM seg.usuarios WHERE username = 'admin';

    -- ------------------------------------------------------------------
    -- 4. Rol ADMINISTRADOR (permisos asignados en 03_parametria.sql)
    -- ------------------------------------------------------------------
    SELECT rol_id INTO v_rol_id FROM seg.roles WHERE clave = 'ADMINISTRADOR';
    IF v_rol_id IS NULL THEN
        RAISE EXCEPTION 'No existe el rol ADMINISTRADOR. Ejecuta 03_parametria.sql primero.';
    END IF;

    INSERT INTO seg.usuario_roles (usuario_id, rol_id)
    VALUES (v_usuario_id, v_rol_id)
    ON CONFLICT DO NOTHING;

    RAISE NOTICE '==========================================================';
    RAISE NOTICE 'Usuario administrador listo:';
    RAISE NOTICE '  usuario : admin';
    RAISE NOTICE '  password: Admin123*   (CAMBIAR en primer login)';
    RAISE NOTICE '  rol     : ADMINISTRADOR (todos los permisos)';
    RAISE NOTICE '==========================================================';
END $$;

SELECT u.username,
       e.nombre || ' ' || e.apellido_p AS empleado,
       r.clave AS rol,
       COUNT(rp.permiso_id) AS permisos_asignados,
       u.activo
FROM seg.usuarios u
JOIN rh.empleados e      ON e.empleado_id = u.empleado_id
JOIN seg.usuario_roles ur ON ur.usuario_id = u.usuario_id
JOIN seg.roles r          ON r.rol_id = ur.rol_id
LEFT JOIN seg.rol_permisos rp ON rp.rol_id = r.rol_id
WHERE u.username = 'admin'
GROUP BY u.username, e.nombre, e.apellido_p, r.clave, u.activo;
