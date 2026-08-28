# Ferretería "El Tornillo Feliz" — PLAN FRONTEND (React + Vite + TS)

> Documento fuente único para construir el frontend. Derivado de: esquema real de la BD
> (`ferreteriaDB/scripts/*`), API real del backend (`ferreteriaBackend/src/main/java`),
> tests de backend, vistas SQL (`vistas_core.sql`), parametría y migraciones (V1/V2),
> y configuración (`application.yml`).

---

## 1. Contexto y objetivos

El negocio es una ferretería mexicana (moneda **MXN**, zona **`America/Mexico_City`**)
operada por roles. El backend (`ferreteria-backend`, Spring Boot 3, PostgreSQL) ya está
terminado y **verde** (433 tests, gates JaCoCo). Este frontend es una SPA de **escritorio
interna** (caja, inventario, reportes) pero responsive: encargados trabajan también en
tablet.

Objetivos:
- **Punto de venta (POS)** rápido: buscar producto por código/barras/nombre, carrito, cobro
  con formas de pago SAT, cliente, ticket.
- **Operación diaria**: inventario + movimientos (filtro por día), compras, caja/turnos,
  cortes, gastos, nómina.
- **Reportes y dashboard** por **rango de fechas** (default = hoy; el backend exige
  `fechaInicio`/`fechaFin` o asume hoy — **todos** los reportes/movimientos usan este rango).
- **Seguridad**: roles, sesión única, refresh rotativo (JWT).
- **Diseño** de ferretería: naranja cálido, utilitario, sobrio, legible y accesible (ADA).

Fuera de alcance (no existe en backend): no hay "tickets" como tabla (se imprime desde
`ven.ventas.folio`), no hay módulo de "Puntos de venta" tipo kiosko público. El login público
(`/auth/register`) existe pero **no se expone en la UI** (solo login de empleados).

---

## 2. Stack elegido

| Área | Decisión | Por qué |
|---|---|---|
| Framework | **React 19 + Vite 8 + TypeScript 6** (proyecto ya scaffolded) | Uno existente, React Compiler activo |
| Routing | **react-router-dom v7** (declarativo, data router) | SPA multi-módulo con guards por rol |
| Server state | **@tanstack/react-query v5** | cache/paginación/retry/mutations |
| Cliente HTTP | **axios** (interceptors) | refresh rotativo + envelope + requestId |
| Estado de sesión | **zustand + persist** (tokens en `localStorage`) | start rápido, tipado |
| UI | Componentes propios + **Tailwind CSS v4** | design system de ferretería sin librería pesada |
| Iconos | **lucide-react** | líneas claras, tamaño controlado |
| Gráficas | **recharts** | dashboard (ventas por hora/día, top) |
| Fechas | API nativa JS + input `date` | LocalDate `yyyy-MM-dd`; Instant ISO-8601 |
| Moneda | `Intl.NumberFormat("es-MX",{style:"currency",currency:"MXN"})` | MXN con `$` |
| Lint/build | ESLint + `tsc -b` (scripts existentes) | ya configurados |

No se usará: librería de componentes pesada (MUI/AntD), Redux, i18n-runetime (es-MX fijo),
ORM en front, SSR (SPA + SEO básico del negocio).

---

## 3. Contratos con el backend (fijos, NO negociar)

### 3.1 Base URL
- Dev: **proxy de Vite** `/api → http://localhost:8080` (el backend **no configura CORS**).
- Prod: `VITE_API_URL` (default `/api`). Todas las rutas de la API parten de `/api/v1`.

