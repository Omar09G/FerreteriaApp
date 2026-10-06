# ferreteria-front — SPA de la Ferretería (React 19 + Vite 8 + TS)

Frontend del Sistema Integral de Ferretería: SPA de escritorio interna (caja, inventario,
compras, reportes) responsive para tablet.
Backend: [`../ferreteriaBackend`](../ferreteriaBackend) · Base de datos: [`../ferreteriaDB`](../ferreteriaDB).
Index raíz: [`../README.md`](../README.md).

## Stack

React 19 + TypeScript 6 + Vite 8 (React Compiler activo) · react-router-dom v7 ·
@tanstack/react-query v5 · axios (interceptors) · zustand + persist · Tailwind CSS v4 ·
lucide-react · recharts.

## Requisitos

Bun 1.x y el backend corriendo (default `http://localhost:8080`).

## Arranque rápido

```bash
bun install        # instala según bun.lock
bun run dev        # Vite, http://localhost:5173 — proxy /api → http://localhost:8080 (sin CORS en backend)
bun run build      # tsc -b && vite build → dist/
bun run lint       # ESLint
bun run preview    # sirve dist/ localmente
```

### Variables de entorno

| Variable | Default | Uso |
|---|---|---|
| `VITE_API_URL` | `/api` | Base de la API (todas las rutas parten de `/api/v1`) |
| Vite dev proxy | `/api → http://localhost:8080` | En dev no se configura CORS en el backend |

## Arquitectura

```
src/
├─ features/       # módulos por dominio: pos, caja, ventas, compras, inventario,
│                  #   catalogo, reportes, rrhh, seguridad, fiscal, dashboard, auth
├─ lib/api/        # clientes axios por módulo + endpoints.ts + types.ts (contratos)
├─ router/         # rutas con guards por rol (roles: ADMINISTRADOR, GERENTE, ...)
├─ store/          # zustand: sesión (tokens en cookies HttpOnly, solo perfil en store) y estado de UI
├─ components/     # UI kit propio (design system de ferretería + Tailwind)
└─ hooks/          # useToast, useDocumentTitle, etc.
```

## Contratos con el backend (resumen)

- Envelope: éxito `{ success, data?, meta? }`, error `{ success, errorCode, codigo,
  errorMessage, details? }`. `success===false` → `ApiError`; `CREDENCIALES_INVALIDAS` /
  `TOKEN_EXPIRADO` manejan sesión; el resto → toast.
- Enviar `X-Request-Id` (UUID) en cada request; el backend lo ecoa en `errorMessage`.
- Auth en dos fases: `POST /auth/login` (password) devuelve un desafío OTP;
  `/auth/otp/enviar` manda el código de 6 dígitos por email o WhatsApp y
  `/auth/otp/verificar` abre la sesión. Login con Google por redirect
  (`/auth/oauth2/google` → `/auth/callback` → OTP). Refresh rotativo
  (access 15 min / refresh 8 h) en cookies HttpOnly; el interceptor refresca
  con mutex y reintenta una vez; si falla → logout local → `/login`.
- Fechas: `LocalDate` `yyyy-MM-dd` (input `date`); moneda: `Intl.NumberFormat("es-MX", MXN)`.
- Reportes y cortes por rango de fechas (default = hoy).
- POS con sugerencias difusas en vivo (`GET /productos/buscar`, tolera typos,
  debounce 200 ms, clic agrega al ticket; Enter/F3 conservan la búsqueda manual).
- Recordatorios diarios: botón "Enviar recordatorio"/"Avisar" en Compras →
  Cuentas por pagar, Ventas → Cobranza, Ventas → Rentas, Inventario →
  Existencias y Caja (`POST /reportes/{cuentas-pagar,cobranza,rentas,
  stock-bajo,turnos}/informe`); antes pregunta `GET .../estado` y, si hoy ya
  se envió, pide confirmación con `ConfirmDialog` (mismo patrón del informe
  del dashboard).
- POS: sugerencias difusas en vivo, ticket por WhatsApp desde el diálogo de
  venta registrada, dashboard con banner narrativo (hoy vs ayer + estrella),
  cotizaciones con foto de evidencia.