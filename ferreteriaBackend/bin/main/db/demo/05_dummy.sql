-- ============================================================================
-- 05_dummy.sql
-- Sistema Integral de Ferretería · Paso 5/5
-- Datos DEMO end-to-end: usuarios, almacén, proveedores, clientes, productos,
-- inventario inicial, compras, ventas (contado y crédito), abonos, promociones,
-- devolución, renta, gastos, otros ingresos, nómina, conteo físico y traslado.
--
--   psql -U postgres -d ferreteria -f 05_dummy.sql
--
-- Requiere: 02_tablas.sql, 03_parametria.sql y 04_admin.sql ya ejecutados.
-- Todo corre en UNA transacción (DO block): si algo falla, no queda nada a medias.
-- Nota: las ventas de días anteriores se insertan SIN turno de caja (turno NULL),
-- por lo que no generan flujo en caja; las de HOJ sí usan el turno abierto.
-- ============================================================================

SET timezone TO 'America/Mexico_City';

DO $$
DECLARE
    -- Usuarios / empleados
    v_u_admin INT;  v_u_cajero INT;  v_u_vend INT;
    v_e_cajero INT; v_e_vend INT;   v_e_alm INT;
    -- Estructura
    v_alm_cen INT;  v_alm_bod INT;  v_caja INT;  v_turno BIGINT;
    -- Proveedores / clientes
    v_pv_truper INT; v_pv_cemex INT; v_pv_guerrero INT;
    v_cli_moctezuma BIGINT; v_cli_constructora BIGINT; v_cli_garcia BIGINT;
    -- Formas de pago
    v_fp_efec INT; v_fp_td INT; v_fp_tc INT; v_fp_transf INT; v_fp_cred INT;
    -- Documentos
    v_vid BIGINT; v_cc BIGINT; v_dev BIGINT; v_renta BIGINT;
    v_compra1 BIGINT; v_compra2 BIGINT; v_compra3 BIGINT;
    v_promo2 BIGINT; v_nomina BIGINT;