### 3.2 Envelope
- Éxito no paginado: `{ success: true, data: {…} }`
- Éxito paginado (`Page`): `{ success: true, data: […], meta: { page, size, totalElements, totalPages } }`
- Error: `{ success: false, data: null, errorCode: <http>, codigo: <ErrorCode>, errorMessage, details?: [{field,error}], requestId?, instance }`
- `spring.jackson.non_null`: los campos `null` **no aparecen** en JSON → tratar como ausentes.
- **Regla del cliente API**: si `success===false` → lanzar `ApiError(codigo, mensaje)`;
  los errores `CREDENCIALES_INVALIDAS|TOKEN_EXPIRADO` manejan sesión; el resto → toast.

### 3.3 Request ID
- Enviar header `X-Request-Id` con un UUID por request (generado en el cliente).
- El backend lo ecoa y lo incluye en el `errorMessage` → mostrarlo en diálogos de error
  (soporte), junto a la URI (`instance`).

### 3.4 Autenticación (sesión única + refresh rotativo)
- `POST /api/v1/auth/login` `{username,password}` → `TokenResponse`:
  `{ accessToken, refreshToken, expiresInSeconds (=refresh TTL 72h), usuario: MeResponse }`
  - access: **8h**; refresh: **72h**.
  - `MeResponse`: `{ usuarioId, username, empleadoId, roles: string[], ultimoLogin, empleado:{empleadoId, nombreCompleto, puestoNombre, email, telefono, activo} }`
- `POST /api/v1/auth/refresh` `{refreshToken}` → nuevo `TokenResponse`. **El refresh se rota
  (el presentado se invalida)**. Error → 401 `TOKEN_EXPIRADO`.
- `POST /api/v1/auth/logout` `{refreshToken}` (público, sin Bearer) → `{revocado:true}`.
- Resto de rutas: header `Authorization: Bearer <accessToken>`.
- **Flujo del interceptor axios:**
  1. Adjunta Bearer si hay sesión.
  2. En `response.error` con `codigo===TOKEN_EXPIRADO` (o HTTP 401) y sin más de 1 reintento
     **y** el request era autenticado con **mutex** (una sola refresh en vuelo):
     `POST /auth/refresh` con el refresh guardado → guarda nuevo par → reintenta el request original.
     Si el refresh falla → **logout local** → redirigir `/login?expired=1`.
  3. **Sesión única**: al hacer login el backend revoca sesiones previas; el front solo debe
     propagar el 401 → a login. No hay UI multiclier.
- Tokens en `localStorage` (zustand `persist`). Refresh a `localStorage` también (rotativo:
  cada uso lo sustituye).

### 3.5 Roles, permisos y navegación (del seed `03_parametria.sql`)
| Rol | Alcance |
|---|---|
| `ADMINISTRADOR` | Todo (incluye **Administración**: `/usuarios`, `/roles`, `/permisos`, `/empleados`) |
| `GERENTE` | Operación + reportes (no administración) |
| `ENCARGADO_CAJA` | POS, cobranza, caja/turnos/cortes, reportes |
| `VENDEDOR` | Solo punto de venta |
| `ALMACENISTA` | Inventario, movimientos, traslados, conteos |
| `AUDITOR` | Solo reportes (lectura) |

Guards: ruta = `{ roles?: string[] }`. Sin permiso → página "Acceso denegado" (403). Si el
usuario no tiene ningún rol del módulo, el ítem del menú no se renderiza.

### 3.6 Códigos de error relevantes para UI
`CREDENCIALES_INVALIDAS(401)`, `TOKEN_EXPIRADO(401)`, `ACCESO_DENEGADO(403)`,
`RECURSO_NO_ENCONTRADO(404)`, `CAMPO_REQUERIDO(400)` (valida `details` en forms),
`PAGINACION_INVALIDA(400)`, `VALOR_INVALIDO(400)` (rango de fechas invertido),
`STOCK_INSUFICIENTE(409)`, `CREDITO_EXCEDIDO(422)`, `CREDITO_NO_DISPONIBLE(422)`,
`TURNO_YA_CERRADO(409)`.

---

## 4. Arquitectura de carpetas

