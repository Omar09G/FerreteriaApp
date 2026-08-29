-- ============================================================================
-- 02_tablas.sql
-- Sistema Integral de Ferretería · Paso 2/5
-- Tablas, índices, FKs cruzadas, funciones, triggers y permisos (PostgreSQL 14+)
--
-- Ejecutar DESPUÉS de 01_base_esquemas.sql:
--   psql -U postgres -d ferreteria -f 02_tablas.sql
--
-- ORDEN DE CREACIÓN (resuelve dependencias):
--   cat → cfg → com.proveedores → rh.empleados → seg → rh.nominas → inv
--   → fis → ven → fin → FKs cruzadas → funciones/triggers → GRANTs
-- ============================================================================

SET timezone TO 'America/Mexico_City';

-- ============================================================================
-- A. MÓDULO cat — Catálogos
-- ============================================================================
CREATE TABLE IF NOT EXISTS cat.unidades_medida (
    unidad_id        INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    clave            VARCHAR(10)  NOT NULL UNIQUE,
    nombre           VARCHAR(50)  NOT NULL,
    permite_fraccion BOOLEAN      NOT NULL DEFAULT false,
    activo           BOOLEAN      NOT NULL DEFAULT true
);

CREATE TABLE IF NOT EXISTS cat.marcas (
    marca_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre   VARCHAR(100) NOT NULL UNIQUE,
    activo   BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE IF NOT EXISTS cat.categorias (
    categoria_id       INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre             VARCHAR(100) NOT NULL,
    categoria_padre_id INTEGER REFERENCES cat.categorias(categoria_id),
    ruta               TEXT,
    nivel              SMALLINT NOT NULL DEFAULT 0 CHECK (nivel BETWEEN 0 AND 5),
    activo             BOOLEAN NOT NULL DEFAULT true,
    CONSTRAINT chk_cat_no_auto_padre CHECK (categoria_id <> categoria_padre_id)
);
CREATE INDEX IF NOT EXISTS idx_categorias_padre ON cat.categorias(categoria_padre_id);

CREATE TABLE IF NOT EXISTS cat.formas_pago (
    forma_pago_id       INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    clave               VARCHAR(25) NOT NULL UNIQUE,
    nombre              VARCHAR(60) NOT NULL,
    es_efectivo         BOOLEAN NOT NULL DEFAULT false,
    requiere_referencia BOOLEAN NOT NULL DEFAULT false,
    afecta_caja         BOOLEAN NOT NULL DEFAULT true,
    activo              BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE IF NOT EXISTS cat.motivos_movimiento (
    motivo_id    INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    clave        VARCHAR(30) NOT NULL UNIQUE,
    nombre       VARCHAR(80) NOT NULL,
    tipo_default VARCHAR(8) NOT NULL CHECK (tipo_default IN ('ENTRADA','SALIDA')),
    activo       BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE IF NOT EXISTS cat.tipos_gasto (
    tipo_gasto_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    clave         VARCHAR(30) NOT NULL UNIQUE,
    nombre        VARCHAR(80) NOT NULL,
    es_fijo       BOOLEAN NOT NULL DEFAULT false,
    activo        BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE IF NOT EXISTS cat.puestos (
    puesto_id   INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre      VARCHAR(80) NOT NULL UNIQUE,
    sueldo_base NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (sueldo_base >= 0),
    activo      BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE IF NOT EXISTS cat.estados (
    estado_id   INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    clave_inegi VARCHAR(3) NOT NULL UNIQUE,
    nombre      VARCHAR(60) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS cat.ciudades (
    ciudad_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    estado_id INTEGER NOT NULL REFERENCES cat.estados(estado_id),
    nombre    VARCHAR(100) NOT NULL,
    UNIQUE (estado_id, nombre)
);
CREATE INDEX IF NOT EXISTS idx_ciudades_estado ON cat.ciudades(estado_id);

-- ============================================================================
-- B. MÓDULO com.proveedores (temprano: lo referencia inv.producto_proveedores)
-- ============================================================================
CREATE TABLE IF NOT EXISTS com.proveedores (
    proveedor_id    INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    razon_social    VARCHAR(180) NOT NULL,
    rfc             VARCHAR(13) CHECK (rfc ~* '^[A-ZÑ&]{3,4}[0-9]{6}[A-V1-9][0-9A-Z]{2}$'),
    regimen_fiscal  VARCHAR(10),
    contacto_nombre VARCHAR(120),
    telefono        VARCHAR(20),
    email           VARCHAR(120),
    calle           VARCHAR(150),
    colonia         VARCHAR(100),
    ciudad_id       INTEGER REFERENCES cat.ciudades(ciudad_id),
    cp              VARCHAR(10),
    dias_credito    SMALLINT NOT NULL DEFAULT 0 CHECK (dias_credito BETWEEN 0 AND 365),
    limite_credito  NUMERIC(12,2) NOT NULL DEFAULT 0,
    activo          BOOLEAN NOT NULL DEFAULT true,
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_proveedor_razon UNIQUE (razon_social)
);

-- ============================================================================
-- C. MÓDULO cfg — Configuración y folios
-- ============================================================================
CREATE TABLE IF NOT EXISTS cfg.configuracion (
    clave          VARCHAR(60) PRIMARY KEY,
    valor          TEXT NOT NULL,
    descripcion    VARCHAR(200),
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS cfg.folios (
    tipo           VARCHAR(25) PRIMARY KEY,
    prefijo        VARCHAR(6)  NOT NULL,
    consecutivo    BIGINT      NOT NULL DEFAULT 0,
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================================
-- D. MÓDULO rh.empleados (antes de seg.usuarios)
-- ============================================================================
CREATE TABLE IF NOT EXISTS rh.empleados (
    empleado_id   INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    puesto_id     INTEGER NOT NULL REFERENCES cat.puestos(puesto_id),
    nombre        VARCHAR(80)  NOT NULL,
    apellido_p    VARCHAR(80)  NOT NULL,
    apellido_m    VARCHAR(80),
    curp          VARCHAR(18)  UNIQUE,
    nss           VARCHAR(11)  UNIQUE,
    telefono      VARCHAR(20),
    email         VARCHAR(120) UNIQUE,
    calle         VARCHAR(150),
    colonia       VARCHAR(100),
    ciudad_id     INTEGER REFERENCES cat.ciudades(ciudad_id),
    cp            VARCHAR(10),
    fecha_ingreso DATE NOT NULL DEFAULT CURRENT_DATE,
    fecha_baja    DATE,
    sueldo_diario NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (sueldo_diario >= 0),
    activo        BOOLEAN NOT NULL DEFAULT true,
    CONSTRAINT chk_empleado_baja CHECK (fecha_baja IS NULL OR fecha_baja >= fecha_ingreso)
);
CREATE INDEX IF NOT EXISTS idx_empleados_puesto ON rh.empleados(puesto_id);

-- ============================================================================
-- E. MÓDULO seg — Seguridad y auditoría
-- ============================================================================
CREATE TABLE IF NOT EXISTS seg.roles (
    rol_id      INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    clave       VARCHAR(30) NOT NULL UNIQUE,
    nombre      VARCHAR(80) NOT NULL,
    descripcion VARCHAR(200),
    activo      BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE IF NOT EXISTS seg.permisos (
    permiso_id  INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    clave       VARCHAR(40) NOT NULL UNIQUE,
    descripcion VARCHAR(150) NOT NULL
);

CREATE TABLE IF NOT EXISTS seg.usuarios (
    usuario_id    INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    empleado_id   INTEGER UNIQUE REFERENCES rh.empleados(empleado_id),
    username      VARCHAR(40)  NOT NULL UNIQUE,
    email         VARCHAR(120) UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    activo        BOOLEAN NOT NULL DEFAULT true,
    ultimo_login  TIMESTAMPTZ,
    creado_en     TIMESTAMPTZ NOT NULL DEFAULT now(),
    eliminado_en  TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS seg.usuario_roles (
    usuario_id INTEGER NOT NULL REFERENCES seg.usuarios(usuario_id) ON DELETE CASCADE,
    rol_id     INTEGER NOT NULL REFERENCES seg.roles(rol_id),
    PRIMARY KEY (usuario_id, rol_id)
);

CREATE TABLE IF NOT EXISTS seg.rol_permisos (
    rol_id     INTEGER NOT NULL REFERENCES seg.roles(rol_id) ON DELETE CASCADE,
    permiso_id INTEGER NOT NULL REFERENCES seg.permisos(permiso_id) ON DELETE CASCADE,
    PRIMARY KEY (rol_id, permiso_id)
);

CREATE TABLE IF NOT EXISTS seg.sesiones (
    sesion_id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id         INTEGER NOT NULL REFERENCES seg.usuarios(usuario_id),
    ip_address         INET,
    user_agent         TEXT,
    inicio             TIMESTAMPTZ NOT NULL DEFAULT now(),
    fin                TIMESTAMPTZ,
    cerrada_por_logout BOOLEAN NOT NULL DEFAULT false
);
CREATE INDEX IF NOT EXISTS idx_sesiones_usuario ON seg.sesiones(usuario_id, inicio DESC);

CREATE TABLE IF NOT EXISTS seg.auditoria (
    auditoria_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    esquema          VARCHAR(40) NOT NULL,
    tabla            VARCHAR(60) NOT NULL,
    registro_id      BIGINT      NOT NULL,
    accion           VARCHAR(8)  NOT NULL CHECK (accion IN ('INSERT','UPDATE','DELETE')),
    datos_anteriores JSONB,
    datos_nuevos     JSONB,
    usuario_id       INTEGER REFERENCES seg.usuarios(usuario_id),
    creado_en        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_auditoria_tabla_registro ON seg.auditoria(esquema, tabla, registro_id);
CREATE INDEX IF NOT EXISTS idx_auditoria_fecha ON seg.auditoria(creado_en DESC);

-- ============================================================================
-- F. MÓDULO rh.nominas (después de seg.usuarios)
-- ============================================================================
CREATE TABLE IF NOT EXISTS rh.nominas (
    nomina_id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    empleado_id         INTEGER NOT NULL REFERENCES rh.empleados(empleado_id),
    periodo_ini         DATE NOT NULL,
    periodo_fin         DATE NOT NULL,
    dias_pagados        NUMERIC(4,1) NOT NULL CHECK (dias_pagados > 0),
    percepciones        NUMERIC(12,2) NOT NULL DEFAULT 0,
    deducciones         NUMERIC(12,2) NOT NULL DEFAULT 0,
    neto_pagar          NUMERIC(12,2) GENERATED ALWAYS AS (percepciones - deducciones) STORED,
    estado              VARCHAR(12) NOT NULL DEFAULT 'PENDIENTE'
                        CHECK (estado IN ('PENDIENTE','PAGADA','CANCELADA')),
    fecha_pago          TIMESTAMPTZ,
    usuario_registra_id INTEGER NOT NULL REFERENCES seg.usuarios(usuario_id),
    notas               TEXT,
    CONSTRAINT chk_nomina_periodo CHECK (periodo_fin >= periodo_ini),
    CONSTRAINT uq_nomina_empleado_periodo UNIQUE (empleado_id, periodo_ini, periodo_fin)
);
CREATE INDEX IF NOT EXISTS idx_nominas_estado_fecha ON rh.nominas(estado, periodo_fin);

-- ============================================================================
-- G. MÓDULO inv — Productos, almacenes e inventario
-- ============================================================================
CREATE TABLE IF NOT EXISTS inv.productos (
    producto_id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo              VARCHAR(40) UNIQUE,
    tipo                VARCHAR(20) NOT NULL DEFAULT 'PRODUCTO'
                        CHECK (tipo IN ('PRODUCTO','SERVICIO','HERRAMIENTA_RENTA')),
    nombre              VARCHAR(180) NOT NULL,
    descripcion         TEXT,
    categoria_id        INTEGER NOT NULL REFERENCES cat.categorias(categoria_id),
    marca_id            INTEGER REFERENCES cat.marcas(marca_id),
    unidad_medida_id    INTEGER NOT NULL REFERENCES cat.unidades_medida(unidad_id),
    costo_actual        NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (costo_actual >= 0),
    precio_menudeo      NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (precio_menudeo >= 0),
    precio_mayoreo      NUMERIC(12,2),
    mayoreo_desde       NUMERIC(12,3),
    aplica_iva          BOOLEAN NOT NULL DEFAULT true,
    stock_minimo_global NUMERIC(12,3) DEFAULT 0,
    ubicacion_almacen   VARCHAR(40),
    atributos           JSONB,
    imagen_url          TEXT,
    activo              BOOLEAN NOT NULL DEFAULT true,
    creado_en           TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_productos_categoria ON inv.productos(categoria_id);
CREATE INDEX IF NOT EXISTS idx_productos_marca     ON inv.productos(marca_id);
CREATE INDEX IF NOT EXISTS idx_productos_nombre_trgm ON inv.productos USING GIN (nombre gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_productos_activos ON inv.productos(categoria_id) WHERE activo;

CREATE TABLE IF NOT EXISTS inv.producto_codigos_barras (
    codigo_barras VARCHAR(50) PRIMARY KEY,
    producto_id   BIGINT NOT NULL REFERENCES inv.productos(producto_id) ON DELETE CASCADE,
    factor        NUMERIC(12,3) NOT NULL DEFAULT 1 CHECK (factor > 0)
);

CREATE TABLE IF NOT EXISTS inv.producto_proveedores (
    producto_id        BIGINT NOT NULL REFERENCES inv.productos(producto_id) ON DELETE CASCADE,
    proveedor_id       INTEGER NOT NULL REFERENCES com.proveedores(proveedor_id),
    costo_ref          NUMERIC(12,2),
    tiempo_entrega_dias SMALLINT,
    codigo_proveedor   VARCHAR(40),
    es_principal       BOOLEAN NOT NULL DEFAULT false,
    PRIMARY KEY (producto_id, proveedor_id)
);

CREATE TABLE IF NOT EXISTS inv.almacenes (
    almacen_id     INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre         VARCHAR(100) NOT NULL UNIQUE,
    direccion      TEXT,
    telefono       VARCHAR(20),
    es_punto_venta BOOLEAN NOT NULL DEFAULT true,
    activo         BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE IF NOT EXISTS inv.inventario (
    producto_id    BIGINT  NOT NULL REFERENCES inv.productos(producto_id),
    almacen_id     INTEGER NOT NULL REFERENCES inv.almacenes(almacen_id),
    stock          NUMERIC(12,3) NOT NULL DEFAULT 0,
    stock_minimo   NUMERIC(12,3) NOT NULL DEFAULT 0,
    stock_maximo   NUMERIC(12,3),
    reservado      NUMERIC(12,3) NOT NULL DEFAULT 0,
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (producto_id, almacen_id)
    -- La no-negatividad se valida en el trigger del kardex leyendo
    -- cfg.configuracion.permitir_stock_negativo
);
CREATE INDEX IF NOT EXISTS idx_inventario_almacen_stock ON inv.inventario(almacen_id, stock);

CREATE TABLE IF NOT EXISTS inv.movimientos_inventario (
    movimiento_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    producto_id    BIGINT  NOT NULL REFERENCES inv.productos(producto_id),
    almacen_id     INTEGER NOT NULL REFERENCES inv.almacenes(almacen_id),
    tipo           VARCHAR(8) NOT NULL CHECK (tipo IN ('ENTRADA','SALIDA')),
    cantidad       NUMERIC(12,3) NOT NULL CHECK (cantidad > 0),
    costo_unitario NUMERIC(12,2),
    motivo_id      INTEGER NOT NULL REFERENCES cat.motivos_movimiento(motivo_id),
    ref_tabla      VARCHAR(40),
    ref_id         BIGINT,
    traslado_id    BIGINT,
    nota           TEXT,
    usuario_id     INTEGER REFERENCES seg.usuarios(usuario_id),
    creado_en      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_mov_inv_producto_fecha ON inv.movimientos_inventario(producto_id, creado_en DESC);
CREATE INDEX IF NOT EXISTS idx_mov_inv_documento      ON inv.movimientos_inventario(ref_tabla, ref_id);
CREATE INDEX IF NOT EXISTS idx_mov_inv_almacen_fecha  ON inv.movimientos_inventario(almacen_id, creado_en DESC);

CREATE TABLE IF NOT EXISTS inv.conteos_fisicos (
    conteo_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    almacen_id    INTEGER NOT NULL REFERENCES inv.almacenes(almacen_id),
    fecha         TIMESTAMPTZ NOT NULL DEFAULT now(),
    estado        VARCHAR(12) NOT NULL DEFAULT 'EN_PROCESO'
                  CHECK (estado IN ('EN_PROCESO','APLICADO','CANCELADO')),
    usuario_id    INTEGER NOT NULL REFERENCES seg.usuarios(usuario_id),
    observaciones TEXT
);

CREATE TABLE IF NOT EXISTS inv.conteos_fisicos_detalle (
    conteo_id        BIGINT NOT NULL REFERENCES inv.conteos_fisicos(conteo_id) ON DELETE CASCADE,
    producto_id      BIGINT NOT NULL REFERENCES inv.productos(producto_id),
    cantidad_sistema NUMERIC(12,3) NOT NULL,
    cantidad_fisica  NUMERIC(12,3) NOT NULL,
    diferencia       NUMERIC(12,3) GENERATED ALWAYS AS (cantidad_fisica - cantidad_sistema) STORED,
    PRIMARY KEY (conteo_id, producto_id)
);

CREATE TABLE IF NOT EXISTS inv.traslados (
    traslado_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    folio           TEXT NOT NULL UNIQUE,
    almacen_origen  INTEGER NOT NULL REFERENCES inv.almacenes(almacen_id),
    almacen_destino INTEGER NOT NULL REFERENCES inv.almacenes(almacen_id),
    estado          VARCHAR(12) NOT NULL DEFAULT 'APLICADO'
                    CHECK (estado IN ('APLICADO','CANCELADO')),
    usuario_id      INTEGER NOT NULL REFERENCES seg.usuarios(usuario_id),
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_traslado_distinto CHECK (almacen_origen <> almacen_destino)
);

CREATE TABLE IF NOT EXISTS inv.traslado_detalles (
    traslado_id BIGINT NOT NULL REFERENCES inv.traslados(traslado_id) ON DELETE CASCADE,
    producto_id BIGINT NOT NULL REFERENCES inv.productos(producto_id),
    cantidad    NUMERIC(12,3) NOT NULL CHECK (cantidad > 0),
    PRIMARY KEY (traslado_id, producto_id)
);

-- ============================================================================
-- H. MÓDULO fis — Fiscal (catálogos SAT e impuestos)
-- ============================================================================
CREATE TABLE IF NOT EXISTS fis.impuestos (
    impuesto_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    clave_sat   VARCHAR(5) NOT NULL UNIQUE,
    nombre      VARCHAR(60) NOT NULL,
    tipo        VARCHAR(10) NOT NULL CHECK (tipo IN ('TRASLADADO','RETENIDO','LOCAL')),
    activo      BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE IF NOT EXISTS fis.tasas_impuesto (
    tasa_id       INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    impuesto_id   INTEGER NOT NULL REFERENCES fis.impuestos(impuesto_id),
    tasa          NUMERIC(6,4) NOT NULL CHECK (tasa >= 0),
    factor        VARCHAR(8)  NOT NULL DEFAULT 'TASA' CHECK (factor IN ('TASA','CUOTA','EXENTO')),
    ambito        VARCHAR(10) NOT NULL DEFAULT 'VENTA' CHECK (ambito IN ('VENTA','COMPRA','NOMINA')),
    zona_frontera BOOLEAN NOT NULL DEFAULT false,
    vigente_desde DATE NOT NULL DEFAULT CURRENT_DATE,
    vigente_hasta DATE,
    activo        BOOLEAN NOT NULL DEFAULT true,
    UNIQUE (impuesto_id, tasa, factor, ambito, zona_frontera, vigente_desde),
    CONSTRAINT chk_tasa_vigencia CHECK (vigente_hasta IS NULL OR vigente_hasta >= vigente_desde)
);

CREATE TABLE IF NOT EXISTS fis.regimenes_fiscales (
    clave_sat      VARCHAR(3) PRIMARY KEY,
    descripcion    VARCHAR(120) NOT NULL,
    persona_fisica BOOLEAN NOT NULL DEFAULT true,
    persona_moral  BOOLEAN NOT NULL DEFAULT true,
    activo         BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE IF NOT EXISTS fis.usos_cfdi (
    clave         VARCHAR(4) PRIMARY KEY,
    descripcion   VARCHAR(150) NOT NULL,
    aplica_fisica BOOLEAN NOT NULL DEFAULT true,
    aplica_moral  BOOLEAN NOT NULL DEFAULT true,
    activo        BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE IF NOT EXISTS fis.formas_pago_sat (
    clave       VARCHAR(2) PRIMARY KEY,
    descripcion VARCHAR(80) NOT NULL,
    activo      BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE IF NOT EXISTS fis.metodos_pago_sat (
    clave       VARCHAR(3) PRIMARY KEY,
    descripcion VARCHAR(60) NOT NULL,
    activo      BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE IF NOT EXISTS fis.unidades_sat (
    clave       VARCHAR(4) PRIMARY KEY,
    descripcion VARCHAR(80) NOT NULL,
    activo      BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE IF NOT EXISTS fis.claves_prod_serv (
    clave       VARCHAR(8) PRIMARY KEY,
    descripcion TEXT NOT NULL,
    incluye_iva BOOLEAN,
    ejemplo     BOOLEAN NOT NULL DEFAULT false
);

CREATE TABLE IF NOT EXISTS fis.producto_impuesto (
    producto_id BIGINT NOT NULL REFERENCES inv.productos(producto_id) ON DELETE CASCADE,
    tasa_id     INTEGER NOT NULL REFERENCES fis.tasas_impuesto(tasa_id),
    es_default  BOOLEAN NOT NULL DEFAULT false,
    PRIMARY KEY (producto_id, tasa_id)
);
CREATE INDEX IF NOT EXISTS idx_producto_impuesto_default
    ON fis.producto_impuesto(producto_id) WHERE es_default;

-- Integración fiscal con formas de pago
ALTER TABLE cat.formas_pago
    ADD COLUMN IF NOT EXISTS forma_pago_sat VARCHAR(2) REFERENCES fis.formas_pago_sat(clave),
    ADD COLUMN IF NOT EXISTS comision_pct   NUMERIC(5,2) NOT NULL DEFAULT 0
        CHECK (comision_pct BETWEEN 0 AND 100);

-- CFDI persistido (facturas emitidas/recibidas). El timbrado PAC real es
-- integración futura: hoy la API persiste y consulta. Total calculado por BD.
CREATE TABLE IF NOT EXISTS fis.facturas (
    factura_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tipo           VARCHAR(10) NOT NULL CHECK (tipo IN ('EMITIDA','RECIBIDA')),
    serie          VARCHAR(20),
    folio          VARCHAR(40) NOT NULL,
    uuid           VARCHAR(64) UNIQUE,
    emisor_rfc     VARCHAR(13) NOT NULL,
    receptor_rfc   VARCHAR(13) NOT NULL,
    subtotal       NUMERIC(14,2) NOT NULL DEFAULT 0,
    iva            NUMERIC(14,2) NOT NULL DEFAULT 0,
    total          NUMERIC(14,2) GENERATED ALWAYS AS (subtotal + iva) STORED,
    fecha_timbrado TIMESTAMPTZ NOT NULL DEFAULT now(),
    cfdi_xml       TEXT,
    estado         VARCHAR(12) NOT NULL DEFAULT 'ACTIVA'
                   CHECK (estado IN ('ACTIVA','CANCELADA')),
    venta_id       BIGINT,
    usuario_id     INTEGER NOT NULL REFERENCES seg.usuarios(usuario_id),
    creado_en      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_factura_totales CHECK (subtotal >= 0 AND iva >= 0)
);
CREATE INDEX IF NOT EXISTS idx_facturas_tipo_fecha ON fis.facturas(tipo, fecha_timbrado DESC);
CREATE INDEX IF NOT EXISTS idx_facturas_venta ON fis.facturas(venta_id)
    WHERE venta_id IS NOT NULL;
COMMENT ON TABLE fis.facturas IS
'CFDI emitidos/recibidos persistidos para consulta. Timbrado PAC queda como integración futura (M6/M7).';

-- ============================================================================
-- I. MÓDULO ven — Ventas, cotizaciones, devoluciones, rentas, cobranza,
--                 líneas de crédito, descuentos y promociones
-- ============================================================================
CREATE TABLE IF NOT EXISTS ven.clientes (
    cliente_id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tipo_persona    VARCHAR(10) NOT NULL DEFAULT 'FISICA'
                    CHECK (tipo_persona IN ('FISICA','MORAL')),
    razon_social    VARCHAR(180) NOT NULL,
    nombre_comercial VARCHAR(180),
    rfc             VARCHAR(13) CHECK (rfc ~* '^[A-ZÑ&]{3,4}[0-9]{6}[A-V1-9][0-9A-Z]{2}$'),
    curp            VARCHAR(18),
    regimen_fiscal  VARCHAR(10),
    telefono        VARCHAR(20),
    whatsapp        VARCHAR(20),
    email           VARCHAR(120),
    calle           VARCHAR(150),
    colonia         VARCHAR(100),
    ciudad_id       INTEGER REFERENCES cat.ciudades(ciudad_id),
    cp              VARCHAR(10),
    limite_credito  NUMERIC(12,2) NOT NULL DEFAULT 0,
    dias_credito    SMALLINT NOT NULL DEFAULT 0 CHECK (dias_credito BETWEEN 0 AND 365),
    es_mayorista    BOOLEAN NOT NULL DEFAULT false,
    descuento_pct   NUMERIC(5,2) NOT NULL DEFAULT 0 CHECK (descuento_pct BETWEEN 0 AND 100),
    notas           TEXT,
    activo          BOOLEAN NOT NULL DEFAULT true,
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_clientes_razon_trgm ON ven.clientes USING GIN (razon_social gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_clientes_telefono ON ven.clientes(telefono);

CREATE TABLE IF NOT EXISTS ven.descuentos (
    descuento_id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo                VARCHAR(30) UNIQUE,
    nombre                VARCHAR(120) NOT NULL,
    tipo                  VARCHAR(12) NOT NULL CHECK (tipo IN ('PORCENTAJE','MONTO_FIJO')),
    valor                 NUMERIC(12,2) NOT NULL CHECK (valor > 0),
    aplica_a              VARCHAR(8)  NOT NULL DEFAULT 'VENTA'
                          CHECK (aplica_a IN ('VENTA','LINEA','CLIENTE')),
    requiere_autorizacion BOOLEAN NOT NULL DEFAULT true,
    vigencia_desde        DATE NOT NULL DEFAULT CURRENT_DATE,
    vigencia_hasta        DATE,
    activo                BOOLEAN NOT NULL DEFAULT true,
    usuario_id            INTEGER NOT NULL REFERENCES seg.usuarios(usuario_id),
    creado_en             TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS ven.promociones (
    promocion_id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre              VARCHAR(150) NOT NULL,
    descripcion         TEXT,
    tipo                VARCHAR(25) NOT NULL CHECK (tipo IN (
                         'DESCUENTO_PRODUCTO','DESCUENTO_TOTAL_VENTA',
                         'POR_CANTIDAD','NXM','PRECIO_ESPECIAL')),
    valor_pct           NUMERIC(5,2)  CHECK (valor_pct BETWEEN 0 AND 100),
    valor_monto         NUMERIC(12,2) CHECK (valor_monto >= 0),
    precio_especial     NUMERIC(12,2) CHECK (precio_especial >= 0),
    compra_min_total    NUMERIC(14,2),
    compra_min_cantidad NUMERIC(12,3),
    lleva               NUMERIC(12,3),
    paga                NUMERIC(12,3),
    max_usos_total      INTEGER,
    max_usos_cliente    INTEGER,
    usos_actual         INTEGER NOT NULL DEFAULT 0,
    vigencia_desde      TIMESTAMPTZ NOT NULL DEFAULT now(),
    vigencia_hasta      TIMESTAMPTZ,
    dias_semana         SMALLINT[] NOT NULL DEFAULT '{1,2,3,4,5,6,7}',
    hora_desde          TIME,
    hora_hasta          TIME,
    solo_mayoristas     BOOLEAN NOT NULL DEFAULT false,
    estado              VARCHAR(12) NOT NULL DEFAULT 'ACTIVA'
                        CHECK (estado IN ('ACTIVA','PROGRAMADA','FINALIZADA','CANCELADA')),
    usuario_id          INTEGER NOT NULL REFERENCES seg.usuarios(usuario_id),
    creado_en           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_nxm_coherente CHECK
        (tipo <> 'NXM' OR (lleva IS NOT NULL AND paga IS NOT NULL AND paga < lleva)),
    CONSTRAINT chk_promo_con_valor CHECK
        (valor_pct IS NOT NULL OR valor_monto IS NOT NULL
         OR precio_especial IS NOT NULL OR tipo = 'NXM')
);
CREATE INDEX IF NOT EXISTS idx_promos_activas ON ven.promociones(estado, vigencia_desde)
    WHERE estado IN ('ACTIVA','PROGRAMADA');
CREATE INDEX IF NOT EXISTS idx_promos_dias ON ven.promociones USING GIN (dias_semana);

CREATE TABLE IF NOT EXISTS ven.promocion_productos (
    promocion_id BIGINT NOT NULL REFERENCES ven.promociones(promocion_id) ON DELETE CASCADE,
    producto_id  BIGINT NOT NULL REFERENCES inv.productos(producto_id),
    PRIMARY KEY (promocion_id, producto_id)
);

CREATE TABLE IF NOT EXISTS ven.promocion_categorias (
    promocion_id BIGINT NOT NULL REFERENCES ven.promociones(promocion_id) ON DELETE CASCADE,
    categoria_id INTEGER NOT NULL REFERENCES cat.categorias(categoria_id),
    PRIMARY KEY (promocion_id, categoria_id)
);

CREATE TABLE IF NOT EXISTS ven.promocion_usos (
    uso_id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    promocion_id    BIGINT NOT NULL REFERENCES ven.promociones(promocion_id),
    venta_id        BIGINT,
    cliente_id      BIGINT REFERENCES ven.clientes(cliente_id),
    monto_descuento NUMERIC(12,2) NOT NULL CHECK (monto_descuento >= 0),
    usuario_id      INTEGER REFERENCES seg.usuarios(usuario_id),
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (promocion_id, venta_id)
);
CREATE INDEX IF NOT EXISTS idx_promo_usos_promo   ON ven.promocion_usos(promocion_id);
CREATE INDEX IF NOT EXISTS idx_promo_usos_cliente ON ven.promocion_usos(cliente_id);

CREATE TABLE IF NOT EXISTS ven.lineas_credito (
    linea_credito_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cliente_id          BIGINT NOT NULL REFERENCES ven.clientes(cliente_id),
    monto_autorizado    NUMERIC(12,2) NOT NULL CHECK (monto_autorizado > 0),
    dias_credito        SMALLINT NOT NULL DEFAULT 15 CHECK (dias_credito BETWEEN 1 AND 365),
    tasa_moratorio      NUMERIC(5,2)  NOT NULL DEFAULT 0,
    fecha_autorizacion  TIMESTAMPTZ NOT NULL DEFAULT now(),
    vigente_hasta       DATE,
    usuario_autorizo_id INTEGER NOT NULL REFERENCES seg.usuarios(usuario_id),
    estado              VARCHAR(12) NOT NULL DEFAULT 'ACTIVA'
                        CHECK (estado IN ('ACTIVA','SUSPENDIDA','CANCELADA','VENCIDA')),
    observaciones       TEXT
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_linea_activa_por_cliente
    ON ven.lineas_credito(cliente_id) WHERE estado = 'ACTIVA';

CREATE TABLE IF NOT EXISTS ven.cotizaciones (
    cotizacion_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    folio             TEXT NOT NULL UNIQUE,
    cliente_id        BIGINT REFERENCES ven.clientes(cliente_id),
    fecha             TIMESTAMPTZ NOT NULL DEFAULT now(),
    vigencia_hasta    DATE,
    subtotal          NUMERIC(14,2) NOT NULL DEFAULT 0,
    iva               NUMERIC(14,2) NOT NULL DEFAULT 0,
    total             NUMERIC(14,2) NOT NULL DEFAULT 0,
    estado            VARCHAR(12) NOT NULL DEFAULT 'VIGENTE'
                      CHECK (estado IN ('VIGENTE','CONVERTIDA','EXPIRADA','CANCELADA')),
    venta_generada_id BIGINT,
    usuario_id        INTEGER NOT NULL REFERENCES seg.usuarios(usuario_id)
);

CREATE TABLE IF NOT EXISTS ven.cotizacion_detalles (
    cotizacion_id   BIGINT NOT NULL REFERENCES ven.cotizaciones(cotizacion_id) ON DELETE CASCADE,
    producto_id     BIGINT NOT NULL REFERENCES inv.productos(producto_id),
    cantidad        NUMERIC(12,3) NOT NULL CHECK (cantidad > 0),
    precio_unitario NUMERIC(12,2) NOT NULL,
    importe_linea   NUMERIC(14,2) GENERATED ALWAYS AS (cantidad * precio_unitario) STORED,
    PRIMARY KEY (cotizacion_id, producto_id)
);

CREATE TABLE IF NOT EXISTS ven.ventas (
    venta_id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    folio           TEXT NOT NULL UNIQUE,
    cliente_id      BIGINT REFERENCES ven.clientes(cliente_id),
    almacen_id      INTEGER NOT NULL REFERENCES inv.almacenes(almacen_id),
    cotizacion_id   BIGINT REFERENCES ven.cotizaciones(cotizacion_id),
    fecha           TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_local     DATE GENERATED ALWAYS AS
                    ((fecha AT TIME ZONE 'America/Mexico_City')::date) STORED,
    forma_pago_id   INTEGER NOT NULL REFERENCES cat.formas_pago(forma_pago_id),
    iva_tasa        NUMERIC(5,2)  NOT NULL DEFAULT 16.00,
    iva_incluido    BOOLEAN NOT NULL DEFAULT true,
    subtotal        NUMERIC(14,2) NOT NULL DEFAULT 0,
    iva             NUMERIC(14,2) NOT NULL DEFAULT 0,
    descuento_total NUMERIC(14,2) NOT NULL DEFAULT 0,
    total           NUMERIC(14,2) NOT NULL DEFAULT 0,
    estado          VARCHAR(12) NOT NULL DEFAULT 'COMPLETADA'
                    CHECK (estado IN ('COMPLETADA','CANCELADA')),
    usuario_id      INTEGER NOT NULL REFERENCES seg.usuarios(usuario_id),
    turno_caja_id   BIGINT,
    notas           TEXT
);
CREATE INDEX IF NOT EXISTS idx_ventas_fecha ON ven.ventas(fecha DESC);
CREATE INDEX IF NOT EXISTS idx_ventas_turno ON ven.ventas(turno_caja_id);
CREATE INDEX IF NOT EXISTS idx_ventas_cliente_fecha ON ven.ventas(cliente_id, fecha DESC)
    WHERE estado = 'COMPLETADA';

CREATE TABLE IF NOT EXISTS ven.venta_detalles (
    venta_detalle_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    venta_id         BIGINT NOT NULL REFERENCES ven.ventas(venta_id) ON DELETE CASCADE,
    producto_id      BIGINT NOT NULL REFERENCES inv.productos(producto_id),
    cantidad         NUMERIC(12,3) NOT NULL CHECK (cantidad > 0),
    precio_unitario  NUMERIC(12,2) NOT NULL CHECK (precio_unitario >= 0),
    costo_unitario   NUMERIC(12,2) NOT NULL DEFAULT 0,
    descuento_linea  NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (descuento_linea >= 0),
    total_linea      NUMERIC(14,2) GENERATED ALWAYS AS
                     (cantidad * precio_unitario - descuento_linea) STORED,
    promocion_id     BIGINT
);
CREATE INDEX IF NOT EXISTS idx_venta_det_venta    ON ven.venta_detalles(venta_id);
CREATE INDEX IF NOT EXISTS idx_venta_det_producto ON ven.venta_detalles(producto_id);

CREATE TABLE IF NOT EXISTS ven.devoluciones_venta (
    devolucion_id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    folio               TEXT NOT NULL UNIQUE,
    venta_id            BIGINT NOT NULL REFERENCES ven.ventas(venta_id),
    fecha               TIMESTAMPTZ NOT NULL DEFAULT now(),
    motivo              TEXT NOT NULL,
    total               NUMERIC(14,2) NOT NULL DEFAULT 0,
    forma_devolucion_id INTEGER NOT NULL REFERENCES cat.formas_pago(forma_pago_id),
    usuario_id          INTEGER NOT NULL REFERENCES seg.usuarios(usuario_id),
    turno_caja_id       BIGINT
);

CREATE TABLE IF NOT EXISTS ven.devolucion_detalles (
    devolucion_id    BIGINT NOT NULL REFERENCES ven.devoluciones_venta(devolucion_id) ON DELETE CASCADE,
    venta_detalle_id BIGINT REFERENCES ven.venta_detalles(venta_detalle_id),
    producto_id      BIGINT NOT NULL REFERENCES inv.productos(producto_id),
    cantidad         NUMERIC(12,3) NOT NULL CHECK (cantidad > 0),
    precio_unitario  NUMERIC(12,2) NOT NULL,
    importe_linea    NUMERIC(14,2) GENERATED ALWAYS AS (cantidad * precio_unitario) STORED,
    PRIMARY KEY (devolucion_id, producto_id)
);

CREATE TABLE IF NOT EXISTS ven.rentas (
    renta_id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    folio               TEXT NOT NULL UNIQUE,
    cliente_id          BIGINT NOT NULL REFERENCES ven.clientes(cliente_id),
    almacen_id          INTEGER NOT NULL REFERENCES inv.almacenes(almacen_id),
    fecha_renta         TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_dev_esperada  DATE NOT NULL,
    fecha_dev_real      TIMESTAMPTZ,
    deposito            NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (deposito >= 0),
    costo_total         NUMERIC(12,2) NOT NULL DEFAULT 0,
    estado              VARCHAR(12) NOT NULL DEFAULT 'ABIERTA'
                        CHECK (estado IN ('ABIERTA','DEVUELTA','VENCIDA','CANCELADA')),
    usuario_id          INTEGER NOT NULL REFERENCES seg.usuarios(usuario_id),
    turno_caja_id       BIGINT,
    forma_pago_id       INTEGER REFERENCES cat.formas_pago(forma_pago_id)
);

CREATE TABLE IF NOT EXISTS ven.renta_detalles (
    renta_id      BIGINT NOT NULL REFERENCES ven.rentas(renta_id) ON DELETE CASCADE,
    producto_id   BIGINT NOT NULL REFERENCES inv.productos(producto_id),
    cantidad      NUMERIC(12,3) NOT NULL CHECK (cantidad > 0),
    costo_dia     NUMERIC(12,2) NOT NULL,
    dias_cobrados NUMERIC(6,1) NOT NULL DEFAULT 0,
    subtotal      NUMERIC(12,2) GENERATED ALWAYS AS (costo_dia * dias_cobrados) STORED,
    PRIMARY KEY (renta_id, producto_id)
);
CREATE INDEX IF NOT EXISTS idx_rentas_abiertas ON ven.rentas(estado, fecha_dev_esperada)
    WHERE estado = 'ABIERTA';

CREATE TABLE IF NOT EXISTS ven.cuentas_cobrar (
    cuenta_cobrar_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    venta_id          BIGINT NOT NULL UNIQUE REFERENCES ven.ventas(venta_id),
    cliente_id        BIGINT REFERENCES ven.clientes(cliente_id),  -- NULL = público general (contado)
    monto_total       NUMERIC(14,2) NOT NULL CHECK (monto_total > 0),
    monto_pagado      NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (monto_pagado >= 0),
    fecha_vencimiento DATE NOT NULL,
    estado            VARCHAR(12) NOT NULL DEFAULT 'VIGENTE'
                      CHECK (estado IN ('VIGENTE','PARCIAL','LIQUIDADA','CANCELADA')),
    creado_en         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_cc_pago_monto CHECK (monto_pagado <= monto_total)
);
CREATE INDEX IF NOT EXISTS idx_cc_vencimiento ON ven.cuentas_cobrar(fecha_vencimiento)
    WHERE estado <> 'LIQUIDADA';

CREATE TABLE IF NOT EXISTS ven.pagos_cliente (
    pago_cliente_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cuenta_cobrar_id BIGINT NOT NULL REFERENCES ven.cuentas_cobrar(cuenta_cobrar_id),
    forma_pago_id    INTEGER NOT NULL REFERENCES cat.formas_pago(forma_pago_id),
    referencia       VARCHAR(80),
    monto            NUMERIC(14,2) NOT NULL CHECK (monto > 0),
    fecha            TIMESTAMPTZ NOT NULL DEFAULT now(),
    usuario_id       INTEGER NOT NULL REFERENCES seg.usuarios(usuario_id),
    turno_caja_id    BIGINT
);
CREATE INDEX IF NOT EXISTS idx_pc_cuenta ON ven.pagos_cliente(cuenta_cobrar_id);
CREATE INDEX IF NOT EXISTS idx_pc_fecha  ON ven.pagos_cliente(fecha DESC);

-- ============================================================================
-- J. MÓDULO com — Órdenes, compras, devoluciones, cuentas por pagar
-- ============================================================================
CREATE TABLE IF NOT EXISTS com.ordenes_compra (
    orden_compra_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    folio           TEXT NOT NULL UNIQUE,
    proveedor_id    INTEGER NOT NULL REFERENCES com.proveedores(proveedor_id),
    almacen_destino INTEGER NOT NULL REFERENCES inv.almacenes(almacen_id),
    fecha           TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_esperada  DATE,
    estado          VARCHAR(12) NOT NULL DEFAULT 'EMITIDA'
                    CHECK (estado IN ('BORRADOR','EMITIDA','RECIBIDA_TOTAL','RECIBIDA_PARCIAL','CANCELADA')),
    total_estimado  NUMERIC(14,2) NOT NULL DEFAULT 0,
    usuario_id      INTEGER NOT NULL REFERENCES seg.usuarios(usuario_id),
    notas           TEXT
);

CREATE TABLE IF NOT EXISTS com.orden_compra_detalles (
    orden_compra_id BIGINT NOT NULL REFERENCES com.ordenes_compra(orden_compra_id) ON DELETE CASCADE,
    producto_id     BIGINT NOT NULL REFERENCES inv.productos(producto_id),
    cantidad        NUMERIC(12,3) NOT NULL CHECK (cantidad > 0),
    costo_unitario  NUMERIC(12,2) NOT NULL CHECK (costo_unitario >= 0),
    recibido        NUMERIC(12,3) NOT NULL DEFAULT 0,
    PRIMARY KEY (orden_compra_id, producto_id)
);

CREATE TABLE IF NOT EXISTS com.compras (
    compra_id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    folio              TEXT NOT NULL UNIQUE,
    factura_proveedor  VARCHAR(50),
    proveedor_id       INTEGER NOT NULL REFERENCES com.proveedores(proveedor_id),
    orden_compra_id    BIGINT REFERENCES com.ordenes_compra(orden_compra_id),
    almacen_id         INTEGER NOT NULL REFERENCES inv.almacenes(almacen_id),
    fecha              TIMESTAMPTZ NOT NULL DEFAULT now(),
    forma_pago_id      INTEGER NOT NULL REFERENCES cat.formas_pago(forma_pago_id),
    subtotal           NUMERIC(14,2) NOT NULL DEFAULT 0,
    iva                NUMERIC(14,2) NOT NULL DEFAULT 0,
    descuento_total    NUMERIC(14,2) NOT NULL DEFAULT 0,
    total              NUMERIC(14,2) NOT NULL DEFAULT 0,
    estado             VARCHAR(12) NOT NULL DEFAULT 'RECIBIDA'
                       CHECK (estado IN ('PENDIENTE','RECIBIDA','CANCELADA')),
    usuario_id         INTEGER NOT NULL REFERENCES seg.usuarios(usuario_id),
    turno_caja_id      BIGINT,
    notas              TEXT
);

CREATE TABLE IF NOT EXISTS com.compra_detalles (
    compra_detalle_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    compra_id         BIGINT NOT NULL REFERENCES com.compras(compra_id) ON DELETE CASCADE,
    producto_id       BIGINT NOT NULL REFERENCES inv.productos(producto_id),
    cantidad          NUMERIC(12,3) NOT NULL CHECK (cantidad > 0),
    costo_unitario    NUMERIC(12,2) NOT NULL CHECK (costo_unitario >= 0),
    importe_linea     NUMERIC(14,2) GENERATED ALWAYS AS (cantidad * costo_unitario) STORED
);
CREATE INDEX IF NOT EXISTS idx_compra_det_producto ON com.compra_detalles(producto_id);

CREATE TABLE IF NOT EXISTS com.devoluciones_compra (
    devolucion_id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    folio           TEXT NOT NULL UNIQUE,
    compra_id       BIGINT NOT NULL REFERENCES com.compras(compra_id),
    proveedor_id    INTEGER NOT NULL REFERENCES com.proveedores(proveedor_id),
    almacen_id      INTEGER NOT NULL REFERENCES inv.almacenes(almacen_id),
    fecha           TIMESTAMPTZ NOT NULL DEFAULT now(),
    motivo          TEXT NOT NULL,
    total           NUMERIC(14,2) NOT NULL DEFAULT 0,
    forma_abono_id  INTEGER REFERENCES cat.formas_pago(forma_pago_id),
    usuario_id      INTEGER NOT NULL REFERENCES seg.usuarios(usuario_id)
);

CREATE TABLE IF NOT EXISTS com.devolucion_compra_detalles (
    devolucion_id  BIGINT NOT NULL REFERENCES com.devoluciones_compra(devolucion_id) ON DELETE CASCADE,
    producto_id    BIGINT NOT NULL REFERENCES inv.productos(producto_id),
    cantidad       NUMERIC(12,3) NOT NULL CHECK (cantidad > 0),
    costo_unitario NUMERIC(12,2) NOT NULL,
    importe_linea  NUMERIC(14,2) GENERATED ALWAYS AS (cantidad * costo_unitario) STORED,
    PRIMARY KEY (devolucion_id, producto_id)
);

CREATE TABLE IF NOT EXISTS com.cuentas_pagar (
    cuenta_pagar_id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    compra_id         BIGINT NOT NULL UNIQUE REFERENCES com.compras(compra_id),
    monto_total       NUMERIC(14,2) NOT NULL CHECK (monto_total > 0),
    monto_pagado      NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (monto_pagado >= 0),
    fecha_vencimiento DATE NOT NULL,
    estado            VARCHAR(12) NOT NULL DEFAULT 'VIGENTE'
                      CHECK (estado IN ('VIGENTE','PARCIAL','LIQUIDADA','CANCELADA')),
    creado_en         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_cp_pago_monto CHECK (monto_pagado <= monto_total)
);
CREATE INDEX IF NOT EXISTS idx_cp_vencimiento ON com.cuentas_pagar(fecha_vencimiento)
    WHERE estado <> 'LIQUIDADA';

CREATE TABLE IF NOT EXISTS com.pagos_proveedor (
    pago_proveedor_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cuenta_pagar_id   BIGINT NOT NULL REFERENCES com.cuentas_pagar(cuenta_pagar_id),
    forma_pago_id     INTEGER NOT NULL REFERENCES cat.formas_pago(forma_pago_id),
    referencia        VARCHAR(80),
    monto             NUMERIC(14,2) NOT NULL CHECK (monto > 0),
    fecha             TIMESTAMPTZ NOT NULL DEFAULT now(),
    usuario_id        INTEGER NOT NULL REFERENCES seg.usuarios(usuario_id),
    turno_caja_id     BIGINT
);
CREATE INDEX IF NOT EXISTS idx_pp_cuenta ON com.pagos_proveedor(cuenta_pagar_id);

-- ============================================================================
-- K. MÓDULO fin — Caja, gastos, otros ingresos
-- ============================================================================
CREATE TABLE IF NOT EXISTS fin.cajas (
    caja_id    INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre     VARCHAR(80) NOT NULL UNIQUE,
    almacen_id INTEGER NOT NULL REFERENCES inv.almacenes(almacen_id),
    activa     BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE IF NOT EXISTS fin.turnos_caja (
    turno_caja_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    caja_id        INTEGER NOT NULL REFERENCES fin.cajas(caja_id),
    usuario_id     INTEGER NOT NULL REFERENCES seg.usuarios(usuario_id),
    apertura_en    TIMESTAMPTZ NOT NULL DEFAULT now(),
    monto_apertura NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (monto_apertura >= 0),
    cierre_en      TIMESTAMPTZ,
    monto_esperado NUMERIC(14,2),
    monto_contado  NUMERIC(14,2),
    diferencia     NUMERIC(14,2),
    estado         VARCHAR(10) NOT NULL DEFAULT 'ABIERTO'
                   CHECK (estado IN ('ABIERTO','CERRADO')),
    observaciones  TEXT
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_turno_abierto_por_caja
    ON fin.turnos_caja(caja_id) WHERE estado = 'ABIERTO';
CREATE INDEX IF NOT EXISTS idx_turnos_fecha ON fin.turnos_caja(apertura_en DESC);

CREATE TABLE IF NOT EXISTS fin.movimientos_caja (
    movimiento_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    turno_caja_id BIGINT NOT NULL REFERENCES fin.turnos_caja(turno_caja_id),
    tipo          VARCHAR(8)  NOT NULL CHECK (tipo IN ('ENTRADA','SALIDA')),
    concepto      VARCHAR(30) NOT NULL CHECK (concepto IN (
                      'APERTURA','VENTA_CONTADO','COBRANZA_CREDITO',
                      'DEPOSITO_GARANTIA_RENTA','OTRO_INGRESO',
                      'GASTO_OPERATIVO','PAGO_PROVEEDOR','NOMINA',
                      'DEVOLUCION_CLIENTE','RETIRO_EFECTIVO',
                      'DEVOLUCION_DEPOSITO_RENTA')),
    monto         NUMERIC(14,2) NOT NULL CHECK (monto > 0),
    forma_pago_id INTEGER REFERENCES cat.formas_pago(forma_pago_id),
    ref_tabla     VARCHAR(40),
    ref_id        BIGINT,
    usuario_id    INTEGER REFERENCES seg.usuarios(usuario_id),
    creado_en     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_mc_turno ON fin.movimientos_caja(turno_caja_id);
CREATE INDEX IF NOT EXISTS idx_mc_fecha ON fin.movimientos_caja(creado_en DESC);
CREATE INDEX IF NOT EXISTS idx_mc_concepto_fecha ON fin.movimientos_caja(concepto, creado_en DESC);

CREATE TABLE IF NOT EXISTS fin.gastos (
    gasto_id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    folio         TEXT NOT NULL UNIQUE,
    tipo_gasto_id INTEGER NOT NULL REFERENCES cat.tipos_gasto(tipo_gasto_id),
    descripcion   VARCHAR(250) NOT NULL,
    monto         NUMERIC(14,2) NOT NULL CHECK (monto > 0),
    fecha_gasto   DATE NOT NULL DEFAULT CURRENT_DATE,
    forma_pago_id INTEGER NOT NULL REFERENCES cat.formas_pago(forma_pago_id),
    proveedor_id  INTEGER REFERENCES com.proveedores(proveedor_id),
    turno_caja_id BIGINT REFERENCES fin.turnos_caja(turno_caja_id),
    factura_uuid  VARCHAR(64),
    usuario_id    INTEGER NOT NULL REFERENCES seg.usuarios(usuario_id),
    creado_en     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_gastos_fecha ON fin.gastos(fecha_gasto DESC);

CREATE TABLE IF NOT EXISTS fin.ingresos_otros (
    ingreso_otro_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    concepto        VARCHAR(150) NOT NULL,
    monto           NUMERIC(14,2) NOT NULL CHECK (monto > 0),
    fecha           DATE NOT NULL DEFAULT CURRENT_DATE,
    forma_pago_id   INTEGER NOT NULL REFERENCES cat.formas_pago(forma_pago_id),
    turno_caja_id   BIGINT REFERENCES fin.turnos_caja(turno_caja_id),
    usuario_id      INTEGER NOT NULL REFERENCES seg.usuarios(usuario_id),
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Histórico de cortes de caja (una fila inmutable por turno cerrado)
CREATE TABLE IF NOT EXISTS fin.cortes_caja (
    corte_id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    turno_caja_id       BIGINT NOT NULL UNIQUE REFERENCES fin.turnos_caja(turno_caja_id),
    caja_id             INTEGER NOT NULL REFERENCES fin.cajas(caja_id),
    almacen_id          INTEGER NOT NULL REFERENCES inv.almacenes(almacen_id),
    usuario_id          INTEGER NOT NULL REFERENCES seg.usuarios(usuario_id),   -- cajero del turno
    usuario_cierre_id   INTEGER NOT NULL REFERENCES seg.usuarios(usuario_id),   -- quien hizo el corte
    fecha               DATE NOT NULL DEFAULT CURRENT_DATE,
    apertura_en         TIMESTAMPTZ NOT NULL,
    cierre_en           TIMESTAMPTZ NOT NULL DEFAULT now(),
    num_ventas          INTEGER  NOT NULL DEFAULT 0,
    subtotal            NUMERIC(14,2) NOT NULL DEFAULT 0,
    iva                 NUMERIC(14,2) NOT NULL DEFAULT 0,
    descuentos          NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_vendido       NUMERIC(14,2) NOT NULL DEFAULT 0,
    costo_ventas        NUMERIC(14,2) NOT NULL DEFAULT 0,
    utilidad_bruta      NUMERIC(14,2) GENERATED ALWAYS AS (subtotal - costo_ventas) STORED,
    margen_pct          NUMERIC(6,2)  GENERATED ALWAYS AS
                        ((subtotal - costo_ventas) / NULLIF(subtotal,0) * 100) STORED,
    fondo_apertura      NUMERIC(14,2) NOT NULL DEFAULT 0,
    entradas_efectivo   NUMERIC(14,2) NOT NULL DEFAULT 0,
    salidas_efectivo    NUMERIC(14,2) NOT NULL DEFAULT 0,
    dinero_esperado     NUMERIC(14,2) NOT NULL DEFAULT 0,
    dinero_contado      NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (dinero_contado >= 0),
    diferencia          NUMERIC(14,2) NOT NULL DEFAULT 0,
    ingresos_no_efectivo NUMERIC(14,2) NOT NULL DEFAULT 0,
    egresos_no_efectivo  NUMERIC(14,2) NOT NULL DEFAULT 0,
    perdidas_inventario  NUMERIC(14,2) NOT NULL DEFAULT 0,
    desglose_entradas    JSONB NOT NULL DEFAULT '{}'::jsonb,
    desglose_salidas     JSONB NOT NULL DEFAULT '{}'::jsonb,
    desglose_formas_pago JSONB NOT NULL DEFAULT '{}'::jsonb,
    observaciones        TEXT,
    creado_en            TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_cortes_fecha ON fin.cortes_caja(fecha DESC);
CREATE INDEX IF NOT EXISTS idx_cortes_caja  ON fin.cortes_caja(caja_id, fecha DESC);

COMMENT ON TABLE fin.cortes_caja IS
'Historial inmutable de cortes de caja: congela ventas, utilidad, margen, efectivo
esperado/contado/diferencia, pérdidas de inventario y desgloses de flujo del turno.';

-- ============================================================================
-- L. FOREIGN KEYS CRUZADAS (dependencias circulares resueltas)
-- ============================================================================
ALTER TABLE inv.movimientos_inventario
    DROP CONSTRAINT IF EXISTS fk_mov_traslado,
    ADD  CONSTRAINT fk_mov_traslado FOREIGN KEY (traslado_id)
         REFERENCES inv.traslados(traslado_id);

ALTER TABLE ven.ventas
    ADD COLUMN IF NOT EXISTS metodo_pago_sat  VARCHAR(3) DEFAULT 'PUE',
    ADD COLUMN IF NOT EXISTS folio_fiscal_uuid UUID,
    ADD COLUMN IF NOT EXISTS descuento_id      BIGINT;
ALTER TABLE ven.ventas
    DROP CONSTRAINT IF EXISTS fk_venta_turno,
    ADD  CONSTRAINT fk_venta_turno FOREIGN KEY (turno_caja_id)
         REFERENCES fin.turnos_caja(turno_caja_id);
ALTER TABLE ven.ventas
    DROP CONSTRAINT IF EXISTS fk_venta_metodo_sat,
    ADD  CONSTRAINT fk_venta_metodo_sat FOREIGN KEY (metodo_pago_sat)
         REFERENCES fis.metodos_pago_sat(clave);
ALTER TABLE ven.ventas
    DROP CONSTRAINT IF EXISTS fk_venta_descuento,
    ADD  CONSTRAINT fk_venta_descuento FOREIGN KEY (descuento_id)
         REFERENCES ven.descuentos(descuento_id);

ALTER TABLE ven.venta_detalles
    DROP CONSTRAINT IF EXISTS fk_detalle_promo,
    ADD  CONSTRAINT fk_detalle_promo FOREIGN KEY (promocion_id)
         REFERENCES ven.promociones(promocion_id);

ALTER TABLE ven.promocion_usos
    DROP CONSTRAINT IF EXISTS fk_pu_venta,
    ADD  CONSTRAINT fk_pu_venta FOREIGN KEY (venta_id)
         REFERENCES ven.ventas(venta_id);

ALTER TABLE ven.devoluciones_venta
    DROP CONSTRAINT IF EXISTS fk_dev_turno,
    ADD  CONSTRAINT fk_dev_turno FOREIGN KEY (turno_caja_id)
         REFERENCES fin.turnos_caja(turno_caja_id);

ALTER TABLE ven.rentas
    DROP CONSTRAINT IF EXISTS fk_renta_turno,
    ADD  CONSTRAINT fk_renta_turno FOREIGN KEY (turno_caja_id)
         REFERENCES fin.turnos_caja(turno_caja_id);

ALTER TABLE ven.pagos_cliente
    DROP CONSTRAINT IF EXISTS fk_pc_turno,
    ADD  CONSTRAINT fk_pc_turno FOREIGN KEY (turno_caja_id)
         REFERENCES fin.turnos_caja(turno_caja_id);

ALTER TABLE ven.cotizaciones
    DROP CONSTRAINT IF EXISTS fk_cot_venta,
    ADD  CONSTRAINT fk_cot_venta FOREIGN KEY (venta_generada_id)
         REFERENCES ven.ventas(venta_id);

ALTER TABLE com.compras
    DROP CONSTRAINT IF EXISTS fk_compra_turno,
    ADD  CONSTRAINT fk_compra_turno FOREIGN KEY (turno_caja_id)
         REFERENCES fin.turnos_caja(turno_caja_id);

ALTER TABLE com.pagos_proveedor
    DROP CONSTRAINT IF EXISTS fk_pp_turno,
    ADD  CONSTRAINT fk_pp_turno FOREIGN KEY (turno_caja_id)
         REFERENCES fin.turnos_caja(turno_caja_id);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_cliente_regimen') THEN
        ALTER TABLE ven.clientes ADD CONSTRAINT fk_cliente_regimen
            FOREIGN KEY (regimen_fiscal) REFERENCES fis.regimenes_fiscales(clave_sat);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_proveedor_regimen') THEN
        ALTER TABLE com.proveedores ADD CONSTRAINT fk_proveedor_regimen
            FOREIGN KEY (regimen_fiscal) REFERENCES fis.regimenes_fiscales(clave_sat);
    END IF;
END $$;

-- ============================================================================
-- M. FUNCIONES Y TRIGGERS
-- ============================================================================

-- ---------- Folios ----------
CREATE OR REPLACE FUNCTION cfg.fn_siguiente_folio(p_tipo TEXT)
RETURNS TEXT LANGUAGE sql AS $$
    WITH upsert AS (
        -- El placeholder '' evita que el NOT NULL de prefijo se valide
        -- antes del arbitraje ON CONFLICT (comportamiento de PostgreSQL).
        INSERT INTO cfg.folios (tipo, prefijo, consecutivo)
        VALUES (p_tipo, '', 1)
        ON CONFLICT (tipo) DO UPDATE
            SET consecutivo = cfg.folios.consecutivo + 1,
                actualizado_en = now()
        RETURNING consecutivo
    )
    SELECT f.prefijo || lpad(u.consecutivo::text, 8, '0')
    FROM upsert u
    CROSS JOIN LATERAL (SELECT prefijo FROM cfg.folios WHERE tipo = p_tipo) f;
$$;

CREATE OR REPLACE FUNCTION common_asigna_folio()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.folio := cfg.fn_siguiente_folio(TG_ARGV[0]);
    RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS trg_folio_ventas ON ven.ventas;
CREATE TRIGGER trg_folio_ventas      BEFORE INSERT ON ven.ventas
    FOR EACH ROW WHEN (NEW.folio IS NULL) EXECUTE FUNCTION common_asigna_folio('VENTA');
DROP TRIGGER IF EXISTS trg_folio_compras ON com.compras;
CREATE TRIGGER trg_folio_compras     BEFORE INSERT ON com.compras
    FOR EACH ROW WHEN (NEW.folio IS NULL) EXECUTE FUNCTION common_asigna_folio('COMPRA');
DROP TRIGGER IF EXISTS trg_folio_dev_ven ON ven.devoluciones_venta;
CREATE TRIGGER trg_folio_dev_ven     BEFORE INSERT ON ven.devoluciones_venta
    FOR EACH ROW WHEN (NEW.folio IS NULL) EXECUTE FUNCTION common_asigna_folio('DEVOLUCION_VENTA');
DROP TRIGGER IF EXISTS trg_folio_dev_com ON com.devoluciones_compra;
CREATE TRIGGER trg_folio_dev_com     BEFORE INSERT ON com.devoluciones_compra
    FOR EACH ROW WHEN (NEW.folio IS NULL) EXECUTE FUNCTION common_asigna_folio('DEVOLUCION_COMPRA');
DROP TRIGGER IF EXISTS trg_folio_rentas ON ven.rentas;
CREATE TRIGGER trg_folio_rentas      BEFORE INSERT ON ven.rentas
    FOR EACH ROW WHEN (NEW.folio IS NULL) EXECUTE FUNCTION common_asigna_folio('RENTA');
DROP TRIGGER IF EXISTS trg_folio_cotiza ON ven.cotizaciones;
CREATE TRIGGER trg_folio_cotiza      BEFORE INSERT ON ven.cotizaciones
    FOR EACH ROW WHEN (NEW.folio IS NULL) EXECUTE FUNCTION common_asigna_folio('COTIZACION');
DROP TRIGGER IF EXISTS trg_folio_gastos ON fin.gastos;
CREATE TRIGGER trg_folio_gastos      BEFORE INSERT ON fin.gastos
    FOR EACH ROW WHEN (NEW.folio IS NULL) EXECUTE FUNCTION common_asigna_folio('GASTO');
DROP TRIGGER IF EXISTS trg_folio_traslados ON inv.traslados;
CREATE TRIGGER trg_folio_traslados   BEFORE INSERT ON inv.traslados
    FOR EACH ROW WHEN (NEW.folio IS NULL) EXECUTE FUNCTION common_asigna_folio('TRASLADO');

-- ---------- Auditoría y updated_at ----------
CREATE OR REPLACE FUNCTION seg.fn_auditar()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE v_uid INTEGER := NULLIF(current_setting('app.usuario_id', true), '')::INTEGER;
        v_pk_col TEXT := COALESCE(NULLIF(TG_ARGV[0], ''), 'id');
        v_row JSONB;
BEGIN
    v_row := CASE WHEN TG_OP = 'DELETE' THEN to_jsonb(OLD) ELSE to_jsonb(NEW) END;
    INSERT INTO seg.auditoria (esquema, tabla, registro_id, accion,
                               datos_anteriores, datos_nuevos, usuario_id)
    VALUES (TG_TABLE_SCHEMA, TG_TABLE_NAME,
            COALESCE((v_row ->> v_pk_col)::BIGINT, 0),
            TG_OP,
            CASE WHEN TG_OP IN ('UPDATE','DELETE') THEN to_jsonb(OLD) END,
            CASE WHEN TG_OP IN ('INSERT','UPDATE') THEN to_jsonb(NEW) END,
            v_uid);
    RETURN COALESCE(NEW, OLD);
END $$;

DROP TRIGGER IF EXISTS trg_audit_producto ON inv.productos;
CREATE TRIGGER trg_audit_producto  AFTER INSERT OR UPDATE OR DELETE ON inv.productos
    FOR EACH ROW EXECUTE FUNCTION seg.fn_auditar('producto_id');
DROP TRIGGER IF EXISTS trg_audit_cliente ON ven.clientes;
CREATE TRIGGER trg_audit_cliente   AFTER INSERT OR UPDATE OR DELETE ON ven.clientes
    FOR EACH ROW EXECUTE FUNCTION seg.fn_auditar('cliente_id');
DROP TRIGGER IF EXISTS trg_audit_proveedor ON com.proveedores;
CREATE TRIGGER trg_audit_proveedor AFTER INSERT OR UPDATE OR DELETE ON com.proveedores
    FOR EACH ROW EXECUTE FUNCTION seg.fn_auditar('proveedor_id');
DROP TRIGGER IF EXISTS trg_audit_usuario ON seg.usuarios;
CREATE TRIGGER trg_audit_usuario   AFTER INSERT OR UPDATE OR DELETE ON seg.usuarios
    FOR EACH ROW EXECUTE FUNCTION seg.fn_auditar('usuario_id');
DROP TRIGGER IF EXISTS trg_audit_venta ON ven.ventas;
CREATE TRIGGER trg_audit_venta     AFTER UPDATE OR DELETE ON ven.ventas
    FOR EACH ROW EXECUTE FUNCTION seg.fn_auditar('venta_id');
DROP TRIGGER IF EXISTS trg_audit_gasto ON fin.gastos;
CREATE TRIGGER trg_audit_gasto     AFTER UPDATE OR DELETE ON fin.gastos
    FOR EACH ROW EXECUTE FUNCTION seg.fn_auditar('gasto_id');

DROP TRIGGER IF EXISTS trg_audit_promocion ON ven.promociones;
CREATE TRIGGER trg_audit_promocion AFTER INSERT OR UPDATE OR DELETE ON ven.promociones
    FOR EACH ROW EXECUTE FUNCTION seg.fn_auditar('promocion_id');

CREATE OR REPLACE FUNCTION common_touch_updated_at() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN NEW.actualizado_en := now(); RETURN NEW; END $$;

DROP TRIGGER IF EXISTS trg_touch_producto ON inv.productos;
CREATE TRIGGER trg_touch_producto  BEFORE UPDATE ON inv.productos
    FOR EACH ROW EXECUTE FUNCTION common_touch_updated_at();
DROP TRIGGER IF EXISTS trg_touch_cliente ON ven.clientes;
CREATE TRIGGER trg_touch_cliente   BEFORE UPDATE ON ven.clientes
    FOR EACH ROW EXECUTE FUNCTION common_touch_updated_at();
DROP TRIGGER IF EXISTS trg_touch_proveedor ON com.proveedores;
CREATE TRIGGER trg_touch_proveedor BEFORE UPDATE ON com.proveedores
    FOR EACH ROW EXECUTE FUNCTION common_touch_updated_at();

-- ---------- Caja: API única de flujo de efectivo ----------
CREATE OR REPLACE FUNCTION fin.fn_movimiento_caja(
    p_turno BIGINT, p_tipo TEXT, p_concepto TEXT, p_monto NUMERIC,
    p_forma_pago INT DEFAULT NULL, p_ref_tabla TEXT DEFAULT NULL,
    p_ref_id BIGINT DEFAULT NULL, p_usuario INT DEFAULT NULL
) RETURNS BIGINT
LANGUAGE plpgsql AS $$
DECLARE v_id BIGINT; v_efectivo BOOLEAN := TRUE; v_abierto TEXT;
BEGIN
    IF p_forma_pago IS NOT NULL THEN
        SELECT es_efectivo INTO v_efectivo
        FROM cat.formas_pago WHERE forma_pago_id = p_forma_pago;
    END IF;

    SELECT estado INTO v_abierto FROM fin.turnos_caja WHERE turno_caja_id = p_turno;
    IF v_abierto IS DISTINCT FROM 'ABIERTO' THEN
        RAISE EXCEPTION 'El turno % no está abierto', p_turno USING ERRCODE = 'P0300';
    END IF;

    INSERT INTO fin.movimientos_caja
        (turno_caja_id, tipo, concepto, monto, forma_pago_id, ref_tabla, ref_id, usuario_id)
    VALUES
        (p_turno, p_tipo, p_concepto, p_monto, p_forma_pago, p_ref_tabla, p_ref_id, p_usuario)
    RETURNING movimiento_id INTO v_id;
    RETURN v_id;
END $$;

-- ---------- Inventario: kardex ----------
CREATE OR REPLACE FUNCTION inv.fn_registrar_movimiento(
    p_producto BIGINT, p_almacen INT, p_tipo TEXT, p_cantidad NUMERIC,
    p_motivo INT, p_costo NUMERIC DEFAULT NULL,
    p_ref_tabla TEXT DEFAULT NULL, p_ref_id BIGINT DEFAULT NULL,
    p_usuario INT DEFAULT NULL, p_nota TEXT DEFAULT NULL
) RETURNS BIGINT
LANGUAGE plpgsql AS $$
DECLARE v_id BIGINT;
BEGIN
    INSERT INTO inv.movimientos_inventario
        (producto_id, almacen_id, tipo, cantidad, costo_unitario,
         motivo_id, ref_tabla, ref_id, usuario_id, nota)
    VALUES
        (p_producto, p_almacen, p_tipo, p_cantidad, p_costo,
         p_motivo, p_ref_tabla, p_ref_id, p_usuario, p_nota)
    RETURNING movimiento_id INTO v_id;
    RETURN v_id;
END $$;

CREATE OR REPLACE FUNCTION inv.fn_aplica_movimiento_stock()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE v_delta NUMERIC := CASE WHEN NEW.tipo = 'ENTRADA' THEN NEW.cantidad
                                                 ELSE -NEW.cantidad END;
        v_nuevo NUMERIC;
BEGIN
    SELECT COALESCE(i.stock, 0) + v_delta INTO v_nuevo
    FROM inv.inventario i
    WHERE i.producto_id = NEW.producto_id AND i.almacen_id = NEW.almacen_id;

    IF v_nuevo < 0 THEN
        IF COALESCE((SELECT valor::boolean FROM cfg.configuracion
                     WHERE clave = 'permitir_stock_negativo'), false) = false THEN
            RAISE EXCEPTION 'Stock negativo no permitido: producto % quedaria en %',
                NEW.producto_id, v_nuevo USING ERRCODE = 'P0100';
        END IF;
    END IF;

    INSERT INTO inv.inventario (producto_id, almacen_id, stock)
    VALUES (NEW.producto_id, NEW.almacen_id, v_delta)
    ON CONFLICT (producto_id, almacen_id)
    DO UPDATE SET stock = inv.inventario.stock + EXCLUDED.stock,
                  actualizado_en = now();
    RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS trg_mov_stock ON inv.movimientos_inventario;
CREATE TRIGGER trg_mov_stock AFTER INSERT ON inv.movimientos_inventario
FOR EACH ROW EXECUTE FUNCTION inv.fn_aplica_movimiento_stock();

CREATE OR REPLACE FUNCTION inv.fn_kardex_solo_insert()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'movimientos_inventario es append-only' USING ERRCODE = 'P0999'; END $$;

DROP TRIGGER IF EXISTS trg_kardex_no_upd ON inv.movimientos_inventario;
CREATE TRIGGER trg_kardex_no_upd BEFORE UPDATE OR DELETE ON inv.movimientos_inventario
FOR EACH ROW EXECUTE FUNCTION inv.fn_kardex_solo_insert();

-- ---------- Ventas: validación de stock y salida al kardex ----------
CREATE OR REPLACE FUNCTION ven.fn_detalle_valida_stock()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE v_tipo TEXT; v_disp NUMERIC; v_permitir_neg BOOLEAN;
BEGIN
    SELECT tipo INTO v_tipo FROM inv.productos WHERE producto_id = NEW.producto_id;
    IF v_tipo = 'PRODUCTO' THEN
        SELECT i.stock - i.reservado INTO v_disp
        FROM inv.inventario i
        JOIN ven.ventas v ON v.almacen_id = i.almacen_id AND v.venta_id = NEW.venta_id
        WHERE i.producto_id = NEW.producto_id;

        IF v_disp IS NULL OR v_disp < NEW.cantidad THEN
            SELECT COALESCE(valor::boolean, false) INTO v_permitir_neg
            FROM cfg.configuracion WHERE clave = 'permitir_stock_negativo';
            IF COALESCE(v_permitir_neg, false) = false THEN
                RAISE EXCEPTION 'Stock insuficiente producto % disponible % solicitado %',
                    NEW.producto_id, COALESCE(v_disp, 0), NEW.cantidad USING ERRCODE = 'P0100';
            END IF;
        END IF;
    END IF;
    RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS trg_det_venta_valida ON ven.venta_detalles;
CREATE TRIGGER trg_det_venta_valida BEFORE INSERT ON ven.venta_detalles
FOR EACH ROW EXECUTE FUNCTION ven.fn_detalle_valida_stock();

CREATE OR REPLACE FUNCTION ven.fn_detalle_genera_salida()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE v_almacen INT; v_motivo INT;
BEGIN
    SELECT almacen_id INTO v_almacen FROM ven.ventas WHERE venta_id = NEW.venta_id;
    SELECT motivo_id INTO v_motivo FROM cat.motivos_movimiento WHERE clave = 'VENTA';

    PERFORM inv.fn_registrar_movimiento(
        NEW.producto_id, v_almacen, 'SALIDA', NEW.cantidad,
        v_motivo, NEW.costo_unitario, 'ven.ventas', NEW.venta_id);
    RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS trg_det_venta_salida ON ven.venta_detalles;
CREATE TRIGGER trg_det_venta_salida AFTER INSERT ON ven.venta_detalles
FOR EACH ROW EXECUTE FUNCTION ven.fn_detalle_genera_salida();

-- ---------- Ventas: crédito, totales, cuenta por cobrar y contado ----------
CREATE OR REPLACE FUNCTION ven.fn_valida_credito(p_venta BIGINT, p_total NUMERIC)
RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE v_cli BIGINT; v_disp NUMERIC;
BEGIN
    SELECT cliente_id INTO v_cli FROM ven.ventas WHERE venta_id = p_venta;
    IF v_cli IS NULL THEN
        RAISE EXCEPTION 'Venta a credito requiere cliente identificado' USING ERRCODE = 'P0201';
    END IF;

    SELECT lc.monto_autorizado - COALESCE(SUM(cc.monto_total - cc.monto_pagado), 0)
      INTO v_disp
    FROM ven.lineas_credito lc
    LEFT JOIN ven.cuentas_cobrar cc
           ON cc.cliente_id = lc.cliente_id AND cc.estado IN ('VIGENTE','PARCIAL')
    WHERE lc.cliente_id = v_cli
      AND lc.estado = 'ACTIVA'
      AND (lc.vigente_hasta IS NULL OR lc.vigente_hasta >= CURRENT_DATE)
    GROUP BY lc.monto_autorizado;

    IF v_disp IS NULL THEN
        RAISE EXCEPTION 'Cliente % sin linea de credito activa', v_cli USING ERRCODE = 'P0201';
    END IF;
    IF v_disp < p_total THEN
        RAISE EXCEPTION 'Credito insuficiente para cliente %: disponible %, venta %',
            v_cli, v_disp, p_total USING ERRCODE = 'P0200';
    END IF;
END $$;

CREATE OR REPLACE FUNCTION ven.fn_recalc_totales_venta()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE v_vid   BIGINT := COALESCE(NEW.venta_id, OLD.venta_id);
        v_sub NUMERIC; v_desc NUMERIC; v_tot NUMERIC; v_iva NUMERIC;
        v_tasa NUMERIC; v_incl BOOL; v_fp TEXT;
        v_cc BIGINT; v_prev NUMERIC; v_pagado NUMERIC;
        v_pid BIGINT; v_pmonto NUMERIC; v_exceso NUMERIC; v_dias SMALLINT;
BEGIN
    SELECT COALESCE(SUM(total_linea), 0), COALESCE(SUM(descuento_linea), 0)
      INTO v_tot, v_desc
    FROM ven.venta_detalles WHERE venta_id = v_vid;

    SELECT iva_tasa, iva_incluido INTO v_tasa, v_incl
    FROM ven.ventas WHERE venta_id = v_vid;

    IF COALESCE(v_incl, true) THEN
        v_sub := round(v_tot / (1 + v_tasa/100), 2);
        v_iva := v_tot - v_sub;
    ELSE
        v_sub := v_tot;
        v_iva := round(v_tot * v_tasa/100, 2);
        v_tot := v_tot + v_iva;
    END IF;

    UPDATE ven.ventas
       SET subtotal = v_sub, iva = v_iva, total = v_tot, descuento_total = v_desc
     WHERE venta_id = v_vid;

    IF v_tot > 0 THEN
        SELECT fp.clave INTO v_fp
        FROM ven.ventas v JOIN cat.formas_pago fp ON fp.forma_pago_id = v.forma_pago_id
        WHERE v.venta_id = v_vid;

        SELECT cuenta_cobrar_id, monto_total INTO v_cc, v_prev
        FROM ven.cuentas_cobrar WHERE venta_id = v_vid FOR UPDATE;

        IF NOT FOUND THEN
            IF v_fp = 'CREDITO' THEN
                PERFORM ven.fn_valida_credito(v_vid, v_tot);
            END IF;
            SELECT COALESCE(c.dias_credito, 0) INTO v_dias
            FROM ven.ventas v LEFT JOIN ven.clientes c ON c.cliente_id = v.cliente_id
            WHERE v.venta_id = v_vid;

            INSERT INTO ven.cuentas_cobrar (venta_id, cliente_id, monto_total, fecha_vencimiento)
            SELECT v_vid, cliente_id, v_tot, CURRENT_DATE + v_dias
            FROM ven.ventas WHERE venta_id = v_vid
            RETURNING cuenta_cobrar_id INTO v_cc;
        ELSIF v_prev <> v_tot THEN
            -- Al llegar aquí el trigger pudo ejecutarse varias veces (chequeo de
            -- venta_detalles insertados uno por uno desde la app). Una cuenta de
            -- CONTADO puede ya estar LIQUIDADA por el pago auto generado en la
            -- primera línea; aun así hay que sincronizar monto_total con v_tot y
            -- dejar que el bloque CONCILIA_PAGOS ajuste los pagos CONTADO.
            UPDATE ven.cuentas_cobrar SET monto_total = v_tot
            WHERE cuenta_cobrar_id = v_cc AND estado <> 'CANCELADA';
        END IF;

        IF v_fp <> 'CREDITO' THEN
            SELECT COALESCE(SUM(monto), 0) INTO v_pagado
            FROM ven.pagos_cliente
            WHERE cuenta_cobrar_id = v_cc AND referencia = 'CONTADO';

            IF v_pagado < v_tot THEN
                INSERT INTO ven.pagos_cliente
                    (cuenta_cobrar_id, forma_pago_id, monto, usuario_id, turno_caja_id, referencia)
                SELECT v_cc, forma_pago_id, v_tot - v_pagado, usuario_id, turno_caja_id, 'CONTADO'
                FROM ven.ventas WHERE venta_id = v_vid;
            ELSIF v_pagado > v_tot THEN
                v_exceso := v_pagado - v_tot;
                FOR v_pid, v_pmonto IN SELECT pago_cliente_id, monto
                                       FROM ven.pagos_cliente
                                       WHERE cuenta_cobrar_id = v_cc AND referencia = 'CONTADO'
                                       ORDER BY pago_cliente_id DESC FOR UPDATE
                LOOP
                    EXIT WHEN v_exceso <= 0;
                    IF v_pmonto <= v_exceso THEN
                        DELETE FROM ven.pagos_cliente WHERE pago_cliente_id = v_pid;
                        v_exceso := v_exceso - v_pmonto;
                    ELSE
                        UPDATE ven.pagos_cliente SET monto = v_pmonto - v_exceso
                        WHERE pago_cliente_id = v_pid;
                        v_exceso := 0;
                    END IF;
                END LOOP;
            END IF;
        END IF;
    END IF;

    RETURN NULL;
END $$;

DROP TRIGGER IF EXISTS trg_det_venta_totales ON ven.venta_detalles;
CREATE TRIGGER trg_det_venta_totales
AFTER INSERT OR DELETE ON ven.venta_detalles
FOR EACH ROW EXECUTE FUNCTION ven.fn_recalc_totales_venta();

CREATE OR REPLACE FUNCTION ven.fn_pago_cliente_post()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    UPDATE ven.cuentas_cobrar
       SET monto_pagado = monto_pagado + NEW.monto,
           estado = CASE WHEN monto_pagado + NEW.monto >= monto_total
                         THEN 'LIQUIDADA' ELSE 'PARCIAL' END
     WHERE cuenta_cobrar_id = NEW.cuenta_cobrar_id;

    IF NEW.turno_caja_id IS NOT NULL THEN
        PERFORM fin.fn_movimiento_caja(
            NEW.turno_caja_id, 'ENTRADA', 'COBRANZA_CREDITO', NEW.monto,
            NEW.forma_pago_id, 'ven.pagos_cliente', NEW.pago_cliente_id, NEW.usuario_id);
    END IF;
    RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS trg_pago_cliente_post ON ven.pagos_cliente;
CREATE TRIGGER trg_pago_cliente_post AFTER INSERT ON ven.pagos_cliente
FOR EACH ROW EXECUTE FUNCTION ven.fn_pago_cliente_post();

-- Devolución de cliente reingresa stock
CREATE OR REPLACE FUNCTION ven.fn_devolucion_detalle_post()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE v_almacen INT; v_motivo INT;
BEGIN
    SELECT v.almacen_id INTO v_almacen
    FROM ven.devoluciones_venta d JOIN ven.ventas v ON v.venta_id = d.venta_id
    WHERE d.devolucion_id = NEW.devolucion_id;
    SELECT motivo_id INTO v_motivo FROM cat.motivos_movimiento WHERE clave = 'DEVOLUCION_VENTA';

    PERFORM inv.fn_registrar_movimiento(
        NEW.producto_id, v_almacen, 'ENTRADA', NEW.cantidad,
        v_motivo, NULL, 'ven.devoluciones_venta', NEW.devolucion_id);
    RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS trg_devolucion_detalle ON ven.devolucion_detalles;
CREATE TRIGGER trg_devolucion_detalle AFTER INSERT ON ven.devolucion_detalles
FOR EACH ROW EXECUTE FUNCTION ven.fn_devolucion_detalle_post();

-- ---------- Compras: entrada al kardex, costo promedio, totales ----------
CREATE OR REPLACE FUNCTION com.fn_detalle_compra_entrada()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE v_almacen INT; v_motivo INT;
BEGIN
    SELECT almacen_id INTO v_almacen FROM com.compras WHERE compra_id = NEW.compra_id;
    SELECT motivo_id INTO v_motivo FROM cat.motivos_movimiento WHERE clave = 'COMPRA';

    PERFORM inv.fn_registrar_movimiento(
        NEW.producto_id, v_almacen, 'ENTRADA', NEW.cantidad,
        v_motivo, NEW.costo_unitario, 'com.compras', NEW.compra_id);

    -- Costo promedio ponderado global
    UPDATE inv.productos p
       SET costo_actual = ROUND(
             ((p.costo_actual * sub.stock_previo) + (NEW.costo_unitario * NEW.cantidad))
             / NULLIF(sub.stock_previo + NEW.cantidad, 0), 2)
      FROM (
        SELECT COALESCE(SUM(CASE WHEN m.tipo='ENTRADA' THEN m.cantidad
                                 ELSE -m.cantidad END), 0) - NEW.cantidad AS stock_previo
        FROM inv.movimientos_inventario m
        WHERE m.producto_id = NEW.producto_id
      ) sub
     WHERE p.producto_id = NEW.producto_id;
    RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS trg_det_compra_entrada ON com.compra_detalles;
CREATE TRIGGER trg_det_compra_entrada AFTER INSERT ON com.compra_detalles
FOR EACH ROW EXECUTE FUNCTION com.fn_detalle_compra_entrada();

CREATE OR REPLACE FUNCTION com.fn_recalc_totales_compra()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE v_cid BIGINT := COALESCE(NEW.compra_id, OLD.compra_id);
        v_tot NUMERIC; v_fp TEXT; v_cp BIGINT; v_prev NUMERIC;
        v_pagado NUMERIC; v_pid BIGINT; v_pmonto NUMERIC; v_exceso NUMERIC; v_dias SMALLINT;
BEGIN
    SELECT COALESCE(SUM(importe_linea), 0) INTO v_tot
    FROM com.compra_detalles WHERE compra_id = v_cid;

    UPDATE com.compras SET total = v_tot, subtotal = v_tot WHERE compra_id = v_cid;

    IF v_tot > 0 THEN
        SELECT fp.clave INTO v_fp
        FROM com.compras c JOIN cat.formas_pago fp ON fp.forma_pago_id = c.forma_pago_id
        WHERE c.compra_id = v_cid;

        SELECT cuenta_pagar_id, monto_total INTO v_cp, v_prev
        FROM com.cuentas_pagar WHERE compra_id = v_cid FOR UPDATE;

        IF NOT FOUND THEN
            SELECT COALESCE(pv.dias_credito, 0) INTO v_dias
            FROM com.compras c JOIN com.proveedores pv ON pv.proveedor_id = c.proveedor_id
            WHERE c.compra_id = v_cid;

            INSERT INTO com.cuentas_pagar (compra_id, monto_total, fecha_vencimiento)
            VALUES (v_cid, v_tot, CURRENT_DATE + v_dias)
            RETURNING cuenta_pagar_id INTO v_cp;
        ELSIF v_prev <> v_tot THEN
            UPDATE com.cuentas_pagar SET monto_total = v_tot
            WHERE cuenta_pagar_id = v_cp AND estado <> 'LIQUIDADA';
        END IF;

        IF v_fp <> 'CREDITO' THEN
            SELECT COALESCE(SUM(monto), 0) INTO v_pagado
            FROM com.pagos_proveedor
            WHERE cuenta_pagar_id = v_cp AND referencia = 'CONTADO';

            IF v_pagado < v_tot THEN
                INSERT INTO com.pagos_proveedor
                    (cuenta_pagar_id, forma_pago_id, monto, usuario_id, turno_caja_id, referencia)
                SELECT v_cp, forma_pago_id, v_tot - v_pagado, usuario_id, turno_caja_id, 'CONTADO'
                FROM com.compras WHERE compra_id = v_cid;
            ELSIF v_pagado > v_tot THEN
                v_exceso := v_pagado - v_tot;
                FOR v_pid, v_pmonto IN SELECT pago_proveedor_id, monto
                                       FROM com.pagos_proveedor
                                       WHERE cuenta_pagar_id = v_cp AND referencia = 'CONTADO'
                                       ORDER BY pago_proveedor_id DESC FOR UPDATE
                LOOP
                    EXIT WHEN v_exceso <= 0;
                    IF v_pmonto <= v_exceso THEN
                        DELETE FROM com.pagos_proveedor WHERE pago_proveedor_id = v_pid;
                        v_exceso := v_exceso - v_pmonto;
                    ELSE
                        UPDATE com.pagos_proveedor SET monto = v_pmonto - v_exceso
                        WHERE pago_proveedor_id = v_pid;
                        v_exceso := 0;
                    END IF;
                END LOOP;
            END IF;
        END IF;
    END IF;
    RETURN NULL;
END $$;

DROP TRIGGER IF EXISTS trg_det_compra_totales ON com.compra_detalles;
CREATE TRIGGER trg_det_compra_totales
AFTER INSERT OR DELETE ON com.compra_detalles
FOR EACH ROW EXECUTE FUNCTION com.fn_recalc_totales_compra();

CREATE OR REPLACE FUNCTION com.fn_pago_proveedor_post()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    UPDATE com.cuentas_pagar
       SET monto_pagado = monto_pagado + NEW.monto,
           estado = CASE WHEN monto_pagado + NEW.monto >= monto_total
                         THEN 'LIQUIDADA' ELSE 'PARCIAL' END
     WHERE cuenta_pagar_id = NEW.cuenta_pagar_id;

    IF NEW.turno_caja_id IS NOT NULL THEN
        PERFORM fin.fn_movimiento_caja(
            NEW.turno_caja_id, 'SALIDA', 'PAGO_PROVEEDOR', NEW.monto,
            NEW.forma_pago_id, 'com.pagos_proveedor', NEW.pago_proveedor_id, NEW.usuario_id);
    END IF;
    RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS trg_pago_proveedor_post ON com.pagos_proveedor;
CREATE TRIGGER trg_pago_proveedor_post AFTER INSERT ON com.pagos_proveedor
FOR EACH ROW EXECUTE FUNCTION com.fn_pago_proveedor_post();

-- Devolución a proveedor sale del almacén
CREATE OR REPLACE FUNCTION com.fn_devolucion_detalle_post()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE v_almacen INT; v_motivo INT;
BEGIN
    SELECT almacen_id INTO v_almacen
    FROM com.devoluciones_compra WHERE devolucion_id = NEW.devolucion_id;
    SELECT motivo_id INTO v_motivo FROM cat.motivos_movimiento WHERE clave = 'DEVOLUCION_COMPRA';

    PERFORM inv.fn_registrar_movimiento(
        NEW.producto_id, v_almacen, 'SALIDA', NEW.cantidad,
        v_motivo, NEW.costo_unitario, 'com.devoluciones_compra', NEW.devolucion_id);
    RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS trg_devolucion_compra_detalle ON com.devolucion_compra_detalles;
CREATE TRIGGER trg_devolucion_compra_detalle AFTER INSERT ON com.devolucion_compra_detalles
FOR EACH ROW EXECUTE FUNCTION com.fn_devolucion_detalle_post();

-- ---------- Gastos y otros ingresos tocan caja ----------
CREATE OR REPLACE FUNCTION fin.fn_gasto_post()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.turno_caja_id IS NOT NULL THEN
        PERFORM fin.fn_movimiento_caja(NEW.turno_caja_id, 'SALIDA', 'GASTO_OPERATIVO',
            NEW.monto, NEW.forma_pago_id, 'fin.gastos', NEW.gasto_id, NEW.usuario_id);
    END IF;
    RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS trg_gasto_post ON fin.gastos;
CREATE TRIGGER trg_gasto_post AFTER INSERT ON fin.gastos
FOR EACH ROW EXECUTE FUNCTION fin.fn_gasto_post();

CREATE OR REPLACE FUNCTION fin.fn_ingreso_otro_post()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.turno_caja_id IS NOT NULL THEN
        PERFORM fin.fn_movimiento_caja(NEW.turno_caja_id, 'ENTRADA', 'OTRO_INGRESO',
            NEW.monto, NEW.forma_pago_id, 'fin.ingresos_otros',
            NEW.ingreso_otro_id, NEW.usuario_id);
    END IF;
    RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS trg_ingreso_otro_post ON fin.ingresos_otros;
CREATE TRIGGER trg_ingreso_otro_post AFTER INSERT ON fin.ingresos_otros
FOR EACH ROW EXECUTE FUNCTION fin.fn_ingreso_otro_post();

-- Depósito de renta toca caja (ENTRADA al turno asociado)
CREATE OR REPLACE FUNCTION ven.fn_renta_post()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.turno_caja_id IS NOT NULL AND NEW.deposito > 0 THEN
        PERFORM fin.fn_movimiento_caja(NEW.turno_caja_id, 'ENTRADA', 'DEPOSITO_GARANTIA_RENTA',
            NEW.deposito, NEW.forma_pago_id, 'ven.rentas', NEW.renta_id, NEW.usuario_id);
    END IF;
    RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS trg_renta_post ON ven.rentas;
CREATE TRIGGER trg_renta_post AFTER INSERT ON ven.rentas
FOR EACH ROW EXECUTE FUNCTION ven.fn_renta_post();

-- ---------- Corte de caja: cierra turno y congela histórico ----------
CREATE OR REPLACE FUNCTION fin.fn_cerrar_turno(
    p_turno BIGINT,
    p_monto_contado NUMERIC,
    p_usuario_cierre INT DEFAULT NULL,
    p_notas TEXT DEFAULT NULL
) RETURNS BIGINT
LANGUAGE plpgsql AS $$
DECLARE
    v_estado TEXT; v_caja INT; v_usr INT; v_alm INT;
    v_apertura NUMERIC; v_apertura_en TIMESTAMPTZ;
    v_num INTEGER; v_sub NUMERIC; v_iva NUMERIC; v_desc NUMERIC;
    v_tot NUMERIC; v_costo NUMERIC;
    v_ent_eff NUMERIC; v_sal_eff NUMERIC; v_ent_dig NUMERIC; v_sal_dig NUMERIC;
    v_desg_e JSONB; v_desg_s JSONB; v_desg_fp JSONB;
    v_perdidas NUMERIC; v_esperado NUMERIC; v_id BIGINT;
BEGIN
    SELECT estado, caja_id, usuario_id, monto_apertura, apertura_en
      INTO v_estado, v_caja, v_usr, v_apertura, v_apertura_en
    FROM fin.turnos_caja WHERE turno_caja_id = p_turno FOR UPDATE;

    IF v_estado IS NULL THEN RAISE EXCEPTION 'Turno % no existe', p_turno USING ERRCODE = 'P0301'; END IF;
    IF v_estado <> 'ABIERTO' THEN RAISE EXCEPTION 'El turno % ya esta cerrado', p_turno USING ERRCODE = 'P0300'; END IF;
    IF p_monto_contado IS NULL OR p_monto_contado < 0 THEN
        RAISE EXCEPTION 'El monto contado es obligatorio y no puede ser negativo' USING ERRCODE = 'P0302';
    END IF;

    SELECT almacen_id INTO v_alm FROM fin.cajas WHERE caja_id = v_caja;

    -- Ventas del turno
    SELECT COUNT(*), COALESCE(SUM(v.subtotal),0), COALESCE(SUM(v.iva),0),
           COALESCE(SUM(v.descuento_total),0), COALESCE(SUM(v.total),0)
      INTO v_num, v_sub, v_iva, v_desc, v_tot
    FROM ven.ventas v
    WHERE v.turno_caja_id = p_turno AND v.estado = 'COMPLETADA';

    SELECT COALESCE(SUM(d.cantidad * d.costo_unitario), 0) INTO v_costo
    FROM ven.venta_detalles d
    JOIN ven.ventas v ON v.venta_id = d.venta_id
    WHERE v.turno_caja_id = p_turno AND v.estado = 'COMPLETADA';

    -- Movimientos del turno (efectivo vs digital + desgloses JSONB)
    WITH mv AS (
        SELECT mc.tipo, mc.concepto, mc.monto,
               COALESCE(fp.es_efectivo, true) AS es_eff,
               COALESCE(fp.nombre, 'EFECTIVO') AS forma
        FROM fin.movimientos_caja mc
        LEFT JOIN cat.formas_pago fp ON fp.forma_pago_id = mc.forma_pago_id
        WHERE mc.turno_caja_id = p_turno AND mc.concepto <> 'APERTURA'
    )
    SELECT
        COALESCE(SUM(monto) FILTER (WHERE tipo='ENTRADA' AND es_eff), 0),
        COALESCE(SUM(monto) FILTER (WHERE tipo='SALIDA'  AND es_eff), 0),
        COALESCE(SUM(monto) FILTER (WHERE tipo='ENTRADA' AND NOT es_eff), 0),
        COALESCE(SUM(monto) FILTER (WHERE tipo='SALIDA'  AND NOT es_eff), 0),
        (SELECT COALESCE(jsonb_object_agg(concepto, m ORDER BY concepto), '{}'::jsonb)
           FROM (SELECT concepto, SUM(monto) m FROM mv WHERE tipo='ENTRADA' GROUP BY concepto) x),
        (SELECT COALESCE(jsonb_object_agg(concepto, m ORDER BY concepto), '{}'::jsonb)
           FROM (SELECT concepto, SUM(monto) m FROM mv WHERE tipo='SALIDA' GROUP BY concepto) x),
        (SELECT COALESCE(jsonb_object_agg(forma, m ORDER BY forma), '{}'::jsonb)
           FROM (SELECT forma, SUM(monto) m FROM mv GROUP BY forma) x)
    INTO v_ent_eff, v_sal_eff, v_ent_dig, v_sal_dig, v_desg_e, v_desg_s, v_desg_fp
    FROM mv;

    -- Pérdidas de inventario del turno a costo (deterioro / uso interno / muestras)
    SELECT COALESCE(SUM(m.cantidad * COALESCE(m.costo_unitario, p.costo_actual)), 0)
      INTO v_perdidas
    FROM inv.movimientos_inventario m
    JOIN inv.productos p ON p.producto_id = m.producto_id
    JOIN cat.motivos_movimiento mo ON mo.motivo_id = m.motivo_id
    WHERE m.almacen_id = v_alm
      AND m.tipo = 'SALIDA'
      AND mo.clave IN ('DETERIORO','USO_INTERNO','MUESTRA')
      AND m.creado_en BETWEEN v_apertura_en AND now();

    v_esperado := v_apertura + v_ent_eff - v_sal_eff;

    UPDATE fin.turnos_caja
       SET estado         = 'CERRADO',
           cierre_en      = now(),
           monto_esperado = v_esperado,
           monto_contado  = p_monto_contado,
           diferencia     = p_monto_contado - v_esperado,
           observaciones  = COALESCE(NULLIF(p_notas,''), observaciones)
     WHERE turno_caja_id = p_turno;

    INSERT INTO fin.cortes_caja
        (turno_caja_id, caja_id, almacen_id, usuario_id, usuario_cierre_id,
         apertura_en, num_ventas, subtotal, iva, descuentos, total_vendido,
         costo_ventas, fondo_apertura, entradas_efectivo, salidas_efectivo,
         dinero_esperado, dinero_contado, diferencia,
         ingresos_no_efectivo, egresos_no_efectivo, perdidas_inventario,
         desglose_entradas, desglose_salidas, desglose_formas_pago, observaciones)
    VALUES
        (p_turno, v_caja, v_alm, v_usr, COALESCE(p_usuario_cierre, v_usr),
         v_apertura_en, v_num, v_sub, v_iva, v_desc, v_tot,
         v_costo, v_apertura, v_ent_eff, v_sal_eff,
         v_esperado, p_monto_contado, p_monto_contado - v_esperado,
         v_ent_dig, v_sal_dig, v_perdidas,
         v_desg_e, v_desg_s, v_desg_fp, p_notas)
    RETURNING corte_id INTO v_id;

    RETURN v_id;
END $$;

COMMENT ON FUNCTION fin.fn_cerrar_turno IS
'Corte de caja transaccional: calcula ventas/utilidad/margen/efectivo esperado,
cierra el turno y congela todo en fin.cortes_caja (historico).';

-- ---------- Promociones ----------
CREATE OR REPLACE FUNCTION ven.fn_promo_para_producto(
    p_producto BIGINT, p_cantidad NUMERIC,
    p_precio_unit NUMERIC, p_cliente BIGINT DEFAULT NULL
)
RETURNS TABLE (promo_id BIGINT, promo_nombre TEXT, promo_tipo TEXT,
               beneficio NUMERIC, detalle TEXT)
LANGUAGE plpgsql STABLE AS $$
BEGIN
    RETURN QUERY
    SELECT pr.promocion_id,
           pr.nombre::TEXT,
           pr.tipo::TEXT,
           CASE pr.tipo
               WHEN 'PRECIO_ESPECIAL' THEN
                   GREATEST(p_precio_unit - COALESCE(pr.precio_especial, 0), 0) * p_cantidad
               WHEN 'DESCUENTO_PRODUCTO' THEN
                   COALESCE(pr.valor_pct/100 * p_precio_unit * p_cantidad, pr.valor_monto)
               WHEN 'POR_CANTIDAD' THEN
                   CASE WHEN p_cantidad >= COALESCE(pr.compra_min_cantidad, 0)
                        THEN COALESCE(pr.valor_pct/100 * p_precio_unit * p_cantidad,
                                      pr.valor_monto)
                        ELSE 0 END
               WHEN 'NXM' THEN
                   FLOOR(p_cantidad / NULLIF(pr.lleva, 0))
                     * (pr.lleva - pr.paga) * p_precio_unit
               ELSE 0 END::numeric AS beneficio,
           ('[' || pr.tipo || '] ' || COALESCE(pr.descripcion, ''))::TEXT
    FROM ven.promociones pr
    LEFT JOIN ven.promocion_productos pp
           ON pp.promocion_id = pr.promocion_id AND pp.producto_id = p_producto
    LEFT JOIN ven.promocion_categorias pc
           ON pc.promocion_id = pr.promocion_id
          AND pc.categoria_id = (SELECT categoria_id FROM inv.productos
                                 WHERE producto_id = p_producto)
    WHERE pr.estado = 'ACTIVA'
      AND CURRENT_TIMESTAMP BETWEEN pr.vigencia_desde
                                AND COALESCE(pr.vigencia_hasta, 'infinity'::timestamptz)
      AND EXTRACT(ISODOW FROM CURRENT_TIMESTAMP)::smallint = ANY(pr.dias_semana)
      AND (pr.hora_desde IS NULL OR CURRENT_TIME BETWEEN pr.hora_desde
                                    AND COALESCE(pr.hora_hasta, '23:59:59'::time))
      AND (pp.producto_id IS NOT NULL OR pc.categoria_id IS NOT NULL)
      AND (pr.compra_min_cantidad IS NULL OR p_cantidad >= pr.compra_min_cantidad)
      AND (NOT pr.solo_mayoristas OR EXISTS (
              SELECT 1 FROM ven.clientes c
              WHERE c.cliente_id = p_cliente AND c.es_mayorista))
      AND (pr.max_usos_total IS NULL OR pr.usos_actual < pr.max_usos_total)
    ORDER BY 4 DESC
    LIMIT 1;
END $$;

CREATE OR REPLACE FUNCTION ven.fn_registrar_uso_promo(
    p_promocion BIGINT, p_venta BIGINT, p_cliente BIGINT,
    p_descuento NUMERIC, p_usuario INT
) RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE v_max_total INT; v_usados INT; v_max_cli INT; v_usos_cli INT;
BEGIN
    SELECT max_usos_total, usos_actual, max_usos_cliente
      INTO v_max_total, v_usados, v_max_cli
    FROM ven.promociones WHERE promocion_id = p_promocion FOR UPDATE;

    IF v_max_total IS NOT NULL AND v_usados >= v_max_total THEN
        RAISE EXCEPTION 'Promocion % agotada', p_promocion USING ERRCODE = 'P0400';
    END IF;

    IF v_max_cli IS NOT NULL AND p_cliente IS NOT NULL THEN
        SELECT COUNT(*) INTO v_usos_cli
        FROM ven.promocion_usos
        WHERE promocion_id = p_promocion AND cliente_id = p_cliente;
        IF v_usos_cli >= v_max_cli THEN
            RAISE EXCEPTION 'El cliente alcanzo el limite de usos de esta promocion' USING ERRCODE = 'P0401';
        END IF;
    END IF;

    INSERT INTO ven.promocion_usos
        (promocion_id, venta_id, cliente_id, monto_descuento, usuario_id)
    VALUES (p_promocion, p_venta, p_cliente, p_descuento, p_usuario);

    UPDATE ven.promociones SET usos_actual = usos_actual + 1
    WHERE promocion_id = p_promocion;
END $$;

-- ---------- Traslados entre almacenes ----------
CREATE OR REPLACE FUNCTION inv.fn_aplicar_traslado(
    p_origen INT, p_destino INT, p_usuario INT,
    p_items JSONB
) RETURNS BIGINT
LANGUAGE plpgsql AS $$
DECLARE v_traslado BIGINT; v_item JSONB; v_mot_salida INT; v_mot_entrada INT;
BEGIN
    INSERT INTO inv.traslados (folio, almacen_origen, almacen_destino, usuario_id)
    VALUES (cfg.fn_siguiente_folio('TRASLADO'), p_origen, p_destino, p_usuario)
    RETURNING traslado_id INTO v_traslado;

    SELECT motivo_id INTO v_mot_salida  FROM cat.motivos_movimiento WHERE clave = 'TRASLADO_SALIDA';
    SELECT motivo_id INTO v_mot_entrada FROM cat.motivos_movimiento WHERE clave = 'TRASLADO_ENTRADA';

    FOR v_item IN SELECT * FROM jsonb_array_elements(p_items) LOOP
        INSERT INTO inv.traslado_detalles (traslado_id, producto_id, cantidad)
        VALUES (v_traslado, (v_item->>'producto')::bigint, (v_item->>'cantidad')::numeric);

        PERFORM inv.fn_registrar_movimiento((v_item->>'producto')::bigint, p_origen,
                    'SALIDA', (v_item->>'cantidad')::numeric, v_mot_salida,
                    NULL, 'inv.traslados', v_traslado, p_usuario);
        PERFORM inv.fn_registrar_movimiento((v_item->>'producto')::bigint, p_destino,
                    'ENTRADA', (v_item->>'cantidad')::numeric, v_mot_entrada,
                    NULL, 'inv.traslados', v_traslado, p_usuario);
    END LOOP;
    RETURN v_traslado;
END $$;

-- ============================================================================
-- N. VISTAS DE NEGOCIO (núcleo; las analíticas completas están en el documento)
-- ============================================================================
\ir vistas_core.sql

-- ============================================================================
-- Ñ. PERMISOS DEL ROL DE APLICACIÓN
-- ============================================================================
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES
    IN SCHEMA cat, cfg, rh, seg, inv, com, ven, fin, fis TO ferreteria_app;
GRANT USAGE, SELECT ON ALL SEQUENCES
    IN SCHEMA cat, cfg, rh, seg, inv, com, ven, fin, fis TO ferreteria_app;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA cat, cfg, rh, seg, inv, com, ven, fin, fis
    TO ferreteria_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA cat, cfg, rh, seg, inv, com, ven, fin, fis
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ferreteria_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA cat, cfg, rh, seg, inv, com, ven, fin, fis
    GRANT USAGE, SELECT ON SEQUENCES TO ferreteria_app;

-- Endurecimiento: ledger append-only y auditoría sin borrado
REVOKE DELETE ON inv.movimientos_inventario, fin.movimientos_caja, seg.auditoria
    FROM ferreteria_app;

SELECT 'PASO 2 COMPLETO: tablas, triggers y permisos creados.' AS resultado;