BEGIN
    -- =====================================================================
    -- 0. IDs base
    -- =====================================================================
    SELECT usuario_id INTO v_u_admin FROM seg.usuarios WHERE username = 'admin';

    SELECT forma_pago_id INTO v_fp_efec    FROM cat.formas_pago WHERE clave='EFECTIVO';
    SELECT forma_pago_id INTO v_fp_td      FROM cat.formas_pago WHERE clave='TARJETA_DEBITO';
    SELECT forma_pago_id INTO v_fp_tc      FROM cat.formas_pago WHERE clave='TARJETA_CREDITO';
    SELECT forma_pago_id INTO v_fp_transf  FROM cat.formas_pago WHERE clave='TRANSFERENCIA';
    SELECT forma_pago_id INTO v_fp_cred    FROM cat.formas_pago WHERE clave='CREDITO';

    -- =====================================================================
    -- 1. EMPLEADOS Y USUARIOS ADICIONALES
    -- =====================================================================
    INSERT INTO rh.empleados (puesto_id, nombre, apellido_p, apellido_m, email, telefono, sueldo_diario)
    VALUES ((SELECT puesto_id FROM cat.puestos WHERE nombre='Encargado de caja'),
            'Juan', 'Pérez', 'González', 'cajero@ferreteria.local', '81-1111-1111', 450)
    ON CONFLICT (email) DO NOTHING;

    INSERT INTO rh.empleados (puesto_id, nombre, apellido_p, apellido_m, email, telefono, sueldo_diario)
    VALUES ((SELECT puesto_id FROM cat.puestos WHERE nombre='Vendedor'),
            'María', 'López', 'Ramírez', 'vendedor@ferreteria.local', '81-2222-2222', 400)
    ON CONFLICT (email) DO NOTHING;

    INSERT INTO rh.empleados (puesto_id, nombre, apellido_p, apellido_m, email, telefono, sueldo_diario)
    VALUES ((SELECT puesto_id FROM cat.puestos WHERE nombre='Almacenista'),
            'Pedro', 'Ramírez', 'Soto', 'almacen@ferreteria.local', '81-3333-3333', 400)
    ON CONFLICT (email) DO NOTHING;

    SELECT empleado_id INTO v_e_cajero FROM rh.empleados WHERE email='cajero@ferreteria.local';
    SELECT empleado_id INTO v_e_vend   FROM rh.empleados WHERE email='vendedor@ferreteria.local';
    SELECT empleado_id INTO v_e_alm    FROM rh.empleados WHERE email='almacen@ferreteria.local';

    INSERT INTO seg.usuarios (empleado_id, username, email, password_hash)
    VALUES (v_e_cajero, 'cajero1', 'cajero@ferreteria.local',   crypt('Cajero123*',   gen_salt('bf',12))),
           (v_e_vend,   'vendedor1','vendedor@ferreteria.local',crypt('Vendedor123*', gen_salt('bf',12)))
    ON CONFLICT (username) DO NOTHING;

    INSERT INTO seg.usuarios (empleado_id, username, email, password_hash)
    VALUES (v_e_alm, 'almacen1', 'almacen@ferreteria.local', crypt('Almacen123*', gen_salt('bf',12)))
    ON CONFLICT (username) DO NOTHING;

    SELECT usuario_id INTO v_u_cajero FROM seg.usuarios WHERE username='cajero1';
    SELECT usuario_id INTO v_u_vend   FROM seg.usuarios WHERE username='vendedor1';

    INSERT INTO seg.usuario_roles (usuario_id, rol_id)
    SELECT v_u_cajero, rol_id FROM seg.roles WHERE clave='ENCARGADO_CAJA'
    UNION ALL SELECT v_u_vend, rol_id FROM seg.roles WHERE clave='VENDEDOR'
    ON CONFLICT DO NOTHING;

    -- Almacenista: usuario sin login activo para demo (queda creado si se necesita)

    -- =====================================================================
    -- 2. ALMACÉN, CAJA Y TURNO ABIERTO
    -- =====================================================================
    INSERT INTO inv.almacenes (nombre, direccion, es_punto_venta)
    VALUES ('Sucursal Centro','Av. Unión 123, Monterrey', true),
           ('Bodega Norte','Periférico 456, Monterrey', false)
    ON CONFLICT (nombre) DO NOTHING;

    SELECT almacen_id INTO v_alm_cen FROM inv.almacenes WHERE nombre='Sucursal Centro';
    SELECT almacen_id INTO v_alm_bod FROM inv.almacenes WHERE nombre='Bodega Norte';

    INSERT INTO fin.cajas (nombre, almacen_id) VALUES ('CAJA_01', v_alm_cen)
    ON CONFLICT (nombre) DO NOTHING;

    SELECT caja_id INTO v_caja FROM fin.cajas WHERE nombre='CAJA_01';

    SELECT turno_caja_id INTO v_turno FROM fin.turnos_caja
     WHERE caja_id = v_caja AND estado='ABIERTO';
    IF v_turno IS NULL THEN
        INSERT INTO fin.turnos_caja (caja_id, usuario_id, monto_apertura,
                                     apertura_en, observaciones)
        VALUES (v_caja, v_u_admin, 3000,
                (CURRENT_DATE::timestamp + time '09:00') AT TIME ZONE 'America/Mexico_City',
                'Turno demo del día')
        RETURNING turno_caja_id INTO v_turno;
    END IF;

    -- =====================================================================
    -- 3. PROVEEDORES
    -- =====================================================================
    INSERT INTO com.proveedores (razon_social, rfc, regimen_fiscal, contacto_nombre, telefono,
                                 calle, colonia, ciudad_id, cp, dias_credito, limite_credito)
    VALUES ('Truper Distribuidora SA CV','TTR850101ABC','601','Lic. Soto','81-8000-1000',
            'Parque Industrial 1','Apodaca',(SELECT ciudad_id FROM cat.ciudades c JOIN cat.estados e USING(estado_id) WHERE e.nombre='Nuevo León' AND c.nombre='Apodaca'),'66600',30,80000),
           ('Cemex Construcción','CEM900101DEF','601','Ing. Ramírez','81-8000-2000',
            'Av. Constitución 100','Monterrey',(SELECT ciudad_id FROM cat.ciudades c JOIN cat.estados e USING(estado_id) WHERE e.nombre='Nuevo León' AND c.nombre='Monterrey'),'64000',0,0),
           ('Electricidad Guerrero','EUG950101GHI','626','Sra. Ruiz','81-8000-3000',
            'Rayón 78','Guadalupe',(SELECT ciudad_id FROM cat.ciudades c JOIN cat.estados e USING(estado_id) WHERE e.nombre='Nuevo León' AND c.nombre='Guadalupe'),'67100',15,30000)
    ON CONFLICT (razon_social) DO NOTHING;

    SELECT proveedor_id INTO v_pv_truper    FROM com.proveedores WHERE razon_social='Truper Distribuidora SA CV';
    SELECT proveedor_id INTO v_pv_cemex     FROM com.proveedores WHERE razon_social='Cemex Construcción';
    SELECT proveedor_id INTO v_pv_guerrero  FROM com.proveedores WHERE razon_social='Electricidad Guerrero';

    -- =====================================================================
    -- 4. CLIENTES + LÍNEAS DE CRÉDITO
    -- =====================================================================
    INSERT INTO ven.clientes (tipo_persona, razon_social, rfc, regimen_fiscal, telefono,
                              es_mayorista, dias_credito, limite_credito, ciudad_id)
    VALUES
     ('MORAL','Moctezuma Ferretera Mayorista SA CV','MFE120101JKL','601','81-5000-1000',
      true, 15, 60000,(SELECT ciudad_id FROM cat.ciudades c JOIN cat.estados e USING(estado_id) WHERE e.nombre='Nuevo León' AND c.nombre='Monterrey')),
     ('MORAL','Constructora del Norte SA CV','CON100101MNO','601','81-5000-2000',
      true, 30, 150000,(SELECT ciudad_id FROM cat.ciudades c JOIN cat.estados e USING(estado_id) WHERE e.nombre='Nuevo León' AND c.nombre='San Pedro Garza García')),
     ('FISICA','Juanita Pérez Compras Menudeo','PECJ800101MV7',NULL,'81-5000-3000',
      false, 0, 0, NULL),
     ('FISICA','Gerardo García Plomería','GAGG750101NX2','626','81-5000-4000',
      false, 0, 0, NULL),
     ('MORAL','Escuela Secundaria Técnica 18','EST950101OPQ','603','81-5000-5000',
      false, 8, 20000, NULL),
     ('FISICA','Rosa Inda Jardinería','IIRB820101QR3','612','81-5000-6000',
      false, 0, 0, NULL)
    ON CONFLICT DO NOTHING;

    SELECT cliente_id INTO v_cli_moctezuma     FROM ven.clientes WHERE razon_social LIKE 'Moctezuma%';
    SELECT cliente_id INTO v_cli_constructora  FROM ven.clientes WHERE razon_social LIKE 'Constructora%';
    SELECT cliente_id INTO v_cli_garcia        FROM ven.clientes WHERE razon_social LIKE 'Gerardo García%';

    INSERT INTO ven.lineas_credito (cliente_id, monto_autorizado, dias_credito,
                                    usuario_autorizo_id, vigente_hasta, observaciones)
    VALUES (v_cli_moctezuma,    60000, 15, v_u_admin, CURRENT_DATE + 365, 'Cliente frecuente desde 2019'),
           (v_cli_constructora,150000, 30, v_u_admin, CURRENT_DATE + 365, 'Contrato de obra vigente')
    ON CONFLICT DO NOTHING;

    -- =====================================================================
    -- 5. PRODUCTOS (22: 18 físicos + 2 servicios + 2 renta)
    -- =====================================================================
    INSERT INTO inv.productos (codigo, tipo, nombre, categoria_id, marca_id,
                               unidad_medida_id, costo_actual, precio_menudeo,
                               precio_mayoreo, mayoreo_desde, ubicacion_almacen, atributos)
    VALUES
     ('MAR-001','PRODUCTO','Martillo bola 16 oz fibra',
        (SELECT categoria_id FROM cat.categorias WHERE nombre='Martillos'),
        (SELECT marca_id FROM cat.marcas WHERE nombre='Truper'),
        (SELECT unidad_id FROM cat.unidades_medida WHERE clave='PZA'),
        85,129,115,6,'H-A1','{"peso_oz":16}'),
     ('LLA-002','PRODUCTO','Juego llaves combinadas 12 pz',
        (SELECT categoria_id FROM cat.categorias WHERE nombre='Llaves y Dados'),
        (SELECT marca_id FROM cat.marcas WHERE nombre='Urrea'),
        (SELECT unidad_id FROM cat.unidades_medida WHERE clave='JGO'),
        380,489,455,3,'H-A2',NULL),
     ('DES-003','PRODUCTO','Desarmador plano 1/4 x 6',
        (SELECT categoria_id FROM cat.categorias WHERE nombre='Desarmadores'),
        (SELECT marca_id FROM cat.marcas WHERE nombre='Pretul'),
        (SELECT unidad_id FROM cat.unidades_medida WHERE clave='PZA'),
        25,39,NULL,NULL,'H-A3',NULL),
     ('PIN-004','PRODUCTO','Pinza de electricista 8',
        (SELECT categoria_id FROM cat.categorias WHERE nombre='Pinzas'),
        (SELECT marca_id FROM cat.marcas WHERE nombre='Hermann'),
        (SELECT unidad_id FROM cat.unidades_medida WHERE clave='PZA'),
        95,139,125,4,'H-A4',NULL),
     ('TAL-005','PRODUCTO','Taladro percutor 1/2 650W',
        (SELECT categoria_id FROM cat.categorias WHERE nombre='Taladros'),
        (SELECT marca_id FROM cat.marcas WHERE nombre='Bosch'),
        (SELECT unidad_id FROM cat.unidades_medida WHERE clave='PZA'),
        890,1249,1150,3,'H-B1','{"potencia_w":650,"mandril":"1/2"}'),
     ('AMO-007','PRODUCTO','Amoladora 4-1/2 850W',
        (SELECT categoria_id FROM cat.categorias WHERE nombre='Amoladoras'),
        (SELECT marca_id FROM cat.marcas WHERE nombre='DeWalt'),
        (SELECT unidad_id FROM cat.unidades_medida WHERE clave='PZA'),
        760,1049,980,3,'H-B2','{"potencia_w":850}'),
     ('PVC-010','PRODUCTO','Tubo PVC hidraulico 1/2 (metro)',
        (SELECT categoria_id FROM cat.categorias WHERE nombre='Tubo PVC'),
        (SELECT marca_id FROM cat.marcas WHERE nombre='Genérica'),
        (SELECT unidad_id FROM cat.unidades_medida WHERE clave='M'),
        68,95,88,20,'P-C1',NULL),
     ('PVC-011','PRODUCTO','Codo PVC 90 1/2',
        (SELECT categoria_id FROM cat.categorias WHERE nombre='Accesorios PVC'),
        (SELECT marca_id FROM cat.marcas WHERE nombre='Genérica'),
        (SELECT unidad_id FROM cat.unidades_medida WHERE clave='PZA'),
        8,13,NULL,NULL,'P-C2',NULL),
     ('LLA-012','PRODUCTO','Llave de paso bronce 1/2',
        (SELECT categoria_id FROM cat.categorias WHERE nombre='Llaves de Paso'),
        (SELECT marca_id FROM cat.marcas WHERE nombre='Genérica'),
        (SELECT unidad_id FROM cat.unidades_medida WHERE clave='PZA'),
        120,175,160,6,'P-C3',NULL),
     ('CAB-020','PRODUCTO','Cable THW 12 AWG (corte por metro)',
        (SELECT categoria_id FROM cat.categorias WHERE nombre='Cable y Alambre'),
        (SELECT marca_id FROM cat.marcas WHERE nombre='Condumex'),
        (SELECT unidad_id FROM cat.unidades_medida WHERE clave='M'),
        18,26,24,50,'E-D1',NULL),
     ('APC-021','PRODUCTO','Apagador sencillo blanco',
        (SELECT categoria_id FROM cat.categorias WHERE nombre='Apagadores y Contactos'),
        (SELECT marca_id FROM cat.marcas WHERE nombre='Berel'),
        (SELECT unidad_id FROM cat.unidades_medida WHERE clave='PZA'),
        32,49,44,10,'E-D2',NULL),
     ('FOC-022','PRODUCTO','Foco LED 9W luz fria',
        (SELECT categoria_id FROM cat.categorias WHERE nombre='Focos y Lámparas LED'),
        (SELECT marca_id FROM cat.marcas WHERE nombre='Genérica'),
        (SELECT unidad_id FROM cat.unidades_medida WHERE clave='PZA'),
        28,45,40,12,'E-D3','{"watts":9,"kelvin":6500}'),
     ('BRK-023','PRODUCTO','Breaker termomagnetico 20A',
        (SELECT categoria_id FROM cat.categorias WHERE nombre='Breakers y Centros de Carga'),
        (SELECT marca_id FROM cat.marcas WHERE nombre='Berel'),
        (SELECT unidad_id FROM cat.unidades_medida WHERE clave='PZA'),
        98,145,132,6,'E-D4',NULL),
     ('CEM-030','PRODUCTO','Cemento gris 50 kg',
        (SELECT categoria_id FROM cat.categorias WHERE nombre='Cemento y Mortero'),
        (SELECT marca_id FROM cat.marcas WHERE nombre='Genérica'),
        (SELECT unidad_id FROM cat.unidades_medida WHERE clave='PQ'),
        215,245,235,10,'K-E1',NULL),
     ('TOR-040','PRODUCTO','Tornillo drywall 1 x 500 pz',
        (SELECT categoria_id FROM cat.categorias WHERE nombre='Tornillos'),
        (SELECT marca_id FROM cat.marcas WHERE nombre='Genérica'),
        (SELECT unidad_id FROM cat.unidades_medida WHERE clave='PQ'),
        55,79,NULL,NULL,'T-F1',NULL),
     ('CLA-041','PRODUCTO','Clavo 2-1/2 (por kilogramo)',
        (SELECT categoria_id FROM cat.categorias WHERE nombre='Clavos'),
        (SELECT marca_id FROM cat.marcas WHERE nombre='Genérica'),
        (SELECT unidad_id FROM cat.unidades_medida WHERE clave='KG'),
        34,48,44,5,'T-F2',NULL),
     ('PIN-042','PRODUCTO','Pintura vinilica blanca 4L',
        (SELECT categoria_id FROM cat.categorias WHERE nombre='Pintura Vinílica'),
        (SELECT marca_id FROM cat.marcas WHERE nombre='Comex'),
        (SELECT unidad_id FROM cat.unidades_medida WHERE clave='CAJA'),
        320,429,400,4,'G-G1',NULL),
     ('BRO-043','PRODUCTO','Brocha 3 cerda natural',
        (SELECT categoria_id FROM cat.categorias WHERE nombre='Brochas y Rodillos'),
        (SELECT marca_id FROM cat.marcas WHERE nombre='Pretul'),
        (SELECT unidad_id FROM cat.unidades_medida WHERE clave='PZA'),
        42,65,NULL,NULL,'G-G2',NULL),
     ('MAN-050','PRODUCTO','Manguera jardin 15 m',
        (SELECT categoria_id FROM cat.categorias WHERE nombre='Mangueras y Riego'),
        (SELECT marca_id FROM cat.marcas WHERE nombre='Genérica'),
        (SELECT unidad_id FROM cat.unidades_medida WHERE clave='PZA'),
        189,259,240,3,'J-H1',NULL),
     ('SERV-001','SERVICIO','Corte de vidrio a medida',
        (SELECT categoria_id FROM cat.categorias WHERE nombre='Corte de Vidrio y Espejo'),
        NULL,
        (SELECT unidad_id FROM cat.unidades_medida WHERE clave='SRV'),
        0,80,NULL,NULL,NULL,NULL),
     ('SERV-002','SERVICIO','Duplicado de llave',
        (SELECT categoria_id FROM cat.categorias WHERE nombre='Duplicado de Llaves'),
        NULL,
        (SELECT unidad_id FROM cat.unidades_medida WHERE clave='SRV'),
        0,35,NULL,NULL,NULL,NULL),
     ('REN-001','HERRAMIENTA_RENTA','Rotomarto SDS (renta por dia)',
        (SELECT categoria_id FROM cat.categorias WHERE nombre='Rotomartos'),
        (SELECT marca_id FROM cat.marcas WHERE nombre='Truper'),
        (SELECT unidad_id FROM cat.unidades_medida WHERE clave='PZA'),
        1500,250,NULL,NULL,'R-Z1',NULL),
     ('REN-002','HERRAMIENTA_RENTA','Compresor 25L (renta por dia)',
        (SELECT categoria_id FROM cat.categorias WHERE nombre='Renta de Equipo'),
        NULL,
        (SELECT unidad_id FROM cat.unidades_medida WHERE clave='PZA'),
        2200,180,NULL,NULL,'R-Z2',NULL)
    ON CONFLICT (codigo) DO NOTHING;

    -- Códigos de barras
    INSERT INTO inv.producto_codigos_barras (codigo_barras, producto_id, factor) VALUES
     ('7501234567001',(SELECT producto_id FROM inv.productos WHERE codigo='MAR-001'),1),
     ('7501234567002',(SELECT producto_id FROM inv.productos WHERE codigo='TAL-005'),1),
     ('7501234567003',(SELECT producto_id FROM inv.productos WHERE codigo='TOR-040'),1),
     ('7501234567004',(SELECT producto_id FROM inv.productos WHERE codigo='CEM-030'),1),
     ('7501234567005',(SELECT producto_id FROM inv.productos WHERE codigo='FOC-022'),1)
    ON CONFLICT (codigo_barras) DO NOTHING;

    -- Relación producto-proveedor
    INSERT INTO inv.producto_proveedores (producto_id, proveedor_id, costo_ref, es_principal)
    SELECT p.producto_id, v_pv_truper,
           CASE WHEN p.codigo IN ('MAR-001') THEN 82 WHEN p.codigo IN ('DES-003') THEN 24
                WHEN p.codigo IN ('PIN-004') THEN 92 ELSE 745 END, true
    FROM inv.productos p WHERE p.codigo IN ('MAR-001','DES-003','PIN-004','AMO-007')
    ON CONFLICT DO NOTHING;

    INSERT INTO inv.producto_proveedores (producto_id, proveedor_id, costo_ref, es_principal)
    SELECT p.producto_id, v_pv_cemex, 205, true
    FROM inv.productos p WHERE p.codigo='CEM-030'
    ON CONFLICT DO NOTHING;

    INSERT INTO inv.producto_proveedores (producto_id, proveedor_id, costo_ref, es_principal)
    SELECT p.producto_id, v_pv_guerrero,
           CASE WHEN p.codigo='CAB-020' THEN 17.5 WHEN p.codigo='BRK-023' THEN 95 ELSE 31 END, true
    FROM inv.productos p WHERE p.codigo IN ('CAB-020','BRK-023','APC-021')
    ON CONFLICT DO NOTHING;

    -- Impuesto default IVA 16% a todos los productos físicos que aplican IVA
    INSERT INTO fis.producto_impuesto (producto_id, tasa_id, es_default)
    SELECT p.producto_id, t.tasa_id, true
    FROM inv.productos p
    CROSS JOIN (SELECT t.tasa_id
                FROM fis.tasas_impuesto t JOIN fis.impuestos i USING (impuesto_id)
                WHERE i.clave_sat='002' AND t.tasa=0.16 AND t.factor='TASA'
                  AND t.ambito='VENTA' AND NOT t.zona_frontera) t
    WHERE p.tipo='PRODUCTO' AND p.aplica_iva
    ON CONFLICT DO NOTHING;

    -- =====================================================================
    -- 6. INVENTARIO INICIAL (kardex → actualiza stock vía trigger)
    -- =====================================================================
    INSERT INTO inv.movimientos_inventario
        (producto_id, almacen_id, tipo, cantidad, costo_unitario, motivo_id,
         ref_tabla, ref_id, usuario_id, nota)
    SELECT p.producto_id, v_alm_cen, 'ENTRADA', d.qty, d.costo,
           (SELECT motivo_id FROM cat.motivos_movimiento WHERE clave='INVENTARIO_INICIAL'),
           'demo', NULL, v_u_admin, 'Inventario inicial demo'
    FROM (VALUES
        ('MAR-001',60,85),('LLA-002',25,380),('DES-003',120,25),('PIN-004',40,95),
        ('TAL-005',12,890),('AMO-007',10,760),('PVC-010',400,68),('PVC-011',500,8),
        ('LLA-012',45,120),('CAB-020',200,18),('APC-021',250,32),('FOC-022',400,28),
        ('BRK-023',80,98),('CEM-030',100,215),('TOR-040',600,55),('CLA-041',300,34),
        ('PIN-042',40,320),('BRO-043',90,42),('MAN-050',30,189),
        ('REN-001',3,1500),('REN-002',2,2200)
    ) d(codigo, qty, costo)
    JOIN inv.productos p ON p.codigo = d.codigo;

    INSERT INTO inv.movimientos_inventario
        (producto_id, almacen_id, tipo, cantidad, costo_unitario, motivo_id,
         ref_tabla, ref_id, usuario_id, nota)
    SELECT p.producto_id, v_alm_bod, 'ENTRADA', d.qty, d.costo,
           (SELECT motivo_id FROM cat.motivos_movimiento WHERE clave='INVENTARIO_INICIAL'),
           'demo', NULL, v_u_admin, 'Inventario inicial bodega'
    FROM (VALUES ('CEM-030',150,215),('PVC-010',600,68),('TOR-040',400,55),('CLA-041',200,34)
    ) d(codigo, qty, costo)
    JOIN inv.productos p ON p.codigo = d.codigo;

    -- Stock mínimo por producto en sucursal
    UPDATE inv.inventario i SET stock_minimo = d.minimo
    FROM (VALUES ('MAR-001',10),('DES-003',30),('CEM-030',20),('FOC-022',50),
                 ('TOR-040',50),('CLA-041',40),('CAB-020',50)) d(codigo,minimo)
    JOIN inv.productos p ON p.codigo = d.codigo
    WHERE i.producto_id = p.producto_id AND i.almacen_id = v_alm_cen;

    -- =====================================================================
    -- 7. COMPRAS (crédito 30d vencida, contado, crédito 15d vencida)
    -- =====================================================================
    -- C1 · Truper — crédito 30 días (se simulará vencida)
    INSERT INTO com.compras (factura_proveedor, proveedor_id, almacen_id, fecha,
                             forma_pago_id, estado, usuario_id)
    VALUES ('FAC-T-88421', v_pv_truper, v_alm_cen,
            ((CURRENT_DATE - 35)::timestamp + interval '10 hours') AT TIME ZONE 'America/Mexico_City',
            v_fp_cred, 'RECIBIDA', v_u_admin)
    RETURNING compra_id INTO v_compra1;

    INSERT INTO com.compra_detalles (compra_id, producto_id, cantidad, costo_unitario)
    SELECT v_compra1, p.producto_id, d.qty, d.costo
    FROM (VALUES ('MAR-001',24,82),('DES-003',48,24),('PIN-004',12,92),('AMO-007',6,745)
    ) d(codigo, qty, costo)
    JOIN inv.productos p ON p.codigo = d.codigo;

    -- C2 · Cemex — contado por transferencia (auto-pagada por trigger)
    INSERT INTO com.compras (factura_proveedor, proveedor_id, almacen_id, fecha,
                             forma_pago_id, estado, usuario_id, turno_caja_id)
    VALUES ('CEM-99123', v_pv_cemex, v_alm_cen,
            ((CURRENT_DATE - 2)::timestamp + interval '9 hours') AT TIME ZONE 'America/Mexico_City',
            v_fp_transf, 'RECIBIDA', v_u_admin, v_turno)
    RETURNING compra_id INTO v_compra2;

    INSERT INTO com.compra_detalles (compra_id, producto_id, cantidad, costo_unitario)
    SELECT v_compra2, p.producto_id, d.qty, d.costo
    FROM (VALUES ('CEM-030',40,205)) d(codigo, qty, costo)
    JOIN inv.productos p ON p.codigo = d.codigo;

    -- C3 · Electricidad Guerrero — crédito 15 días (se simulará vencida)
    INSERT INTO com.compras (factura_proveedor, proveedor_id, almacen_id, fecha,
                             forma_pago_id, estado, usuario_id)
    VALUES ('EG-2026-114', v_pv_guerrero, v_alm_cen,
            ((CURRENT_DATE - 20)::timestamp + interval '11 hours') AT TIME ZONE 'America/Mexico_City',
            v_fp_cred, 'RECIBIDA', v_u_admin)
    RETURNING compra_id INTO v_compra3;

    INSERT INTO com.compra_detalles (compra_id, producto_id, cantidad, costo_unitario)
    SELECT v_compra3, p.producto_id, d.qty, d.costo
    FROM (VALUES ('CAB-020',20,17.5),('BRK-023',30,95),('APC-021',100,31)
    ) d(codigo, qty, costo)
    JOIN inv.productos p ON p.codigo = d.codigo;

    -- Simulación de mora para demostrar vistas de facturas vencidas
    UPDATE com.cuentas_pagar SET fecha_vencimiento = CURRENT_DATE - 11 WHERE compra_id = v_compra1;
    UPDATE com.cuentas_pagar SET fecha_vencimiento = CURRENT_DATE - 5  WHERE compra_id = v_compra3;

    -- =====================================================================
    -- 8. TRASLADO Centro → Bodega
    -- =====================================================================
    PERFORM inv.fn_aplicar_traslado(v_alm_cen, v_alm_bod, v_u_admin,
        jsonb_build_array(
            jsonb_build_object('producto',(SELECT producto_id FROM inv.productos WHERE codigo='CEM-030'),'cantidad',20),
            jsonb_build_object('producto',(SELECT producto_id FROM inv.productos WHERE codigo='TOR-040'),'cantidad',50)));

    -- =====================================================================
    -- 9. PROMOCIONES
    -- =====================================================================
    INSERT INTO ven.promociones (nombre, descripcion, tipo, valor_pct, compra_min_total,
                                 dias_semana, estado, usuario_id)
    VALUES ('Martes de 10% en compras mayores a $500',
            'Todos los martes, ticket mínimo 500 pesos',
            'DESCUENTO_TOTAL_VENTA', 10, 500, '{2}', 'ACTIVA', v_u_admin);

    INSERT INTO ven.promociones (nombre, descripcion, tipo, valor_pct,
                                 dias_semana, hora_desde, hora_hasta, estado, usuario_id)
    VALUES ('Tornilleria Happy Hour 15%',
            '15% en toda la familia Tornillería y Fijación, lunes a viernes de 15:00 a 18:00',
            'DESCUENTO_PRODUCTO', 15, '{1,2,3,4,5}', '15:00', '18:00', 'ACTIVA', v_u_admin)
    RETURNING promocion_id INTO v_promo2;

    INSERT INTO ven.promocion_categorias (promocion_id, categoria_id)
    SELECT v_promo2, categoria_id FROM cat.categorias
    WHERE nombre = 'Tornillería y Fijación' AND nivel = 0;

    -- Catálogo de descuentos manuales
    INSERT INTO ven.descuentos (codigo, nombre, tipo, valor, aplica_a, requiere_autorizacion, usuario_id)
    VALUES ('LIQ15','Liquidación autorizada 15%','PORCENTAJE',15,'LINEA',true,v_u_admin)
    ON CONFLICT (codigo) DO NOTHING;

    -- =====================================================================
    -- 10. VENTAS (histórico variado + hoy con turno/caja)
    -- =====================================================================
    -- V1 · hace 9 días 11:00 · efectivo
    INSERT INTO ven.ventas (cliente_id, almacen_id, fecha, forma_pago_id, usuario_id, notas)
    VALUES (NULL, v_alm_cen,
            ((CURRENT_DATE - 9)::timestamp + interval '11 hours') AT TIME ZONE 'America/Mexico_City',
            v_fp_efec, v_u_vend, 'Venta demo menudeo')
    RETURNING venta_id INTO v_vid;
    INSERT INTO ven.venta_detalles (venta_id, producto_id, cantidad, precio_unitario, costo_unitario)
    SELECT v_vid, p.producto_id, d.qty, d.precio, d.costo
    FROM (VALUES ('MAR-001',2,129,83),('DES-003',4,39,24),('CLA-041',5,48,34)) d(codigo,qty,precio,costo)
    JOIN inv.productos p ON p.codigo=d.codigo;

    -- V2 · hace 7 días 16:20 · tarjeta débito (se devolverá el taladro)
    INSERT INTO ven.ventas (cliente_id, almacen_id, fecha, forma_pago_id, usuario_id)
    VALUES (NULL, v_alm_cen,
            ((CURRENT_DATE - 7)::timestamp + interval '16 hours' + interval '20 minutes') AT TIME ZONE 'America/Mexico_City',
            v_fp_td, v_u_cajero)
    RETURNING venta_id INTO v_vid;
    INSERT INTO ven.venta_detalles (venta_id, producto_id, cantidad, precio_unitario, costo_unitario)
    SELECT v_vid, p.producto_id, d.qty, d.precio, d.costo
    FROM (VALUES ('TAL-005',1,1249,890),('FOC-022',6,45,28)) d(codigo,qty,precio,costo)
    JOIN inv.productos p ON p.codigo=d.codigo;

    -- V3 · hace 7 días 12:05 · efectivo
    INSERT INTO ven.ventas (cliente_id, almacen_id, fecha, forma_pago_id, usuario_id)
    VALUES (NULL, v_alm_cen,
            ((CURRENT_DATE - 7)::timestamp + interval '12 hours' + interval '5 minutes') AT TIME ZONE 'America/Mexico_City',
            v_fp_efec, v_u_vend);
    INSERT INTO ven.venta_detalles (venta_id, producto_id, cantidad, precio_unitario, costo_unitario)
    SELECT (SELECT venta_id FROM ven.ventas ORDER BY venta_id DESC LIMIT 1),
           p.producto_id, d.qty, d.precio, d.costo
    FROM (VALUES ('PVC-010',15,95,68),('PVC-011',10,13,8)) d(codigo,qty,precio,costo)
    JOIN inv.productos p ON p.codigo=d.codigo;

    -- V4 · hace 5 días 13:00 · transferencia · cliente mayorista
    INSERT INTO ven.ventas (cliente_id, almacen_id, fecha, forma_pago_id, usuario_id)
    VALUES (v_cli_moctezuma, v_alm_cen,
            ((CURRENT_DATE - 5)::timestamp + interval '13 hours') AT TIME ZONE 'America/Mexico_City',
            v_fp_transf, v_u_cajero);
    INSERT INTO ven.venta_detalles (venta_id, producto_id, cantidad, precio_unitario, costo_unitario)
    SELECT (SELECT venta_id FROM ven.ventas ORDER BY venta_id DESC LIMIT 1),
           p.producto_id, d.qty, d.precio, d.costo
    FROM (VALUES ('LLA-002',3,489,380),('PIN-004',2,139,93)) d(codigo,qty,precio,costo)
    JOIN inv.productos p ON p.codigo=d.codigo;

    -- V5 · hace 3 días 17:10 · tarjeta crédito · con descuento autorizado
    INSERT INTO ven.ventas (cliente_id, almacen_id, fecha, forma_pago_id, usuario_id,
                            descuento_id, notas)
    VALUES (NULL, v_alm_cen,
            ((CURRENT_DATE - 3)::timestamp + interval '17 hours' + interval '10 minutes') AT TIME ZONE 'America/Mexico_City',
            v_fp_tc, v_u_vend,
            (SELECT descuento_id FROM ven.descuentos WHERE codigo='LIQ15'),
            'Descuento LIQ15 aplicado a pintura')
    RETURNING venta_id INTO v_vid;
    INSERT INTO ven.venta_detalles (venta_id, producto_id, cantidad, precio_unitario,
                                    costo_unitario, descuento_linea)
    SELECT v_vid, p.producto_id, d.qty, d.precio, d.costo, d.dto
    FROM (VALUES ('PIN-042',4,429,320,100.00),('BRO-043',4,65,42,0)) d(codigo,qty,precio,costo,dto)
    JOIN inv.productos p ON p.codigo=d.codigo;

    -- V6 · hace 3 días 15:45 · efectivo · tornillería (usará promo Happy Hour)
    INSERT INTO ven.ventas (cliente_id, almacen_id, fecha, forma_pago_id, usuario_id, turno_caja_id)
    VALUES (NULL, v_alm_cen,
            ((CURRENT_DATE - 3)::timestamp + interval '15 hours' + interval '45 minutes') AT TIME ZONE 'America/Mexico_City',
            v_fp_efec, v_u_cajero, NULL)
    RETURNING venta_id INTO v_vid;
    INSERT INTO ven.venta_detalles (venta_id, producto_id, cantidad, precio_unitario, costo_unitario)
    SELECT v_vid, p.producto_id, d.qty, d.precio, d.costo
    FROM (VALUES ('CEM-030',10,245,212),('TOR-040',3,79,55),('CLA-041',8,48,34)) d(codigo,qty,precio,costo)
    JOIN inv.productos p ON p.codigo=d.codigo;

    -- Uso real de la promoción sobre las líneas de tornillería (237+384)*15% = 93.15
    PERFORM ven.fn_registrar_uso_promo(v_promo2, v_vid, NULL, 93.15, v_u_cajero);

    -- V7 · ayer 18:30 · tarjeta débito
    INSERT INTO ven.ventas (cliente_id, almacen_id, fecha, forma_pago_id, usuario_id)
    VALUES (NULL, v_alm_cen,
            ((CURRENT_DATE - 1)::timestamp + interval '18 hours' + interval '30 minutes') AT TIME ZONE 'America/Mexico_City',
            v_fp_td, v_u_cajero);
    INSERT INTO ven.venta_detalles (venta_id, producto_id, cantidad, precio_unitario, costo_unitario)
    SELECT (SELECT venta_id FROM ven.ventas ORDER BY venta_id DESC LIMIT 1),
           p.producto_id, d.qty, d.precio, d.costo
    FROM (VALUES ('BRK-023',5,145,96),('APC-021',8,49,31)) d(codigo,qty,precio,costo)
    JOIN inv.productos p ON p.codigo=d.codigo;

    -- V8 · HOY 10:15 · efectivo · con turno (genera caja)
    INSERT INTO ven.ventas (cliente_id, almacen_id, fecha, forma_pago_id, usuario_id, turno_caja_id)
    VALUES (NULL, v_alm_cen,
            (CURRENT_DATE::timestamp + interval '10 hours' + interval '15 minutes') AT TIME ZONE 'America/Mexico_City',
            v_fp_efec, v_u_cajero, v_turno)
    RETURNING venta_id INTO v_vid;
    INSERT INTO ven.venta_detalles (venta_id, producto_id, cantidad, precio_unitario, costo_unitario)
    SELECT v_vid, p.producto_id, d.qty, d.precio, d.costo
    FROM (VALUES ('MAR-001',1,129,83),('BRO-043',2,65,42),('FOC-022',10,45,28)) d(codigo,qty,precio,costo)
    JOIN inv.productos p ON p.codigo=d.codigo;

    -- V9 · HOY 13:40 · CRÉDITO 30 días · Constructora (línea 150k)
    INSERT INTO ven.ventas (cliente_id, almacen_id, fecha, forma_pago_id, usuario_id, turno_caja_id)
    VALUES (v_cli_constructora, v_alm_cen,
            (CURRENT_DATE::timestamp + interval '13 hours' + interval '40 minutes') AT TIME ZONE 'America/Mexico_City',
            v_fp_cred, v_u_vend, v_turno)
    RETURNING venta_id INTO v_vid;
    INSERT INTO ven.venta_detalles (venta_id, producto_id, cantidad, precio_unitario, costo_unitario)
    SELECT v_vid, p.producto_id, d.qty, d.precio, d.costo
    FROM (VALUES ('TAL-005',3,1249,890),('AMO-007',2,1049,750),
                 ('CEM-030',25,245,212),('PVC-010',60,95,68)) d(codigo,qty,precio,costo)
    JOIN inv.productos p ON p.codigo=d.codigo;

    -- V10 · HOY 17:50 · efectivo · incluye servicio
    INSERT INTO ven.ventas (cliente_id, almacen_id, fecha, forma_pago_id, usuario_id, turno_caja_id)
    VALUES (NULL, v_alm_cen,
            (CURRENT_DATE::timestamp + interval '17 hours' + interval '50 minutes') AT TIME ZONE 'America/Mexico_City',
            v_fp_efec, v_u_cajero, v_turno)
    RETURNING venta_id INTO v_vid;
    INSERT INTO ven.venta_detalles (venta_id, producto_id, cantidad, precio_unitario, costo_unitario)
    SELECT v_vid, p.producto_id, d.qty, d.precio, d.costo
    FROM (VALUES ('SERV-002',3,35,0),('DES-003',2,39,24),('TOR-040',2,79,55)) d(codigo,qty,precio,costo)
    JOIN inv.productos p ON p.codigo=d.codigo;

    -- =====================================================================
    -- 11. ABONO A CRÉDITO de Constructora (efectivo, en caja)
    -- =====================================================================
    SELECT cc.cuenta_cobrar_id INTO v_cc
    FROM ven.cuentas_cobrar cc WHERE cc.cliente_id = v_cli_constructora
    ORDER BY cc.cuenta_cobrar_id DESC LIMIT 1;

    INSERT INTO ven.pagos_cliente (cuenta_cobrar_id, forma_pago_id, referencia,
                                   monto, usuario_id, turno_caja_id)
    VALUES (v_cc, v_fp_efec, 'ABONO-DEMO-001', 5000, v_u_admin, v_turno);

    -- =====================================================================
    -- 12. DEVOLUCIÓN DE CLIENTE (taladro de V2, reembolso a tarjeta)
    -- =====================================================================
    INSERT INTO ven.devoluciones_venta (venta_id, motivo, forma_devolucion_id,
                                        usuario_id, turno_caja_id)
    VALUES ((SELECT MIN(venta_id) FROM ven.ventas WHERE almacen_id = v_alm_cen
             AND EXISTS (SELECT 1 FROM ven.venta_detalles dd
                         JOIN inv.productos p ON p.producto_id = dd.producto_id
                         WHERE dd.venta_id = ven.ventas.venta_id AND p.codigo='TAL-005')),
            'Producto defectuoso de fábrica', v_fp_td, v_u_cajero, v_turno)
    RETURNING devolucion_id INTO v_dev;

    INSERT INTO ven.devolucion_detalles (devolucion_id, venta_detalle_id, producto_id,
                                         cantidad, precio_unitario)
    SELECT v_dev, dd.venta_detalle_id, dd.producto_id, dd.cantidad, dd.precio_unitario
    FROM ven.venta_detalles dd
    JOIN inv.productos p ON p.producto_id = dd.producto_id
    WHERE dd.venta_id = (SELECT venta_id FROM ven.devoluciones_venta
                         WHERE devolucion_id = v_dev)
      AND p.codigo='TAL-005';

    UPDATE ven.devoluciones_venta SET total =
        (SELECT SUM(importe_linea) FROM ven.devolucion_detalles WHERE devolucion_id = v_dev)
    WHERE devolucion_id = v_dev;

    -- Reembolso al cliente (tarjeta: movimiento registrado, sin impacto en efectivo)
    PERFORM fin.fn_movimiento_caja(v_turno, 'SALIDA', 'DEVOLUCION_CLIENTE',
        (SELECT total FROM ven.devoluciones_venta WHERE devolucion_id = v_dev),
        v_fp_td, 'ven.devoluciones_venta', v_dev, v_u_cajero);

    -- =====================================================================
    -- 13. RENTA DE HERRAMIENTA (rotomarto a plomero, depósito en caja)
    -- =====================================================================
    INSERT INTO ven.rentas (cliente_id, almacen_id, fecha_dev_esperada, deposito,
                            estado, usuario_id, turno_caja_id)
    VALUES (v_cli_garcia, v_alm_cen, CURRENT_DATE + 3, 500, 'ABIERTA', v_u_cajero, v_turno)
    RETURNING renta_id INTO v_renta;

    INSERT INTO ven.renta_detalles (renta_id, producto_id, cantidad, costo_dia, dias_cobrados)
    VALUES (v_renta, (SELECT producto_id FROM inv.productos WHERE codigo='REN-001'), 1, 250, 0);

    PERFORM fin.fn_movimiento_caja(v_turno, 'ENTRADA', 'DEPOSITO_GARANTIA_RENTA',
        500, v_fp_efec, 'ven.rentas', v_renta, v_u_cajero);

    -- =====================================================================
    -- 14. GASTOS, OTROS INGRESOS Y NÓMINA
    -- =====================================================================
    INSERT INTO fin.gastos (tipo_gasto_id, descripcion, monto, fecha_gasto, forma_pago_id, usuario_id)
    VALUES ((SELECT tipo_gasto_id FROM cat.tipos_gasto WHERE clave='RENTA_LOCAL'),
            'Renta mensual del local (demo)', 15000, CURRENT_DATE - 5, v_fp_transf, v_u_admin),
           ((SELECT tipo_gasto_id FROM cat.tipos_gasto WHERE clave='LUZ'),
            'Recibo CFE quincena (demo)', 850, CURRENT_DATE, v_fp_efec, v_u_admin),
           ((SELECT tipo_gasto_id FROM cat.tipos_gasto WHERE clave='PAPELERIA'),
            'Bolsas e tickets (demo)', 230, CURRENT_DATE, v_fp_efec, v_u_admin);

    INSERT INTO fin.ingresos_otros (concepto, monto, fecha, forma_pago_id, usuario_id)
    VALUES ('Renta de local anexo (demo)', 8000, CURRENT_DATE - 5, v_fp_transf, v_u_admin);

    INSERT INTO rh.nominas (empleado_id, periodo_ini, periodo_fin, dias_pagados,
                            percepciones, deducciones, estado, fecha_pago,
                            usuario_registra_id, notas)
    VALUES (v_e_cajero, CURRENT_DATE - 6, CURRENT_DATE, 6,
            3200, 480, 'PAGADA', now(), v_u_admin, 'Semana demo')
    RETURNING nomina_id INTO v_nomina;

    PERFORM fin.fn_movimiento_caja(v_turno, 'SALIDA', 'NOMINA',
        (SELECT neto_pagar FROM rh.nominas WHERE nomina_id = v_nomina),
        v_fp_efec, 'rh.nominas', v_nomina, v_u_admin);

    -- =====================================================================
    -- 15. CONTEO FÍSICO en proceso (sin aplicar)
    -- =====================================================================
    DECLARE c INT;
    BEGIN
        INSERT INTO inv.conteos_fisicos (almacen_id, usuario_id, observaciones)
        VALUES (v_alm_cen, v_u_admin, 'Conteo demo en proceso');

        INSERT INTO inv.conteos_fisicos_detalle
            (conteo_id, producto_id, cantidad_sistema, cantidad_fisica)
        SELECT (SELECT MAX(conteo_id) FROM inv.conteos_fisicos),
               p.producto_id, i.stock, i.stock - 2
        FROM inv.inventario i JOIN inv.productos p ON p.producto_id=i.producto_id
        WHERE p.codigo='FOC-022' AND i.almacen_id=v_alm_cen;

        INSERT INTO inv.conteos_fisicos_detalle
            (conteo_id, producto_id, cantidad_sistema, cantidad_fisica)
        SELECT (SELECT MAX(conteo_id) FROM inv.conteos_fisicos),
               p.producto_id, i.stock, i.stock + 1
        FROM inv.inventario i JOIN inv.productos p ON p.producto_id=i.producto_id
        WHERE p.codigo='CLA-041' AND i.almacen_id=v_alm_cen;
    END;

    RAISE NOTICE '==========================================================';
    RAISE NOTICE 'Datos demo cargados correctamente.';
    RAISE NOTICE '  Ventas: %  |  Compras: %  |  Turno abierto: %',
        (SELECT COUNT(*) FROM ven.ventas), (SELECT COUNT(*) FROM com.compras), v_turno;
    RAISE NOTICE 'Pruebe las vistas: ven.vw_resumen_dashboard, fin.vw_dinero_en_caja,';
    RAISE NOTICE 'com.vw_facturas_vencidas, ven.vw_top_productos, inv.vw_stock_bajo...';
    RAISE NOTICE '==========================================================';
END $$;

-- Refrescar estadísticas para que el planner aproveche los índices
ANALYZE;

-- ============================================================================
-- MUESTRA DE RESULTADOS (verificación visual al ejecutar)
-- ============================================================================
SELECT * FROM ven.vw_resumen_dashboard;
SELECT * FROM fin.vw_dinero_en_caja LIMIT 5;
SELECT * FROM com.vw_facturas_pendientes;
SELECT * FROM ven.vw_top_productos ORDER BY ranking_mes LIMIT 10;