```
ferreteriaFront/
├─ index.html                     # SEO estático (lang es-MX, meta, JSON-LD, theme-color)
├─ vite.config.ts                 # alias @/ , proxy /api, React Compiler
├─ src/
│  ├─ main.tsx                    # Providers: QueryClient, Router
│  ├─ App.tsx                     # RouterProvider + AppBoundary
│  ├─ lib/
│  │  ├─ api/client.ts            # axios instance + interceptor refresh + requestId
│  │  ├─ api/endpoints.ts         # funciones tipadas por endpoint
│  │  ├─ api/types.ts             # DTOs (copiados 1:1 del backend)
│  │  ├─ format.ts                # moneda, fecha, número, hora (es-MX, MXN)
│  │  ├─ rango.ts                 # helper fechaInicio/fechaFin (default hoy) + validación
│  │  └─ pager.ts                 # paginación + sort → query params
│  ├─ store/
│  │  └─ auth.ts                  # zustand: tokens, usuario, roles, login/logout/refresh
│  ├─ router/
│  │  ├─ router.tsx               # createBrowserRouter + rutas por módulo
│  │  └─ guards.tsx               # RequireAuth, RequireRoles, RedirectIfAuthed
│  ├─ components/ui/              # Button, Input, Select, Table, Card, Badge, Dialog,
│  │                              #   Toast, EmptyState, Spinner, DateRangePicker, Pagination
│  ├─ components/layout/          # AppShell (sidebar+topbar+responsive), Sidebar, Topbar
│  ├─ components/errors/          # ErrorBoundary, AccessDenied, NotFound
│  ├─ features/
│  │  ├─ auth/     (Login)
│  │  ├─ dashboard/(KPI cards, charts)
│  │  ├─ pos/      (search, carrito, pago, ticket)
│  │  ├─ catalogo/ (productos, categorias(arbol), marcas, unidades, clientes, proveedores)
│  │  ├─ inventario/(stock, movimientos, traslados, conteos)
│  │  ├─ ventas/   (cotizaciones, devoluciones, rentas, creditos/cobranza)
│  │  ├─ compras/  (compras, cuentas_pagar, facturas_proveedor)
│  │  ├─ caja/     (cajas, turnos, movimientos_caja, gastos, ingresos, cortes)
│  │  ├─ reportes/ (ventas-totales, top-productos, mejores-dias, horas-pico, cierre-diario)
│  │  ├─ rrhh/     (empleados, nomina)
│  │  ├─ seguridad/(usuarios, roles, permisos)
│  │  └─ fiscal/   (facturas CFDI, XML)
│  └─ hooks/       # useQuery/useMutation wrappers + usePager + useDocumentTitle
```

---

## 5. Guía de diseño — "Ferretería El Tornillo Feliz"

### 5.1 Principios
- **Utilitario y honesto**: superficies planas, bordes definidos, estado claro.
- **Orange cálido** como color primario (herramientas, seguridad, energía).
- Densidad media: la caja necesita ver mucha info en pantalla completa; el resto cómodo.
- Contraste AA (WCAG): naranja oscuro sobre fondo claro para texto; nunca naranja claro sobre blanco.
- **Numeración tabular** en montos (`font-variant-numeric: tabular-nums`) → columnas alineadas.
- Toda acción destructiva → `Dialog` de confirmación con input tipo el nombre del recurso (si aplica).
- Estados: carga (`Spinner`), vacío (`EmptyState` con icono + acción), error (toast + `ErrorBoundary`).

