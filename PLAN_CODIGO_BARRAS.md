# Plan: Lector de código de barras con cámara + búsqueda por código

## 1. Estado real (verificado en el repo)

| Capa | Estado |
|---|---|
| BD `inv.producto_codigos_barras` | **Existe** (`02_tablas.sql:283-287`): `codigo_barras PK`, `producto_id FK cascade`, `factor NUM>0 default 1`. **Sin triggers, sin auditoría**. Solo 5 códigos demo EAN (`05_dummy.sql:286-292`, ej. `7501234567001`→`MAR-001`). |
| Backend búsqueda `q` | `ProductoService.list` (`ProductoService.java:57-61`): 1º `codigo` exacto, 2º `nombre` parcial. **La tabla de barras JAMÁS se consulta** (no hay entity/repo). `GET /api/v1/productos?q=7501234567001` hoy devuelve **vacío**. |
| Frontend POS | `PosPage.tsx:86-90` detecta barras (`^\d{6,}$`, cubre EAN-8/13, UPC) y busca al vuelo (`qEfectivo`, :195-209) con insignia "código" (:765-787). Diseñado para escáner USB (teclado); **sin cámara** (cero `getUserMedia`/`BarcodeDetector`/`ZXing` en el repo). |
| Páginas que usan `apiProductos(q)` | POS, Compras, Conteos, Traslados, Cotizaciones, Rentas, Promociones, Productos-admin. Un solo fix de backend las beneficia a todas. |
| `factor` | Definido (unidades por escaneo, ej. código de paquete ×6) pero **sin usar en ningún flujo**. |

## 2. Contrato API (actual → propuesto, sin romper compatibilidad)

Actual: `GET /api/v1/productos?page=0&size=20&q=AMO-007&almacenId=1`
→ 1º `codigo` exacto → 2º `nombre` parcial.

Propuesto (nueva rama primera, resto igual):
1. `q` exacto en `producto_codigos_barras` → 1 producto + su `factor`.
2. Si no: `codigo` exacto (igual que hoy).
3. Si no: `nombre` parcial (igual que hoy).

`ProductoResponse`: agregar `factorEscaneo?: number` (solo presente
cuando el match fue por barras) para que el POS sume N piezas por pitido.
Opcional fase 2: `codigosBarras: string[]` en detalle/admin.

## 3. Triggers / BD a validar y agregar

Validar (no se tocan, la venta sigue igual — solo cambia el lookup):
- `trg_det_venta_valida`, `trg_det_venta_salida`, `trg_mov_stock`,
  `trg_kardex_no_upd` (`02_tablas.sql:1272-1326`): el flujo de venta no
  cambia, solo llega con el producto ya resuelto.
- `CHECK (factor > 0)`: ya existe en la tabla.

Agregar (coherencia con el resto del esquema):
- `trg_audit_barras`: `seg.fn_auditar` AFTER I/U/D en
  `producto_codigos_barras` (igual que `trg_audit_producto`, :1162-1163).
- `trg_touch_producto_por_barras`: al cambiar barras, tocar
  `productos.actualizado_en` (igual que `trg_touch_producto`, :1189-1190).
- Índice: la PK ya indexa el lookup exacto; nada más que agregar.

Datos: los 5 códigos de `05_dummy.sql` son **solo demo** — en prod hay que
cargar los EAN reales (importe masivo o alta en Productos-admin, fase 2).

## 4. CRUD de barras al crear/actualizar/desactivar producto (diseño)

Hoy `create/update/deactivate` (`ProductoService.java:135-204`) no tocan
la tabla (no existe entity). Diseño propuesto, todo en la misma
`@Transactional` del servicio:

- **Entity + repo nuevos**: `ProductoCodigoBarras`
  (`codigoBarras @Id String`, `producto @ManyToOne`, `factor`)
  + `CodigoBarrasRepository.findByCodigoBarras / findByProductoId`.
- **`ProductoRequest` += `codigosBarras: [{codigo, factor}]`** con
  validación: no vacíos, `factor > 0`, sin repetidos en la lista.
- **Crear**: guarda producto → guarda sus barras. Si un código ya es de
  OTRO producto → `409 REGISTRO_DUPLICADO` explícito (atrapar
  `DataIntegrityViolation`, porque el traductor BD solo mapea códigos P0
  y el `23505` caería en 500).
- **Actualizar**: estrategia *replace* (borra las que salen, agrega las
  nuevas). Simple y auditable vía el trigger de §3; un producto tiene
  pocos códigos.
- **Desactivar**: no hay borrado físico (`deactivate`, :199-204; el
  `ON DELETE CASCADE` queda como red de seguridad). La rama de búsqueda
  de §2 debe filtrar `producto.activo = true` para no vender fantasmas.
- **Admin (ProductosPage)**: lista dinámica de inputs código+factor en el
  formulario + columna/detalle con sus códigos.

## 5. Cámara del celular (frontend)

- **Requisito previo: HTTPS** (el mismo de `PLAN_APP_MOVIL.md` fase 1
  paso 6). `getUserMedia` y `BarcodeDetector` exigen contexto seguro;
  en `http://192.168.110.30` la cámara **no abre**. Sinergia: hacer PWA
  y escáner en la misma ventana.
- Implementación: hook compartido `useBarcodeScanner()`:
  1ª opción `BarcodeDetector` nativo (Chrome Android, gratis, sin peso);
  fallback `@zxing/browser` (Safari iOS). Botón 📷 junto al buscador del
  POS que abre viewfinder modal → al detectar: cierra, pone el texto en
  búsqueda y dispara el flujo `qEfectivo` existente (auto-agrega si hay
  match único, misma UX que el escáner USB).
- Permiso de cámara: el navegador lo pide una vez por origen; denegado =
  mensaje que indique dónde reactivarlo + fallback a teclado.
- Peso: ZXing solo se importa dinámico (`import()`) cuando no hay
  `BarcodeDetector`, para no engordar el bundle inicial.
- Alcance fase 1: **solo POS**. Compras/Conteos reutilizan el hook en
  fase 2 sin cambios de API.

## 5. Orden de ejecución

1. **Backend** (1): entity `ProductoCodigoBarras` + repo
   `findByCodigoBarras` + rama 1 en `list()` + `factorEscaneo` en
   response. Test: `q=7501234567001` → 1 match (hoy: vacío).
2. **BD** (2): triggers auditoría/touch + script de carga real de EAN.
3. **Frontend cámara** (1 día): hook + botón en POS + fallback ZXing.
   Requiere HTTPS LAN previo.
4. **Fase 2**: CRUD de barras en Productos-admin + hook en
   Compras/Conteos + `codigosBarras[]` en detalle.
5. **Aceptación**: escanear etiqueta real desde Android y iPhone →
   producto correcto agregado con factor; `q` por nombre/código
   intacto; rate limit sin cambios (mismo endpoint, perfil `default`).

## 6. Pruebas manuales con lo que hay hoy

- `GET /api/v1/productos?q=7501234567001` → vacío (confirma el gap).
- `GET /api/v1/productos?q=MAR-001` → match por `codigo` (ok).
- En POS, teclear `7501234567001` muestra insignia "código" pero sin
  resultados (front listo, backend no).