### 5.2 Paleta (Tailwind theme)
| Token | Valor | Uso |
|---|---|---|
| `--color-primary` | `#c2410c` (orange-700) | acciones principales, texto activo |
| `--color-primary-hover` | `#9a3412` (orange-800) | hover/active |
| `--color-accent` | `#f97316` (orange-500) | acentos, marca, banners |
| `--color-warm-bg` | `#fff7ed` (orange-50) / `#fafaf9` (stone-50) | fondos alternados |
| `--color-surface` | `#ffffff` | tarjetas/tablas |
| `--color-ink` | `#1c1917` (stone-900) | texto principal |
| `--color-muted` | `#57534e` (stone-600) | texto secundario |
| `--color-line` | `#e7e5e4` (stone-200) | bordes/divisores |
| estados | `green-600` ok/stock, `red-600` peligro/déficit, `amber-500` advertencia, `blue-600` info | semáforos |
| tipo formato | `P/E` (PENDIENTE=amber, LIQUIDADA=green, CANCELADA=red, VIGENTE=blue) | badges de estado |

### 5.3 Tipografía e iconos
- Familia: `Inter` (variable, opcional webfont) con fallback `ui-sans-serif, system-ui`.
- Escala: `text-xs` tablas densas → `text-sm` base → `text-2xl/3xl` KPIs.
- Números: `tabular-nums` para montos y cantidades. Montos con `Intl es-MX` (2 decimales, `$ `).
- Iconos: `lucide-react`; uso funcional (nav, estados, acciones). Tamaño `1rem–1.25rem`.

### 5.4 Layout
- **AppShell**: sidebar fija (escritorio, 240px) + topbar con buscador global (POS) y usuario
  con menú (cambiar contraseña, cerrar sesión). En < md: sidebar colapsable (overlay) + bottom nav simple.
- Contenido: `max-w-[1400px]` centrado; cabecera de página con título + acciones.
- **Dashboard**: grid de 9 KPI cajas (ver §8) + `DateRangePicker` (hoy | ayer | últimos 7 |
  este mes | periodo libre) que se aplica a **todos** los reportes y movimientos.

### 5.5 POS (pantalla crítica)
- Izquierda (2/3): buscador de producto (debounce), grid/tabla de resultados con stock y
  precios menudeo/mayoreo segun cliente; carrito editable (cantidad, quitar, descuento si permiso
  `V.DESCUENTO`).
- Derecha (1/3): resumen (subtotal, IVA, total), forma de pago (EFECTIVO/TARJETA...), cliente
  (toggle "menudeo/mayoreo/credito"), botón "Cobrar" (amarillo/naranja fuerte, teclado `F1`=`Buscar`, `F2`=`Cobrar`).
- Ticket: diálogo post-venta con folio `V-xxxx`, total, cambio, botón reimprimir.

---

## 6. Detalle de pantallas por módulo (mapa de rutas)

Rutas (todas bajo `/` autenticadas salvo `/login`):

| Módulo | Ruta | Roles (permiso) | Notas API |
|---|---|---|---|
| Login | `/login` | público | guard `RedirectIfAuthed` |
| Dashboard | `/dashboard` | REPORTES | `reportes/dashboard?fechaInicio&fechaFin` |
| POS | `/pos` | V.VENDER | `ventas` POST; busca `productos?q=` |
| Productos | `/catalogo/productos` | inventario/gerente | CRUD + query `q, categoriaId, marcaId, tipo` |
| Categorías | `/catalogo/categorias` | igual | `GET /categorias/arbol` (árbol) |
| Marcas / Unidades | `/catalogo/marcas`, `/catalogo/unidades` | igual | CRUD |
| Clientes | `/catalogo/clientes` | V.VENDER/GERENTE | CRUD + `q` |
| Proveedores | `/compras/proveedores` | GERENTE/ADMIN | CRUD |
| Inventario | `/inventario/stock` | I.AJUSTAR_STOCK o REPORTES | `/inventario?almacenId&soloBajoStock` |
| Movimientos | `/inventario/movimientos` | idem | `movimientos?fechaInicio&fechaFin[&productoId][&almacenId]` raw `movimientos`+rango (lista) |
| Traslados | `/inventario/traslados` | I.TRASLADAR | CRUD |
| Conteos | `/inventario/conteos` | I.AJUSTAR_STOCK | CRUD |
| Compras | `/compras` | C.COMPRAR | CRUD + detecta stock |
| Cuentas a pagar | `/compras/cuentas-pagar` | C.PAGAR | `GET /cuentas-pagar?estado=` |
| Facturas proveedor | `/compras/facturas-proveedor` | C.PAGAR | `GET /reportes/facturas-vencidas`, `facturas-pendientes` |
| Cotizaciones | `/ventas/cotizaciones` | V.VENDER | CRUD + convertir |
| Devoluciones | `/ventas/devoluciones` | V.CANCELAR | CRUD |
| Rentas | `/ventas/rentas` | V.RENTAS | CRUD + devolución |
| Cobranza/Crédito | `/ventas/cobranza` | V.COBRANZA | `creditos/cobranza?estado`, `pagos-cliente` POST |
| Cajas/Turnos | `/caja/cajas` | F.CAJA_ABRIR | `/cajas`, `/cajas/{id}/turnos` |
| Movimientos caja | (dentro de turno) | F.CAJA_ABRIR | POST/GET `/cajas/{id}/turnos/{turnoId}/movimientos` |
| Cortes | `/caja/cortes` | F.CAJA_CORTAR | POST `/cajas/{id}/turnos/{tid}/corte`; `GET /cortes-caja` |
| Gastos / Ingresos | `/caja/gastos`, `/caja/ingresos` | F.GASTOS_CREAR | CRUD |
| Nómina | `/rrhh/nomina` | ADMIN | CRUD + pagar/cancelar |
| Empleados | `/rrhh/empleados` | ADMINISTRADOR | CRUD (incluye crear usuario+roles en alta) |
| Usuarios | `/seguridad/usuarios` | ADMINISTRADOR | CRUD + roles + password |
| Roles/Permisos | `/seguridad/roles` | ADMINISTRADOR | CRUD + `PUT {id}/permisos` |
| Facturas CFDI | `/fiscal/facturas` | REPORTES/ADMIN | CRUD + `GET {id}/xml` (dialog download) |
| Reportes | `/reportes/*` | ADM.REPORTES | todos con `fechaInicio&fechaFin` default hoy |

### 6.1 Todos los endpoints disponibles (mapa de referencia implementado en `endpoints.ts`)

Autenticación: `POST /auth/login`, `/auth/refresh`, `/auth/logout`, `/auth/change-password`, `GET /auth/me`.

Catálogo: `GET|POST|PUT|DELETE /clientes`, `/clientes/{id}`; `/proveedores`, `/proveedores/{id}`;
`/unidades-medida`; `/categorias`, `/categorias/arbol`, `/categorias/{id}`; `/marcas`;
`/productos?q&categoriaId&marcaId&tipo`, `/productos/{id}`.

Inventario: `/almacenes`; `/inventario?almacenId&soloBajoStock`, `/inventario/producto/{id}`;
`/movimientos?fechaInicio&fechaFin`, `POST /movimientos`; `/traslados`; `/conteos-fisicos`.

Ventas: `GET /ventas?almacenId&desde&hasta`, `POST /ventas`, `PATCH /ventas/{id}/cancelar`,
`GET /ventas/{id}`; `/cotizaciones` + `POST /{id}/convertir`; `/devoluciones`; `/rentas` +
`POST /{id}/devolucion`; `/creditos/cobranza?estado`, `/creditos/{clienteId}`; `POST /pagos-cliente`.

Compras: `/compras?almacenId&proveedorId&desde&hasta`, `POST /compras`; `GET /cuentas-pagar?estado`;
`GET /reportes/facturas-vencidas`, `/reportes/facturas-pendientes`, `/facturas-proveedor/{id}`.

Caja/Finanzas: `/cajas`, `/cajas/{id}/turnos`, `POST /cajas/{id}/turnos`,
`POST|GET /cajas/{id}/turnos/{tid}/movimientos`, `POST /cajas/{id}/turnos/{tid}/corte`,
`GET /cortes-caja`, `GET|POST /gastos`, `GET|POST /ingresos-otros`.

RH: `/empleados` (ADMIN), `/nomina` + `/{id}/pagar|cancelar`.

Seguridad: `/usuarios` + `/{id}`, `/{id}/password`, `/{id}/roles`; `/roles` + `/{id}/permisos`;
`/permisos`.

Fiscal: `/facturas?tipo&desde&hasta`, `POST /facturas`, `GET /facturas/{id}/xml`.

Reportes (rango): `GET /reportes/top-productos`, `mejores-clientes`, `ventas-totales`,
`mejores-vendedores`, `horas-pico`, `mejores-dias`, `dashboard`, `cierre-diario` — **todos**
con `fechaInicio/fechaFin` (default hoy).

---

## 7. Paginación, ordenamiento y búsqueda

- Params: `page` (0-based), `size` (default 20, máx 100), `sort` (campo o `campo,desc`).
- El envelope paginado expone `meta { page, size, totalElements, totalPages }`; el componente
  `Pagination` deriva de `meta` (numéricas + anterior/siguiente, con jump a page).
- Búsqueda: `q` con debounce (350ms) en listas maestras; mantener `q` y `page` en la URL
  (useSearchParams) para deep-linking.
- Hook `usePager()` centraliza `page/size/sort/q → URLSearchParams`.

---

## 8. Dashboard / Reportes

`GET /reportes/dashboard` devuelve (objeto):
`ventasEnRango, ticketsEnRango, ticketPromedioEnRango, saldoPorCobrar, cobranzaVencida,
valorInventario, productosAgotados, promocionesActivas, cajasAbiertas`.

KPI cards (icono + valor + label):
1. Ventas en rango (moneda)
2. Tickets (count)
3. Ticket promedio (moneda)
4. Saldo por cobrar (moneda, `warning` si > 0)
5. Cobranza vencida (red si > 0)
6. Valor de inventario (moneda)
7. Productos agotados (red si > 0, enlaza a `/inventario/stock?soloBajoStock=1`)
8. Promociones activas
9. Cajas abiertas (enlaza a `/caja/cajas`)

Gráficas (recharts) con el mismo rango:
- `horas-pico` → BarChart (hora → total).
- `mejores-dias` → BarChart (día semana → total acumulado).
- `ventas-totales` → AreaChart/LineChart por fecha (numVentas, totalVendido, utilidadBruta).
- `top-productos` → tabla rankeada (ranking mes/unidades) + Badge de posición.
- `mejores-clientes` / `mejores-vendedores` → tablas rankeadas.

**Fecha**: en modelos TopProducto/Major, `mes` es la `fechaInicio` del rango consultado (label).

---

## 9. Inferencias de negocio de la BD (funcionalidad que refuerza la UI)

- **Stock bajo**: `inv.vw_stock_bajo` (no expuesta vía rest; en UI reconstruir con
  `inventario?soloBajoStock=1` + tarjeta "cantidad sugerida de compra" si `alerta`).
- **Movimientos = ledger append-only**: no hay DELETE en UI; filtrar por día con
  `fechaInicio/fechaFin`.
- **Semáforo de línea de crédito**: `ven.vw_lineas_credito_uso` (verde <60%, amarillo 60-90%,
  rojo >90%) → duplicar la semántica en la cobranza con `saldo` por cuenta.
- **Facturas vencidas/pendientes**: `antiguedad`/`dias_para_vencer`/`alerta` → badges en
  cuentas por pagar.
- **TZ**: `fecha_local` ya viene calculada por la BD (America/Mexico_City). El front **no**
  convierte: renderiza tal cual (`yyyy-MM-dd`) y envía `desde/hasta` Instant en ISO (p. ej.
  `2026-01-01T00:00:00Z` local diurno cuando aplique) o `LocalDate` según el endpoint.

---

## 10. SEO y metadatos

- `index.html`: `lang="es-MX"`, `<title>` "Ferretería El Tornillo Feliz — Sistema de Punto de
  Venta", meta description, `theme-color #c2410c`, OG (title/description/type=website),
  robots index,follow, favicon con inspiración de engrane/llave.
- **JSON-LD** `HardwareStore` (`@type: "HardwareStore"`, name, currency MXN, address mx,
  openingHours) embebido en el head.
- Por ruta: hook `useDocumentTitle(segmento)` → `"Pedidos │ El Tornillo Feliz"` (sufijo marca).
- Semántica: `<header>`, `<nav aria-label>`, `<main>`, `<table>` con `<caption>`/`scope`,
  `aria-live` para toasts, `role="dialog"` para modales, labels en inputs, foco gestionado en
  diálogos (traps). Mapa del sitio no aplica (SPA interna), pero los títulos/meta de ruta ayudan.

---

## 11. Plan de implementación (punto por punto)

### Fase 0 — Cimientos (setup)
0.1 Instalar deps: `react-router-dom`, `@tanstack/react-query`, `axios`, `zustand`,
   `lucide-react`, `recharts`, `tailwindcss @tailwindcss/vite`.
0.2 `vite.config.ts`: alias `@` → `src`, plugin Tailwind, **proxy `/api`→`http://localhost:8080`**,
   mantener React Compiler.
0.3 `index.html`: lang es-MX, meta/OG/JSON-LD/theme-color, título de marca. Limpiar
   boilerplate (`App.tsx`, assets de ejemplo, favicon).
0.4 Establecer tokens de diseño (paleta §5.2) en `index.css` + estilos base (reset, tabular-nums,
   scrollbar, focus ring).
0.5 `tsconfig` paths `@/*`.
0.6 `src/lib/format.ts` + `rango.ts` (hoy, hoy-7, mes, validación `fin<inicio`).
0.7 Components UI base: `Button, Input, Select, Dialog, Table, Pagination, Badge, Card,
   EmptyState, Spinner, Toast, DateRangePicker`.
0.8 `ErrorBoundary` + `NotFound` + `AccessDenied`.

### Fase 1 — Auth + Shell
1.1 `lib/api/client.ts`: axios instance (baseURL, `X-Request-Id` uuid, interceptor Bearer,
   refresh mutex rotativo, desenvelope → `ApiError`, mapeo codigo→es-MX).
1.2 `store/auth.ts` (zustand persist): estado `{accessToken, refreshToken, usuario}`,
   acciones `login/refreshPares/logout`.
1.3 `features/auth/Login.tsx` + `guards.tsx` (`RequireAuth`, `RequireRoles`, `RedirectIfAuthed`,
    `ProtectedRoute`). Redirigir 401/`expired=1` a login con aviso "Tu sesión expiró o se cerró
    en otro dispositivo".
1.4 `components/layout/AppShell` (sidebar por rol §3.5, topbar usuario, responsive, logout).
1.5 `change-password` (diálogo desde topbar).
1.6 Smoke: login → dashboard vacío → logout.

### Fase 2 — Catálogos (crud genérico)
2.1 Patrón de tabla: `usePager` + `queryKey` + `<DataTable>` reutilizable.
2.2 Productos (búsqueda, filtros categoria/marca/tipo, alta/edición, activo/inactivo).
2.3 Categorías (árbol), Marcas, Unidades de medida.
2.4 Clientes (regimen, mayorista, crédito), Proveedores.

### Fase 3 — Inventario
3.1 Stock por almacen (+filtro bajo stock, semáforo).
3.2 Movimientos: `DateRangePicker` (default hoy) + filtros producto/almacen; alta de
   movimiento manual (ENTRADA/SALIDA, motivo).
3.3 Traslados (origen/destino/detalles) y Conteos físicos (cargar sistema vs fisico).

### Fase 4 — Ventas (POS primero, luego el resto)
4.1 POS: buscador, carrito, cliente, formas de pago, descuento (rol), cobro, ticket, teclado.
4.2 Historial de ventas (`/ventas?desde&hasta`) + detalle + cancelación (motivo).
4.3 Cotizaciones (crear/convertir), Devoluciones, Rentas (+devolución).
4.4 Cobranza: cuentas por cobrar + registrar pago (`pagos-cliente`).

### Fase 5 — Compras
5.1 Alta de compra (multi-detalle, proveedor, almacen, forma pago) + lista.
5.2 Cuentas por pagar + facturas vencidas/pendientes (badges).
5.3 Pagos a proveedor (UI: registro vía `pagos-cliente` NO; usar compra+caja).

### Fase 6 — Caja y finanzas
6.1 Cajas, apertura de turno (monto apertura), cierre (monto contado → corte).
6.2 Movimientos de caja (entrada/salida con concepto).
6.3 Gastos e ingresos otros.
6.4 Corte de caja detalle (desglose) + histórico de cortes.

### Fase 7 — Reportes y Dashboard
7.1 `DateRangePicker` global; dashboard KPI + charts.
7.2 Tablas rankeadas: top productos, mejores clientes/vendedores, horas pico, mejores días,
   ventas totales, cierre diario.

### Fase 8 — RH y seguridad
8.1 Empleados (alta crea usuario+roles), nómina (ciclo: capturar, pagar, cancelar).
8.2 Usuarios (roles, password, activo/inactivo), Roles (permisos con checkbox tree), Permisos.

### Fase 9 — Fiscal
9.1 Facturas CFDI (listado con filtros tipo/fechas, alta, ver XML en diálogo).

### Fase 10 — QA, robustez y cierre
10.1 End-to-end con **Playwright** (login, POS completo, movimiento con rango, corte,
   cambio de rol). 10.2 A11y audit (axe). 10.3 `bun run lint` + `tsc -b` limpias. 10.4 Build
   de producción, README del repo y verificacion del set de provisión (BD + backend + front).

---

## 12. Definición de listo (DoD) por fase

- Compila (`tsc -b`) y pasa `eslint` sin warnings introducidos.
- Los datos provienen del backend real (sin fixtures falsos en producción).
- Los formularios mapean errores `details` por campo y muestran toasts con `requestId/instance`.
- Toda ruta protegida por rol; test de "rol sin permiso" en cada fase.
- Cada fase deja build de preview funcional.

## 13. Riesgos y decisiones abiertas (registradas, con plan)

| Riesgo | Mitigación |
|---|---|
| Backend sin CORS | Proxy `/api` en Vite (dev). Para prod, reverse-proxy o mismo dominio. |
| Tokens en localStorage (XSS) | Policy CSP estricta; nunca pintar datos del token en DOM; sanitizar via React; refs de seguridad en el place. |
| Refresh rotativo en paralelo | Mutex de una sola refrescación en vuelo. |
| Registro público habilitado | No exponer `/register` en UI; es intencional (empleados se crean por ADMIN). |
| `desde/hasta` Instant vs LocalDate | Tabla §6 por endpoint; helpers `toInstant(dia)` para los que piden ISO. |

---

## 14. Referencias (fuentes de verdad)

- `ferreteriaDB/scripts/01_base_esquemas.sql`, `02_tablas.sql`, `03_parametria.sql`,
  `vistas_core.sql`, `05_dummy.sql`.
- `ferreteriaDB/migrations/*` (errcodes P0xxx, refresh tokens).
- `ferreteriaBackend/src/main/java/mx/ferreteria/api/**` (controllers/DTOs/Security).
- `application.yml` (puerto 8080, JWT 8h/72h, TZ America/Mexico_City, PG 6432).